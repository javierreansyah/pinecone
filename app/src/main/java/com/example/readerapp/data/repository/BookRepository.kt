package com.example.readerapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import com.example.readerapp.data.local.BookDao
import com.example.readerapp.data.local.BookEntity
import com.example.readerapp.data.local.BookmarkDao
import com.example.readerapp.data.local.BookmarkEntity
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import org.readium.r2.shared.util.asset.AssetRetriever
import android.graphics.BitmapFactory
import org.readium.r2.shared.util.getOrElse

import com.example.readerapp.data.local.ShelfDao
import com.example.readerapp.data.local.ShelfEntity
import com.example.readerapp.data.local.ShelfWithCovers
import com.example.readerapp.data.local.ShelfBookCrossRefEntity
import com.example.readerapp.data.local.NoteDao
import com.example.readerapp.data.local.NoteEntity

class BookRepository(
    private val context: Context,
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

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBook(id: String): BookEntity? = bookDao.getById(id)

    /**
     * Import an EPUB from a content URI (file picker).
     * Copies to internal storage, extracts metadata via Readium, stores in Room.
     */
    suspend fun importBook(uri: Uri): BookEntity? = withContext(Dispatchers.IO) {
        try {
            // Copy to internal storage
            val tempFile = File(booksDir, "import_${System.currentTimeMillis()}.epub")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            // Generate ID from file hash
            val bookId = generateFileHash(tempFile)

            // Check if already exists
            if (bookDao.exists(bookId) > 0) {
                tempFile.delete()
                return@withContext bookDao.getById(bookId)
            }

            // Rename to final location
            val finalFile = File(booksDir, "$bookId.epub")
            tempFile.renameTo(finalFile)

            // Open with Readium to extract metadata
            val publication = openPublicationFromFile(finalFile) ?: run {
                finalFile.delete()
                return@withContext null
            }

            val entity = createBookEntity(bookId, finalFile, publication)
            publication.close()

            bookDao.insert(entity)
            entity
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Import bundled EPUBs from assets on first launch.
     */
    suspend fun importBundledBooks() = withContext(Dispatchers.IO) {
        val assetFiles = try {
            context.assets.list("")?.filter { it.endsWith(".epub") } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        for (fileName in assetFiles) {
            try {
                val targetFile = File(booksDir, fileName)
                if (targetFile.exists()) {
                    // Already copied, check if in DB
                    val bookId = generateFileHash(targetFile)
                    if (bookDao.exists(bookId) > 0) continue
                }

                // Copy from assets
                context.assets.open(fileName).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val bookId = generateFileHash(targetFile)
                if (bookDao.exists(bookId) > 0) continue

                // Rename to ID-based name
                val finalFile = File(booksDir, "$bookId.epub")
                if (finalFile.absolutePath != targetFile.absolutePath) {
                    targetFile.renameTo(finalFile)
                }

                val publication = openPublicationFromFile(finalFile) ?: continue
                val entity = createBookEntity(bookId, finalFile, publication)
                publication.close()

                bookDao.insert(entity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Open a Publication for reading. Caller is responsible for closing it.
     */
    suspend fun openPublication(book: BookEntity): Publication? = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
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
        val book = bookDao.getById(bookId) ?: return null
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

    fun getBookmarks(bookId: String): Flow<List<BookmarkEntity>> =
        bookmarkDao.getByBookId(bookId)

    suspend fun removeBookmark(id: Long) {
        bookmarkDao.deleteById(id)
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId) ?: return@withContext
        // Delete EPUB file
        File(book.filePath).delete()
        // Delete cover
        book.coverPath?.let { File(it).delete() }
        // Delete bookmarks and notes
        bookmarkDao.deleteAllForBook(bookId)
        noteDao.deleteAllForBook(bookId)
        // Delete from DB
        bookDao.delete(book)
    }

    // --- Archive Methods ---

    suspend fun toggleArchive(bookId: String) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(book.copy(isArchived = !book.isArchived))
    }

    // --- Shelf Methods ---

    fun getAllShelvesWithBooks(): Flow<List<ShelfWithCovers>> = shelfDao.getAllShelvesWithBooks()

    suspend fun getShelfById(shelfId: String): ShelfEntity? = shelfDao.getShelfById(shelfId)

    suspend fun createShelf(name: String): String {
        val id = java.util.UUID.randomUUID().toString()
        shelfDao.insertShelf(ShelfEntity(id = id, name = name))
        return id
    }

    suspend fun deleteShelf(shelfId: String) {
        val shelf = shelfDao.getShelfById(shelfId)
        if (shelf != null) {
            shelfDao.deleteShelf(shelf)
        }
    }

    suspend fun addBookToShelf(shelfId: String, bookId: String) {
        shelfDao.insertShelfBookCrossRef(ShelfBookCrossRefEntity(shelfId = shelfId, bookId = bookId))
    }

    suspend fun removeBookFromShelf(shelfId: String, bookId: String) {
        shelfDao.deleteShelfBookCrossRef(shelfId = shelfId, bookId = bookId)
    }

    // --- Note Methods ---

    suspend fun addNote(bookId: String, locator: Locator, noteText: String) {
        noteDao.insert(
            NoteEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                chapterTitle = locator.title,
                noteText = noteText
            )
        )
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
        bookId: String,
        file: File,
        publication: Publication
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
            author = metadata.authors.joinToString(", ") { it.name },
            coverPath = coverPath,
            filePath = file.absolutePath,
            mediaType = "application/epub+zip",
            identifier = metadata.identifier,
            language = metadata.languages.firstOrNull(),
            progression = 0.0,
            lastLocatorJson = null,
            addedDate = System.currentTimeMillis(),
            lastReadDate = null
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
