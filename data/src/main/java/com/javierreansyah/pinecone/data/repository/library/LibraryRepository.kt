package com.javierreansyah.pinecone.data.repository.library

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.BookAuthorCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookDao
import com.javierreansyah.pinecone.data.local.database.library.BookMetadataUpdate
import com.javierreansyah.pinecone.data.local.database.library.BookSpaceCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookTagCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfDao
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceDao
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.model.library.ALL_SPACES_ID
import com.javierreansyah.pinecone.data.publication.PublicationMetadata
import com.javierreansyah.pinecone.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val bookDao: BookDao,
    private val shelfDao: ShelfDao,
    private val spaceDao: SpaceDao,
    private val assets: PublicationAssetStore,
    @IoDispatcher private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
) {

    private val booksDir: File
        get() = assets.booksDirectory

    private val coversDir: File
        get() = assets.coversDirectory

    fun getAllBooks(): Flow<List<BookWithDetails>> = bookDao.getAllBooks()

    fun getVisibleBooks(spaceId: String?): Flow<List<BookWithDetails>> =
        bookDao.getVisibleBooks(spaceId)

    fun hasUnsortedBooks(): Flow<Boolean> = bookDao.hasUnsortedBooks()


    private val storageMutex get() = assets.mutationMutex

    /** Import, conversion, restore, deletion and orphan collection must not race durable assets. */
    internal suspend fun <T> withAssetTransaction(block: suspend () -> T): T =
        storageMutex.withLock { block() }

    private val publicationDao get() = database.publicationDao()

    suspend fun cleanupOrphanedFiles(): Unit = withContext(ioDispatcher) {
        storageMutex.withLock {
            val books = bookDao.getAllBooksSync()
            val referencedBooks =
                (books.map { it.filePath } + publicationDao.allRenditions().map { it.filePath })
                    .map { File(it).canonicalPath }.toSet()
            val referencedCovers =
                books.asSequence()
                    .mapNotNull { book -> book.coverPath }
                    .map { File(it).canonicalPath }.toSet()
            booksDir.listFiles()?.asSequence()
                ?.filter { it.isFile && it.canonicalPath !in referencedBooks }
                ?.forEach { assets.deleteIfUnleased(it) }
            coversDir.listFiles()?.asSequence()
                ?.filter { it.isFile && it.canonicalPath !in referencedCovers }
                ?.forEach { assets.deleteIfUnleased(it) }
            assets.cleanupStaging()
        }
    }

    fun getArchivedBooks(): Flow<List<BookWithDetails>> = bookDao.getArchivedBooks()

    fun getUnsortedBooks(): Flow<List<BookWithDetails>> =
        bookDao.getUnsortedBooks()

    fun getUnshelvedBooks(spaceId: String?) = bookDao.getUnshelvedBooks(spaceId)
    fun getShelfWithBooks(id: String) = shelfDao.getShelfWithBooks(id)
    fun getShelfRefs(id: String) = shelfDao.getShelfRefs(id)

    suspend fun releaseUnsortedBooks(): Unit = database.withTransaction {
        bookDao.releaseUnsortedBooks()
    }

    fun searchShelves(query: String): Flow<List<ShelfEntity>> = shelfDao.searchShelves(query)

    suspend fun getBook(id: String): BookWithDetails? = bookDao.getById(id)

    fun getBookFlow(id: String): Flow<BookWithDetails?> = bookDao.getByIdFlow(id)


    suspend fun deleteBook(bookId: String) = deleteBooks(listOf(bookId))

    suspend fun deleteBooks(bookIds: Collection<String>): Unit = withContext(ioDispatcher) {
        if (bookIds.isEmpty()) return@withContext
        withAssetTransaction {
            val batches = bookIds.distinct().chunked(900)
            val books = batches.flatMap { bookDao.entities(it) }
            val renditions = batches.flatMap { publicationDao.renditionsForBooks(it) }
            database.withTransaction {
                val operations = database.operationDao()
                operations.runtime()
                operations.advanceSequence()
                val sequence = operations.runtime().sequence
                books.forEach {
                    operations.recordDeletion(
                        com.javierreansyah.pinecone.data.local.database.library.PublicationDeletionEntity(
                            it.id,
                            sequence
                        )
                    )
                }
                batches.forEach { operations.invalidateBooks(it); bookDao.deleteIds(it) }
                shelfDao.deleteEmptyShelves()
                bookDao.deleteOrphanAuthors()
                bookDao.deleteOrphanTags()
            }
            val remaining = bookDao.getAllBooksSync()
            val referenced = (publicationDao.allRenditions().map { it.filePath } +
                    remaining.flatMap {
                        listOfNotNull(
                            it.filePath,
                            it.coverPath
                        )
                    }).map { File(it).canonicalPath }.toSet()
            (renditions.map { it.filePath } + books.flatMap {
                listOfNotNull(
                    it.filePath,
                    it.coverPath
                )
            }).distinct().forEach {
                val file = File(it)
                if (file.canonicalPath !in referenced) assets.deleteIfUnleased(file)
            }
        }
    }

    suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        description: String?,
        coverUri: Uri?,
        authors: List<String>,
        tags: List<String>,
        metadata: PublicationMetadata? = null,
        credits: List<PublicationCreditEntity>? = null,
    ): Unit = withContext(ioDispatcher) {
        val staged = coverUri?.let { File.createTempFile("cover-", ".partial", context.cacheDir) }
        var published: File? = null
        var committed = false
        try {
            if (staged != null) {
                requireNotNull(context.contentResolver.openInputStream(requireNotNull(coverUri))) {
                    "Cannot read replacement cover"
                }.use { input ->
                    staged.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                check(staged.length() > 0) { "Replacement cover is empty" }
            }
            withAssetTransaction {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // A cancelled return from a committed transaction must not delete its new cover.
                withContext(kotlinx.coroutines.NonCancellable) {
                    var previousCover: String? = null
                    database.withTransaction {
                        val current = requireNotNull(bookDao.getById(bookId)?.book) {
                            "Publication no longer exists"
                        }
                        previousCover = current.coverPath
                        if (staged != null) {
                            val target =
                                File(coversDir, "${bookId}_custom_${UUID.randomUUID()}.png")
                            check(staged.renameTo(target)) { "Cannot publish replacement cover" }
                            published = target
                        }
                        check(
                            bookDao.updateMetadata(
                                BookMetadataUpdate(
                                    id = bookId,
                                    title = title,
                                    description = description,
                                    coverPath = published?.absolutePath ?: current.coverPath,
                                    metadataJson = metadata?.encode() ?: current.metadataJson,
                                    language = if (metadata != null) metadata.languages.firstOrNull() else current.language,
                                    identifier = if (metadata != null) metadata.identifiers["identifier"]
                                        ?: metadata.identifiers.values.firstOrNull() else current.identifier,
                                    publisher = credits?.filter { it.role == "publisher" }
                                        ?.joinToString(", ") { it.name } ?: current.publisher,
                                    published = metadata?.published ?: current.published,
                                ),
                            ) == 1)

                        if (credits != null) {
                            publicationDao.deleteCredits(bookId)
                            publicationDao.putCredits(credits)
                        }

                        bookDao.deleteBookAuthorCrossRefs(bookId)
                        authors.forEachIndexed { index, authorName ->
                            var authorId =
                                bookDao.insertAuthor(AuthorEntity(name = authorName.trim()))
                            if (authorId == -1L) {
                                authorId =
                                    bookDao.getAuthorByName(authorName.trim())?.id
                                        ?: return@forEachIndexed
                            }
                            bookDao.insertBookAuthorCrossRef(
                                BookAuthorCrossRef(
                                    bookId,
                                    authorId,
                                    index
                                )
                            )
                        }

                        bookDao.deleteBookTagCrossRefs(bookId)
                        tags.forEach { tagName ->
                            var tagId = bookDao.insertTag(TagEntity(name = tagName.trim()))
                            if (tagId == -1L) {
                                tagId = bookDao.getTagByName(tagName.trim())?.id ?: return@forEach
                            }
                            bookDao.insertBookTagCrossRef(BookTagCrossRef(bookId, tagId))
                        }

                        bookDao.deleteOrphanAuthors()
                        bookDao.deleteOrphanTags()
                    }
                    committed = true
                    if (published != null) previousCover?.let { oldPath ->
                        if (bookDao.coverReferenceCount(oldPath) == 0) File(oldPath).delete()
                    }
                }
            }
        } finally {
            staged?.delete()
            if (!committed) published?.delete()
        }
    }

    fun getAllAuthors(): Flow<List<AuthorEntity>> = bookDao.getAllAuthors()

    fun getAllTags(): Flow<List<TagEntity>> = bookDao.getAllTags()

    suspend fun deleteFilterItem(type: String, name: String): Unit = withContext(ioDispatcher) {
        if (type == "author") {
            bookDao.deleteAuthorByName(name)
            publicationDao.deletePrimaryCredit(name)
        } else if (type == "tag") {
            bookDao.deleteTagByName(name)
        } else if (type == "space") {
            val space = spaceDao.getSpaceByName(name)
            if (space != null && space.id != ALL_SPACES_ID) {
                database.withTransaction {
                    spaceDao.deleteSpace(space)
                }
            }
        }
    }

    suspend fun renameFilterItem(type: String, oldName: String, newName: String): Unit =
        withContext(ioDispatcher) {
            val trimmedNewName = newName.trim()
            if (oldName == trimmedNewName) return@withContext
            if (type == "author") {
                publicationDao.renamePrimaryCredit(oldName, trimmedNewName)
                val existingDest = bookDao.getAuthorByName(trimmedNewName)
                val source = bookDao.getAuthorByName(oldName)
                if (existingDest != null && source != null) {
                    bookDao.mergeBookAuthorCrossRef(source.id, existingDest.id)
                    bookDao.deleteAuthorByName(oldName)
                } else {
                    bookDao.renameAuthor(oldName, trimmedNewName)
                }
            } else if (type == "tag") {
                val existingDest = bookDao.getTagByName(trimmedNewName)
                val source = bookDao.getTagByName(oldName)
                if (existingDest != null && source != null) {
                    bookDao.mergeBookTagCrossRef(source.id, existingDest.id)
                    bookDao.deleteTagByName(oldName)
                } else {
                    bookDao.renameTag(oldName, trimmedNewName)
                }
            } else if (type == "space") {
                val existingDest = spaceDao.getSpaceByName(trimmedNewName)
                val source = spaceDao.getSpaceByName(oldName)
                if (source?.id == ALL_SPACES_ID || existingDest?.id == ALL_SPACES_ID) return@withContext
                if (existingDest != null && source != null) {
                    database.withTransaction {
                        spaceDao.mergeBookSpaceCrossRef(source.id, existingDest.id)
                        spaceDao.deleteSpace(source)
                    }
                } else {
                    source?.let { (itId, _, _) -> spaceDao.renameSpace(itId, trimmedNewName) }
                }
            }
        }

    suspend fun toggleArchive(bookId: String) {
        bookDao.toggleArchive(bookId)
    }

    suspend fun toggleReadStatus(bookId: String) {
        bookDao.toggleReadStatus(bookId)
    }

    suspend fun setArchived(bookIds: Collection<String>, value: Boolean): Unit =
        database.withTransaction {
            bookIds.distinct().chunked(900).forEach { bookDao.setArchived(it, value) }
        }

    suspend fun setReadStatus(bookIds: Collection<String>, value: Boolean): Unit =
        database.withTransaction {
            bookIds.distinct().chunked(900).forEach { bookDao.setReadStatus(it, value) }
        }

    fun getAllShelvesWithBooks(): Flow<List<ShelfWithCovers>> =
        shelfDao.getAllShelvesWithBooks().map { shelves ->
            shelves.map { shelf ->
                shelf.copy(books = shelf.books.filter { details -> !details.book.isArchived })
            }
        }

    suspend fun createShelf(spaceId: String, name: String): String {
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            spaceDao.ensureAllSpace(context)
            shelfDao.insertShelf(ShelfEntity(id = id, spaceId = spaceId, name = name))
        }
        return id
    }

    suspend fun transferShelves(
        shelfIds: Collection<String>,
        destinationId: String,
        copy: Boolean
    ) {
        database.withTransaction {
            spaceDao.ensureAllSpace(context)
            require(spaceDao.getAllSpacesSync().any { it.id == destinationId })
            val refsByShelf = shelfDao.getAllShelfBookCrossRefsSync().groupBy { it.shelfId }
            shelfIds.distinct().forEach { id ->
                val shelf = shelfDao.getShelfById(id) ?: return@forEach
                if (!copy && shelf.spaceId == destinationId) return@forEach
                val target =
                    if (copy) shelf.copy(id = UUID.randomUUID().toString(), spaceId = destinationId)
                    else shelf.copy(spaceId = destinationId)
                if (copy) shelfDao.insertShelf(target) else shelfDao.updateShelf(target)
                refsByShelf[id].orEmpty().forEach { ref ->
                    if (copy) shelfDao.insertShelfBookCrossRef(ref.copy(shelfId = target.id))
                    spaceDao.insertBookSpaceCrossRef(BookSpaceCrossRef(ref.bookId, destinationId))
                    spaceDao.insertBookSpaceCrossRef(BookSpaceCrossRef(ref.bookId, ALL_SPACES_ID))
                    bookDao.markOrganized(ref.bookId)
                }
            }
        }
    }

    suspend fun deleteShelf(shelfId: String) {
        val shelf = shelfDao.getShelfById(shelfId)
        shelf?.let { shelfDao.deleteShelf(it) }
    }

    suspend fun renameShelf(shelfId: String, newName: String) {
        val shelf = shelfDao.getShelfById(shelfId)
        shelf?.let { shelfDao.updateShelf(it.copy(name = newName)) }
    }

    suspend fun setBooksInShelf(
        shelfId: String,
        bookIds: Collection<String>,
        selected: Boolean
    ): Unit = database.withTransaction {
        val ids = bookIds.distinct()
        if (!selected) {
            ids.chunked(900).forEach { shelfDao.removeShelfMembers(shelfId, it) }
            shelfDao.deleteEmptyShelves()
        } else {
            val shelf = shelfDao.getShelfById(shelfId) ?: return@withTransaction
            spaceDao.ensureAllSpace(context)
            val existing = shelfDao.getShelfRefsSync(shelfId)
            val members = existing.mapTo(mutableSetOf()) { it.bookId }
            val first = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
            ids.chunked(900).forEach { bookDao.markOrganized(it) }
            spaceDao.addMembers(ids.flatMap {
                listOf(
                    BookSpaceCrossRef(it, ALL_SPACES_ID),
                    BookSpaceCrossRef(it, shelf.spaceId)
                )
            }.distinct())
            shelfDao.addShelfRefs(ids.filterNot { it in members }.mapIndexed { index, id ->
                ShelfBookCrossRefEntity(
                    shelfId,
                    id,
                    orderIndex = first + index
                )
            })
        }
    }

    suspend fun setBooksInSpace(
        spaceId: String,
        bookIds: Collection<String>,
        selected: Boolean
    ): Unit = database.withTransaction {
        val ids = bookIds.distinct()
        if (selected) {
            spaceDao.ensureAllSpace(context)
            ids.chunked(900).forEach { bookDao.markOrganized(it) }
            spaceDao.addMembers(ids.flatMap {
                listOf(
                    BookSpaceCrossRef(it, ALL_SPACES_ID),
                    BookSpaceCrossRef(it, spaceId)
                )
            }.distinct())
        } else if (spaceId != ALL_SPACES_ID) {
            ids.chunked(900).forEach {
                spaceDao.removeMembers(spaceId, it); shelfDao.removeSpaceMembers(
                spaceId,
                it
            )
            }
            shelfDao.deleteEmptyShelves()
        }
    }

    suspend fun createShelfWithBooks(
        spaceId: String,
        name: String,
        ids: Collection<String>
    ): String = database.withTransaction {
        createShelf(spaceId, name).also { setBooksInShelf(it, ids, true) }
    }

    suspend fun createSpaceWithBooks(name: String, ids: Collection<String>): String =
        database.withTransaction {
            createSpace(name).also { setBooksInSpace(it, ids, true) }
        }

    fun getAllShelfBookCrossRefs(): Flow<List<ShelfBookCrossRefEntity>> =
        shelfDao.getAllShelfBookCrossRefs()

    suspend fun updateShelfOrder(shelfId: String, newBookIdsOrder: List<String>): Unit =
        withContext(ioDispatcher) {
            database.withTransaction {
                newBookIdsOrder.forEachIndexed { index, bookId ->
                    shelfDao.updateShelfBookOrderIndex(shelfId, bookId, index)
                }
            }
        }

    fun getAllSpaces(): Flow<List<SpaceEntity>> = spaceDao.getAllSpaces()

    suspend fun createSpace(name: String): String {
        val id = UUID.randomUUID().toString()
        spaceDao.insertSpace(SpaceEntity(id = id, name = name))
        return id
    }

    fun getAllBookSpaceCrossRefs(): Flow<List<BookSpaceCrossRef>> =
        spaceDao.getAllBookSpaceCrossRefs()


}
