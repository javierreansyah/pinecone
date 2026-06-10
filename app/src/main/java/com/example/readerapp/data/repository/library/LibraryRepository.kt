package com.example.readerapp.data.repository.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.example.readerapp.data.local.database.library.AppDatabase
import com.example.readerapp.data.local.database.library.AuthorEntity
import com.example.readerapp.data.local.database.library.BookAuthorCrossRef
import com.example.readerapp.data.local.database.library.BookDao
import com.example.readerapp.data.local.database.library.BookEntity
import com.example.readerapp.data.local.database.library.BookTagCrossRef
import com.example.readerapp.data.local.database.library.BookWithDetails
import com.example.readerapp.data.local.database.library.BookmarkDao
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteDao
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.data.local.database.library.ShelfBookCrossRefEntity
import com.example.readerapp.data.local.database.library.ShelfDao
import com.example.readerapp.data.local.database.library.ShelfEntity
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.local.database.library.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class LibraryRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val shelfDao: ShelfDao,
    private val noteDao: NoteDao,
    private val publicationOpener: PublicationOpener,
    private val assetRetriever: AssetRetriever
) {

    private val booksDir: File
        get() = File(context.filesDir, "books").also { it.mkdirs() }

    private val coversDir: File
        get() = File(context.filesDir, "covers").also { it.mkdirs() }

    fun getAllBooks(): Flow<List<BookWithDetails>> = bookDao.getAllBooks()

    fun getArchivedBooks(): Flow<List<BookWithDetails>> = bookDao.getArchivedBooks()

    fun searchBooks(query: String): Flow<List<BookWithDetails>> = bookDao.searchBooks(query)

    fun searchShelves(query: String): Flow<List<ShelfEntity>> = shelfDao.searchShelves(query)

    fun searchAuthors(query: String): Flow<List<String>> = bookDao.searchAuthors(query)

    fun searchTags(query: String): Flow<List<String>> = bookDao.searchTags(query)

    suspend fun getBook(id: String): BookWithDetails? = bookDao.getById(id)

    /**
     * Import a book from a content URI (file picker or intent).
     * Copies to internal storage, extracts metadata via Readium, stores in Room.
     */
    suspend fun importBook(uri: Uri): BookWithDetails? = withContext(Dispatchers.IO) {
        try {
            // Copy to a temporary file to analyze it
            val tempFile = File(booksDir, "import_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            // Detect media type and extension
            val asset = assetRetriever.retrieve(tempFile.toUrl(isDirectory = false)).getOrNull()
            val mediaType = asset?.format
            val extension = mediaType?.fileExtension ?: "epub"

            // Generate ID from file hash
            val bookId = generateFileHash(tempFile)

            // Check if already exists
            if (bookDao.exists(bookId) > 0) {
                tempFile.delete()
                return@withContext bookDao.getById(bookId)
            }

            // Rename to final location with correct extension
            val finalFile = File(booksDir, "$bookId.$extension")
            tempFile.renameTo(finalFile)

            // Open with Readium to extract metadata
            val publication = openPublicationFromFile(finalFile) ?: run {
                finalFile.delete()
                return@withContext null
            }

            val entity = createBookEntity(bookId, finalFile, publication, mediaType?.toString())
            database.withTransaction {
                bookDao.insert(entity)

                val authors = publication.metadata.authors.map { it.name }
                authors.forEachIndexed { index, authorName ->
                    var authorId = bookDao.insertAuthor(AuthorEntity(name = authorName))
                    if (authorId == -1L) {
                        authorId = bookDao.getAuthorByName(authorName)?.id ?: return@forEachIndexed
                    }
                    bookDao.insertBookAuthorCrossRef(BookAuthorCrossRef(bookId, authorId, index))
                }

                val tags = publication.metadata.subjects.map { it.name }
                tags.forEach { tagName ->
                    var tagId = bookDao.insertTag(TagEntity(name = tagName))
                    if (tagId == -1L) {
                        tagId = bookDao.getTagByName(tagName)?.id ?: return@forEach
                    }
                    bookDao.insertBookTagCrossRef(BookTagCrossRef(bookId, tagId))
                }
            }

            publication.close()

            bookDao.getById(bookId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Open a Publication for reading. Caller is responsible for closing it.
     */
    suspend fun openPublication(book: BookWithDetails): Publication? = withContext(Dispatchers.IO) {
        val file = File(book.book.filePath)
        if (!file.exists()) return@withContext null
        openPublicationFromFile(file)
    }

    suspend fun saveReadingPosition(bookId: String, locator: Locator) {
        val progression = locator.locations.totalProgression ?: 0.0
        bookDao.updateProgress(
            id = bookId,
            progression = progression,
            lastLocatorJson = locator.toJSON().toString(),
            lastReadDate = System.currentTimeMillis()
        )
    }

    suspend fun getLastLocator(bookId: String): Locator? {
        val book = bookDao.getById(bookId)?.book ?: return null
        val json = book.lastLocatorJson ?: return null
        return try {
            Locator.fromJSON(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addBookmark(bookId: String, locator: Locator) {
        bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                chapterTitle = locator.title
            )
        )
    }

    fun getBookmarks(bookId: String): Flow<List<BookmarkEntity>> = bookmarkDao.getByBookId(bookId)

    suspend fun removeBookmark(id: Long) {
        bookmarkDao.deleteById(id)
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId)?.book ?: return@withContext
        // Delete EPUB file
        File(book.filePath).delete()
        // Delete cover
        book.coverPath?.let { File(it).delete() }
        // Delete from DB in a transaction
        database.withTransaction {
            bookDao.delete(book)
            // Cleanup orphans
            bookDao.deleteOrphanAuthors()
            bookDao.deleteOrphanTags()
            shelfDao.deleteOrphanShelves()
        }
    }

    // --- Update Metadata ---

    suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        description: String?,
        coverUri: Uri?,
        authors: List<String>,
        tags: List<String>
    ) = withContext(Dispatchers.IO) {
        val bookDetails = bookDao.getById(bookId) ?: return@withContext
        val book = bookDetails.book

        var newCoverPath = book.coverPath
        if (coverUri != null) {
            val coverFile = File(coversDir, "${bookId}_custom_${System.currentTimeMillis()}.png")
            try {
                context.contentResolver.openInputStream(coverUri)?.use { input ->
                    FileOutputStream(coverFile).use { output ->
                        input.copyTo(output)
                    }
                }
                newCoverPath = coverFile.absolutePath
                book.coverPath?.let { oldPath ->
                    if (oldPath != newCoverPath) {
                        File(oldPath).delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        database.withTransaction {
            bookDao.update(
                book.copy(
                    title = title, description = description, coverPath = newCoverPath
                )
            )

            bookDao.deleteBookAuthorCrossRefs(bookId)
            authors.forEachIndexed { index, authorName ->
                var authorId = bookDao.insertAuthor(AuthorEntity(name = authorName.trim()))
                if (authorId == -1L) {
                    authorId =
                        bookDao.getAuthorByName(authorName.trim())?.id ?: return@forEachIndexed
                }
                bookDao.insertBookAuthorCrossRef(BookAuthorCrossRef(bookId, authorId, index))
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
    }

    fun getAllAuthors(): Flow<List<AuthorEntity>> = bookDao.getAllAuthors()

    fun getAllTags(): Flow<List<TagEntity>> = bookDao.getAllTags()

    suspend fun deleteFilterItem(type: String, name: String) = withContext(Dispatchers.IO) {
        if (type == "author") {
            bookDao.deleteAuthorByName(name)
        } else if (type == "tag") {
            bookDao.deleteTagByName(name)
        }
    }

    suspend fun renameFilterItem(type: String, oldName: String, newName: String) =
        withContext(Dispatchers.IO) {
            val trimmedNewName = newName.trim()
            if (oldName == trimmedNewName) return@withContext
            if (type == "author") {
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
            }
        }

    // --- Archive & Read Status Methods ---

    suspend fun toggleArchive(bookId: String) {
        val book = bookDao.getById(bookId)?.book ?: return
        bookDao.update(book.copy(isArchived = !book.isArchived))
    }

    suspend fun toggleReadStatus(bookId: String) {
        val book = bookDao.getById(bookId)?.book ?: return
        bookDao.update(book.copy(isRead = !book.isRead))
    }

    // --- Shelf Methods ---

    fun getAllShelvesWithBooks(): Flow<List<ShelfWithCovers>> =
        shelfDao.getAllShelvesWithBooks().map { shelves ->
            shelves.map { shelf ->
                shelf.copy(books = shelf.books.filter { !it.book.isArchived })
            }
        }

    suspend fun createShelf(name: String): String {
        val id = UUID.randomUUID().toString()
        shelfDao.insertShelf(ShelfEntity(id = id, name = name))
        return id
    }

    suspend fun deleteShelf(shelfId: String) {
        val shelf = shelfDao.getShelfById(shelfId)
        if (shelf != null) {
            shelfDao.deleteShelf(shelf)
        }
    }

    suspend fun renameShelf(shelfId: String, newName: String) {
        val shelf = shelfDao.getShelfById(shelfId)
        if (shelf != null) {
            shelfDao.updateShelf(shelf.copy(name = newName))
        }
    }

    suspend fun addBookToShelf(shelfId: String, bookId: String) {
        shelfDao.insertShelfBookCrossRef(
            ShelfBookCrossRefEntity(
                shelfId = shelfId, bookId = bookId
            )
        )
    }

    suspend fun removeBookFromShelf(shelfId: String, bookId: String) {
        shelfDao.deleteShelfBookCrossRef(shelfId = shelfId, bookId = bookId)
        shelfDao.deleteOrphanShelves()
    }

    fun getAllShelfBookCrossRefs(): Flow<List<ShelfBookCrossRefEntity>> =
        shelfDao.getAllShelfBookCrossRefs()

    suspend fun updateShelfOrder(shelfId: String, newBookIdsOrder: List<String>) =
        withContext(Dispatchers.IO) {
            newBookIdsOrder.forEachIndexed { index, bookId ->
                shelfDao.updateShelfBookOrderIndex(shelfId, bookId, index)
            }
        }

    // --- Note Methods ---

    suspend fun addNote(
        bookId: String,
        locator: Locator,
        noteText: String,
        color: Int = -1,
        chapterTitle: String? = null
    ) {
        noteDao.insert(
            NoteEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                chapterTitle = chapterTitle ?: locator.title,
                noteText = noteText,
                color = color
            )
        )
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.update(note)
    }

    fun getNotes(bookId: String): Flow<List<NoteEntity>> = noteDao.getByBookId(bookId)

    suspend fun removeNote(id: Long) {
        noteDao.deleteById(id)
    }

    // --- Private helpers ---

    private suspend fun openPublicationFromFile(file: File): Publication? {
        return try {
            val url = file.toUrl(isDirectory = false)
            val asset = assetRetriever.retrieve(url).getOrNull() ?: return null
            publicationOpener.open(asset, allowUserInteraction = false).getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun createBookEntity(
        bookId: String, file: File, publication: Publication, mediaType: String? = null
    ): BookEntity {
        val metadata = publication.metadata

        // Extract cover image
        val coverPath = try {
            val coverLink = publication.linkWithRel("cover")
            val coverBitmap = coverLink?.let { link ->
                val resource = publication.get(link)
                val bytes = resource!!.read().getOrNull()
                if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
            }

            if (coverBitmap != null) {
                val coverFile = File(coversDir, "$bookId.png")
                withContext(Dispatchers.IO) {
                    FileOutputStream(coverFile).use { out ->
                        coverBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
                coverFile.absolutePath
            } else null
        } catch (e: Exception) {
            null
        }

        return BookEntity(
            id = bookId,
            title = metadata.title ?: file.nameWithoutExtension,
            coverPath = coverPath,
            filePath = file.absolutePath,
            mediaType = mediaType ?: "application/epub+zip",
            identifier = metadata.identifier,
            language = metadata.languages.firstOrNull(),
            progression = 0.0,
            lastLocatorJson = null,
            addedDate = System.currentTimeMillis(),
            lastReadDate = null,
            isArchived = false,
            description = metadata.description,
            publisher = metadata.publishers.joinToString(", ") { it.name }
                .takeIf { it.isNotEmpty() },
            published = metadata.published?.toString(),
            isRead = false
        )
    }

    private fun generateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }
}