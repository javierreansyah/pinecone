package com.javierreansyah.pinecone.data.local.database.library

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookMutationTest {
    @Test
    fun atomicStatusChangesAndMetadataEditsPreserveReadingState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.bookDao()
            val original = BookEntity(
                "book",
                "Original",
                filePath = "/original.epub",
                progression = .75,
                lastLocatorJson = "saved-position",
                lastReadDate = 123L,
                activeRendition = "read-mode"
            )
            dao.insert(original)
            coroutineScope {
                repeat(101) { launch { dao.toggleArchive("book") } }
                repeat(101) { launch { dao.toggleReadStatus("book") } }
                launch {
                    dao.updateMetadata(
                        BookMetadataUpdate(
                            "book",
                            "Edited",
                            null,
                            null,
                            "{}",
                            null,
                            null,
                            null,
                            null
                        )
                    )
                }
            }
            val changed = requireNotNull(dao.getById("book")).book
            assertTrue(changed.isArchived)
            assertTrue(changed.isRead)
            assertEquals(original.copy(title = "Edited", isArchived = true, isRead = true), changed)
            dao.setArchived(listOf("book", "missing"), false)
            dao.setReadStatus(listOf("book", "missing"), false)
            assertEquals(original.copy(title = "Edited"), requireNotNull(dao.getById("book")).book)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedCoverCopyPreservesMetadataAndExistingCover() = runBlocking {
        withRepository { db, repository, old ->
            val before = requireNotNull(db.bookDao().getById("book")).book
            val missing = File(old.parentFile, "missing-${old.name}")
            try {
                repository.updateBookMetadata(
                    "book",
                    "Must not commit",
                    null,
                    Uri.fromFile(missing),
                    emptyList(),
                    emptyList()
                )
                throw AssertionError("A missing cover must fail")
            } catch (_: java.io.FileNotFoundException) {
                assertEquals(before, requireNotNull(db.bookDao().getById("book")).book)
                assertEquals("old-cover", old.readText())
            }
        }
    }

    @Test
    fun replacementCoverIsCommittedBeforeOldCoverCollectionAndSharedCoverIsKept() = runBlocking {
        withRepository { db, repository, old ->
            db.bookDao().insert(
                BookEntity(
                    "other",
                    "Other",
                    coverPath = old.path,
                    filePath = "/other.epub"
                )
            )
            val source = File.createTempFile("replacement-", ".png", old.parentFile)
            try {
                source.writeText("new-cover")
                repository.updateBookMetadata(
                    "book",
                    "Edited",
                    null,
                    Uri.fromFile(source),
                    emptyList(),
                    emptyList()
                )
                val changed = requireNotNull(db.bookDao().getById("book")).book
                assertEquals("new-cover", File(requireNotNull(changed.coverPath)).readText())
                assertTrue(old.isFile)
                db.bookDao().delete(requireNotNull(db.bookDao().getById("other")).book)
                repository.updateBookMetadata(
                    "book",
                    "Edited again",
                    null,
                    Uri.fromFile(source),
                    emptyList(),
                    emptyList()
                )
                assertFalse(File(requireNotNull(changed.coverPath)).exists())
            } finally {
                source.delete()
            }
        }
    }

    private suspend fun withRepository(block: suspend (AppDatabase, LibraryRepository, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val old = withContext(Dispatchers.IO) {
            File.createTempFile("old-cover-", ".png", context.cacheDir)
        }
        try {
            old.writeText("old-cover")
            db.bookDao().insert(
                BookEntity(
                    "book",
                    "Original",
                    coverPath = old.path,
                    filePath = "/original.epub"
                )
            )
            val repository = LibraryRepository(
                context,
                db,
                db.bookDao(),
                db.shelfDao(),
                db.spaceDao(),
                com.javierreansyah.pinecone.data.repository.library.PublicationAssetStore(context),
                Dispatchers.IO
            )
            block(db, repository, old)
        } finally {
            db.bookDao()
                .getById("book")?.book?.coverPath?.let { if (it != old.path) File(it).delete() }
            old.delete()
            db.close()
        }
    }
}
