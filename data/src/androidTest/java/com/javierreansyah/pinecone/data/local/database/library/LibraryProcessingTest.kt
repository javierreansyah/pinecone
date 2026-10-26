package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.PublicationAssetStore
import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Locator
import java.util.concurrent.CopyOnWriteArrayList

class LibraryProcessingTest {
    @Test
    fun readingWritesAvoidRelationshipQueriesAndKeepMirroredStateAndSessionGuards() = runBlocking {
        val reads = CopyOnWriteArrayList<String>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK)
            .setQueryCallback({ sql, _ ->
                if (sql.startsWith("SELECT", ignoreCase = true)) reads += sql
            }, { it.run() })
            .build()
        try {
            val book = BookEntity("reading", "Reading", filePath = "/fixture/reading.epub")
            db.bookDao().insert(book)
            db.publicationDao().putRenditions(
                listOf(
                    PublicationRenditionEntity(
                        book.id,
                        "original",
                        book.filePath,
                        "application/epub+zip",
                        "EPUB",
                        "source-hash",
                    )
                )
            )
            val reading = ReadingRepository(db, db.bookDao(), db.bookmarkDao(), db.noteDao())
            val session = requireNotNull(reading.currentSession(book.id))
            fun locator(progression: Double) = requireNotNull(
                Locator.fromJSON(
                    JSONObject(
                        """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":$progression}}""",
                    )
                )
            )
            reads.clear()
            db.bookDao().getById(book.id)
            val relationReads = reads.size
            reads.clear()
            val origin = locator(.2)
            reading.saveReadingPosition(book.id, origin, session = session)
            val writeReads = reads.toList()
            assertTrue(
                "Reading writes should load fewer rows: $writeReads",
                writeReads.size < relationReads
            )
            val relations =
                Regex("\\b(authors|tags|spaces|publication_credits|book_author_cross_ref|book_tag_cross_ref|book_space_cross_ref)\\b")
            assertTrue(writeReads.none { relations.containsMatchIn(it) })
            println("PINECONE_BENCHMARK bookDetailsSelects=$relationReads readingWriteSelects=${writeReads.size}")

            reading.saveJumpOrigin(book.id, origin, session)
            reading.saveJumpReadingPosition(book.id, locator(.8), session)
            var storedBook = requireNotNull(db.bookDao().getEntity(book.id))
            var rendition = requireNotNull(reading.rendition(book.id, "original"))
            assertEquals(storedBook.lastLocatorJson, rendition.lastLocatorJson)
            assertEquals(origin.toJSON().toString(), rendition.jumpOriginLocatorJson)
            assertEquals(.2, rendition.furthestProgression, 0.0)
            reading.clearJumpOrigin(book.id, session)
            reading.resetFurthestToCurrent(book.id, session)
            storedBook = requireNotNull(db.bookDao().getEntity(book.id))
            rendition = requireNotNull(reading.rendition(book.id, "original"))
            assertEquals(null, storedBook.jumpOriginLocatorJson)
            assertEquals(null, rendition.jumpOriginLocatorJson)
            assertEquals(.8, storedBook.furthestProgression, 0.0)
            assertEquals(storedBook.furthestLocatorJson, rendition.furthestLocatorJson)

            db.operationDao().beginRestore()
            reading.saveReadingPosition(book.id, locator(.9), session = session)
            assertEquals(storedBook, db.bookDao().getEntity(book.id))
            db.operationDao().finishRestore()
            reading.saveJumpReadingPosition(book.id, locator(.9), session)
            reading.saveJumpOrigin(book.id, locator(.9), session)
            assertEquals(storedBook, db.bookDao().getEntity(book.id))
            assertEquals(rendition, reading.rendition(book.id, "original"))
        } finally {
            db.close()
        }
    }

    @Test
    fun selectedShelfMaterializesOnlyItsMembersAndRecordsQueryMappingTimes() = runBlocking {
        fixture { db, _ ->
            val books = (0..<1000).map {
                BookEntity(
                    "book-$it",
                    "Book $it",
                    filePath = "/fixture/$it.epub"
                )
            }
            db.bookDao().insertAll(books)
            db.spaceDao()
                .insertAllBookSpaceCrossRefs(books.map { BookSpaceCrossRef(it.id, "_all_") })
            db.shelfDao().insertShelf(ShelfEntity("selected", "_all_", "Selected"))
            db.shelfDao().addShelfRefs(
                books.take(20).mapIndexed { index, b ->
                    ShelfBookCrossRefEntity(
                        "selected",
                        b.id,
                        orderIndex = index
                    )
                })
            suspend fun full() = db.bookDao().getAllBooks().first().map(Book::fromEntity)
            suspend fun selected() =
                db.shelfDao().getShelfWithBooks("selected").first()!!.books.map(Book::fromEntity)
            repeat(2) { full(); selected() }
            val fullTimes = mutableListOf<Long>()
            val selectedTimes = mutableListOf<Long>()
            repeat(5) {
                var start = System.nanoTime(); assertEquals(
                1000,
                full().size
            ); fullTimes += System.nanoTime() - start
                start = System.nanoTime(); assertEquals(
                20,
                selected().size
            ); selectedTimes += System.nanoTime() - start
            }
            println("PINECONE_BENCHMARK libraryRows=1000 shelfRows=20 fullMedianMs=${fullTimes.sorted()[2] / 1_000_000.0} selectedMedianMs=${selectedTimes.sorted()[2] / 1_000_000.0}")
        }
    }

    @Test
    fun unshelvedAndInboxQueriesPreserveSpaceAndStatusRules() = runBlocking {
        fixture { db, _ ->
            db.spaceDao()
                .insertAllSpaces(listOf(SpaceEntity("work", "Work"), SpaceEntity("other", "Other")))
            val books = listOf(
                BookEntity("a", "A", filePath = "/fixture/a"),
                BookEntity("b", "B", filePath = "/fixture/b"),
                BookEntity("c", "C", filePath = "/fixture/c"),
                BookEntity("inbox", "Inbox", filePath = "/fixture/i", isUnsorted = true),
                BookEntity("archive", "Archived", filePath = "/fixture/r", isArchived = true)
            )
            db.bookDao().insertAll(books)
            db.spaceDao().insertAllBookSpaceCrossRefs(books.flatMap {
                listOf(
                    BookSpaceCrossRef(
                        it.id,
                        "_all_"
                    ), BookSpaceCrossRef(it.id, "work")
                )
            } + BookSpaceCrossRef("c", "other"))
            db.shelfDao().insertAllShelves(
                listOf(
                    ShelfEntity("work-shelf", "work", "Work"),
                    ShelfEntity("other-shelf", "other", "Other")
                )
            )
            db.shelfDao().addShelfRefs(
                listOf(
                    ShelfBookCrossRefEntity("work-shelf", "b"),
                    ShelfBookCrossRefEntity("other-shelf", "c")
                )
            )
            assertEquals(
                setOf("a", "c"),
                db.bookDao().getUnshelvedBooks("work").first().map { it.book.id }.toSet()
            )
            assertEquals(
                listOf("a"),
                db.bookDao().getUnshelvedBooks(null).first().map { it.book.id })
            assertEquals(
                listOf("inbox"),
                db.bookDao().getUnsortedBooks().first().map { it.book.id })
        }
    }

    @Test
    fun largeOrganizationBatchesAreIdempotentOrderedAndTransactional() = runBlocking {
        fixture { db, library ->
            val ids = (0..<1001).map { "book-$it" }
            db.bookDao().insertAll(ids.map {
                BookEntity(
                    it,
                    it,
                    filePath = "/fixture/$it",
                    isUnsorted = true
                )
            })
            val space = library.createSpaceWithBooks("Work", ids)
            val shelf = library.createShelfWithBooks(space, "Shelf", ids)
            val first = db.shelfDao().getShelfRefsSync(shelf).sortedBy { it.orderIndex }
            assertEquals(ids, first.map { it.bookId })
            val revision = db.backupStateDao().revision()
            library.setBooksInShelf(shelf, ids, true)
            assertEquals(first, db.shelfDao().getShelfRefsSync(shelf).sortedBy { it.orderIndex })
            assertEquals(revision, db.backupStateDao().revision())
            val failure = runCatching {
                library.createShelfWithBooks(
                    space,
                    "Must roll back",
                    listOf(ids.first(), "missing")
                )
            }.exceptionOrNull()
            assertNotNull(failure)
            assertFalse(db.shelfDao().getAllShelvesSync().any { it.name == "Must roll back" })
            library.setBooksInSpace(space, ids, false)
            assertTrue(db.shelfDao().getAllShelvesSync().isEmpty())
            assertTrue(db.bookDao().getAllBooksSync().all { !it.isUnsorted })
            assertEquals(ids.size, db.spaceDao().getBookCountForSpace("_all_"))
        }
    }

    private suspend fun fixture(block: suspend (AppDatabase, LibraryRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK).build()
        try {
            block(
                db,
                LibraryRepository(
                    context,
                    db,
                    db.bookDao(),
                    db.shelfDao(),
                    db.spaceDao(),
                    PublicationAssetStore(context),
                    Dispatchers.IO
                )
            )
        } finally {
            db.close()
        }
    }
}
