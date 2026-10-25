package com.javierreansyah.pinecone.data.repository.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.BookAuthorCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookDao
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookSpaceCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookTagCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.BookmarkDao
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteDao
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity
import com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfDao
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceDao
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.publication.PublicationFormats
import com.javierreansyah.pinecone.data.publication.PublicationMetadata
import com.javierreansyah.pinecone.data.publication.PublicationMetadataImporter
import com.javierreansyah.pinecone.data.publication.conversion.GeneratedEpubLocations
import com.javierreansyah.pinecone.data.publication.conversion.PdfReadModeConverter
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory
import com.javierreansyah.pinecone.ui.features.library.ALL_SPACES_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.coverFitting
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
    private val spaceDao: SpaceDao,
    private val publicationOpener: PublicationOpener,
    private val assetRetriever: AssetRetriever,
    private val preferences: ReaderPreferences,
    private val audioSession: dagger.Lazy<com.javierreansyah.pinecone.reader.audio.AudioReadingSession>,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val mainDispatcher: kotlinx.coroutines.CoroutineDispatcher,
) {

    private val booksDir: File
        get() = File(context.filesDir, "publication-assets").also { it.mkdirs() }

    private val coversDir: File
        get() = File(context.filesDir, "publication-covers").also { it.mkdirs() }

    fun getAllBooks(): Flow<List<BookWithDetails>> = bookDao.getAllBooks()

    fun getVisibleBooks(spaceId: String?): Flow<List<BookWithDetails>> =
        bookDao.getVisibleBooks(spaceId)

    fun hasUnsortedBooks(): Flow<Boolean> = bookDao.hasUnsortedBooks()

    suspend fun importPublicationFolder(directory: androidx.documentfile.provider.DocumentFile): BookWithDetails? =
        withContext(ioDispatcher) {
            val staged = File(
                context.cacheDir,
                "${
                    directory.name.orEmpty().replace(Regex("[^a-zA-Z0-9_-]"), "_")
                }_${UUID.randomUUID()}.webpub"
            )
            try {
                com.javierreansyah.pinecone.data.publication.LocalPublicationPackager.packageFolder(
                    context,
                    directory,
                    staged
                )
                importBook(Uri.fromFile(staged))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                timber.log.Timber.e(failure); null
            } finally {
                staged.delete()
            }
        }

    private val storageMutex = Mutex()

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
                ?.forEach { it.delete() }
            coversDir.listFiles()?.asSequence()
                ?.filter { it.isFile && it.canonicalPath !in referencedCovers }
                ?.forEach { it.delete() }
        }
    }

    fun getArchivedBooks(): Flow<List<BookWithDetails>> = bookDao.getArchivedBooks()

    fun getUnsortedBooks(): Flow<List<BookWithDetails>> =
        bookDao.getAllBooks()
            .map { list -> list.filter { it.book.isUnsorted } }

    suspend fun releaseUnsortedBooks(): Unit = database.withTransaction {
        bookDao.releaseUnsortedBooks()
    }

    private suspend fun ensureAllSpace() {
        if (spaceDao.getAllSpacesSync().none { it.id == ALL_SPACES_ID }) {
            spaceDao.insertSpace(
                SpaceEntity(
                    ALL_SPACES_ID,
                    context.getString(com.javierreansyah.pinecone.R.string.action_all),
                    0L
                )
            )
        }
    }

    fun searchShelves(query: String): Flow<List<ShelfEntity>> = shelfDao.searchShelves(query)

    suspend fun getBook(id: String): BookWithDetails? = bookDao.getById(id)

    fun getBookFlow(id: String): Flow<BookWithDetails?> = bookDao.getByIdFlow(id)

    suspend fun importBook(uri: Uri): BookWithDetails? = withContext(ioDispatcher) {
        storageMutex.withLock { importPublication(uri) }
    }

    private suspend fun importPublication(uri: Uri): BookWithDetails? {
        val tempFile = File(booksDir, "import_${UUID.randomUUID()}")
        var finalFile: File? = null
        var publication: Publication? = null
        var committed = false
        try {
            val filename = originalFilename(uri)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // Some supported audio formats (e.g. WAV/AIFF) need filename/MIME hints in Readium's
            // sniffer. Temporary filenames intentionally have no extension, so carry the source
            // hints explicitly rather than misclassifying or falling back to EPUB.
            val hints = org.readium.r2.shared.util.format.FormatHints(
                fileExtension = filename.substringAfterLast('.', "").takeIf(String::isNotBlank)
                    ?.let { org.readium.r2.shared.util.FileExtension(it) },
                mediaType = context.contentResolver.getType(uri)
                    ?.let { org.readium.r2.shared.util.mediatype.MediaType(it) },
            )
            val mediaType = assetRetriever.sniffFormat(tempFile, hints).getOrNull()
                ?: error("Unsupported publication: $filename")
            val extension = mediaType.fileExtension

            val bookId = generateFileHash(tempFile)

            if (bookDao.exists(bookId) > 0) {
                tempFile.delete()
                return bookDao.getById(bookId)
            }

            finalFile = File(booksDir, "$bookId.$extension")
            check(tempFile.renameTo(finalFile)) { "Cannot store publication" }

            publication = openPublicationFromFile(finalFile) ?: run {
                finalFile.delete()
                return null
            }
            val profile = PublicationFormats.profile(publication)
            PublicationFormats.requireLocalResources(publication)
            val metadata = PublicationMetadataImporter.read(bookId, finalFile, publication.metadata)
            val entity = createBookEntity(
                bookId,
                finalFile,
                publication,
                mediaType.mediaType.toString()
            ).copy(
                title = metadata.title ?: publication.metadata.title
                ?: filename.substringBeforeLast('.'),
                description = metadata.description ?: publication.metadata.description,
                metadataJson = metadata.metadata.encode(),
                sourceMetadataJson = metadata.metadata.encode(),
                language = metadata.metadata.languages.firstOrNull(),
                originalFilename = filename,
                published = metadata.metadata.published.ifBlank { null },
                publisher = metadata.credits.filter { it.role == "publisher" }
                    .joinToString(", ") { it.name }.ifBlank { null },
            )
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            withContext(kotlinx.coroutines.NonCancellable) {
                database.withTransaction {
                    ensureAllSpace()
                    bookDao.insert(entity.copy(isUnsorted = preferences.readerSettings.first().unsortedEnabled))
                    spaceDao.insertBookSpaceCrossRef(BookSpaceCrossRef(bookId, ALL_SPACES_ID))
                    publicationDao.putRenditions(
                        listOf(
                            PublicationRenditionEntity(
                                bookId,
                                "original",
                                finalFile.absolutePath,
                                entity.mediaType.orEmpty(),
                                profile.name,
                                bookId,
                            )
                        )
                    )
                    publicationDao.putCredits(metadata.credits)

                    val authors =
                        metadata.credits.filter { it.role == "author" || it.role == "writer" }
                            .map { it.name }.distinct()
                    authors.forEachIndexed { index, authorName ->
                        var authorId = bookDao.insertAuthor(AuthorEntity(name = authorName))
                        if (authorId == -1L) {
                            authorId =
                                bookDao.getAuthorByName(authorName)?.id ?: return@forEachIndexed
                        }
                        bookDao.insertBookAuthorCrossRef(
                            BookAuthorCrossRef(
                                bookId,
                                authorId,
                                index
                            )
                        )
                    }

                    val tags = metadata.subjects
                    tags.forEach { tagName ->
                        var tagId = bookDao.insertTag(TagEntity(name = tagName))
                        if (tagId == -1L) {
                            tagId = bookDao.getTagByName(tagName)?.id ?: return@forEach
                        }
                        bookDao.insertBookTagCrossRef(BookTagCrossRef(bookId, tagId))
                    }
                }
                committed = true
            }
            return bookDao.getById(bookId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            timber.log.Timber.e(e)
            return null
        } finally {
            publication?.close()
            tempFile.delete()
            if (!committed) finalFile?.delete()
        }
    }

    private fun originalFilename(uri: Uri): String {
        if (uri.scheme == "content") context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
            ?: context.getString(com.javierreansyah.pinecone.R.string.publication_untitled)
    }

    suspend fun rendition(bookId: String, key: String): PublicationRenditionEntity? =
        publicationDao.rendition(bookId, key)

    fun renditionFlow(bookId: String, key: String): Flow<PublicationRenditionEntity?> =
        publicationDao.renditionFlow(bookId, key)

    suspend fun setReadingOverrides(bookId: String, key: String, json: String): Unit? =
        database.withTransaction {
            publicationDao.rendition(bookId, key)
                ?.let { publicationDao.putRenditions(listOf(it.copy(preferencesJson = json))) }
        }

    suspend fun activateRendition(bookId: String, key: String): Unit = database.withTransaction {
        val item = bookDao.getById(bookId)?.book ?: return@withTransaction
        val target = publicationDao.rendition(bookId, key) ?: return@withTransaction
        bookDao.update(
            item.copy(
                activeRendition = key,
                progression = target.progression,
                lastLocatorJson = target.lastLocatorJson,
                furthestProgression = target.furthestProgression,
                furthestLocatorJson = target.furthestLocatorJson,
                jumpOriginLocatorJson = target.jumpOriginLocatorJson,
            )
        )
    }

    suspend fun createReadMode(
        bookId: String,
        forceReparse: Boolean = false,
        onConversionStart: () -> Unit = {},
        progress: suspend (Int, Int) -> Unit
    ): PublicationRenditionEntity = withContext(ioDispatcher) {
        storageMutex.withLock {
            val existing = publicationDao.rendition(bookId, "read-mode")
            if (!forceReparse && existing != null && existing.converterVersion == PdfReadModeConverter.VERSION &&
                File(existing.filePath).isFile
            ) return@withLock existing
            onConversionStart()
            val item = requireNotNull(bookDao.getById(bookId)).book
            val original = requireNotNull(openPublicationFromFile(File(item.filePath)))
            require(PublicationFormats.profile(original) == com.javierreansyah.pinecone.data.publication.ReaderProfile.PDF)
            val temp = File(booksDir, "conversion_${UUID.randomUUID()}.epub")
            var generated: Publication? = null
            var final: File? = null
            var committed = false
            var ownsFinalFile = false
            try {
                val sources =
                    mutableListOf<com.javierreansyah.pinecone.data.publication.conversion.PdfSource>()
                val mapping = try {
                    for (link in original.readingOrder) {
                        val resource = requireNotNull(original.get(link))
                        val pdf = MuPdfDocumentFactory().open(resource, null).getOrNull()
                            ?: error("Cannot extract PDF")
                        sources += com.javierreansyah.pinecone.data.publication.conversion.PdfSource(
                            pdf,
                            link.href.toString()
                        )
                    }
                    PdfReadModeConverter().convert(
                        sources,
                        temp,
                        "urn:pinecone:$bookId:read-mode",
                        item.title,
                        item.language ?: "und",
                        com.javierreansyah.pinecone.data.publication.conversion.generatedPublicationStrings(
                            context.resources
                        ),
                        previousSourceMap = existing?.sourceMapJson?.let {
                            kotlinx.serialization.json.Json.decodeFromString<List<com.javierreansyah.pinecone.data.publication.conversion.SourcePage>>(
                                it
                            )
                        }.orEmpty(),
                        progress = progress,
                    )
                } finally {
                    sources.forEach { it.document.close() }
                }
                generated =
                    requireNotNull(openPublicationFromFile(temp)) { "Generated EPUB failed to open" }
                require(PublicationFormats.profile(generated) == com.javierreansyah.pinecone.data.publication.ReaderProfile.EPUB)
                generated.close(); generated = null
                val hash = generateFileHash(temp)
                final = File(booksDir, "${bookId}_read-mode_$hash.epub")
                if (final.exists()) {
                    check(generateFileHash(final) == hash) { "Existing rendition file is damaged" }
                } else {
                    check(temp.renameTo(final))
                    ownsFinalFile = true
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val rendition = withContext(kotlinx.coroutines.NonCancellable) {
                    database.withTransaction {
                        // Migrate only read-mode locators. IDs, labels, notes and PDF locations stay intact.
                        val latest = publicationDao.rendition(bookId, "read-mode") ?: existing
                        val before = latest?.sourceMapJson?.let {
                            kotlinx.serialization.json.Json.decodeFromString<List<com.javierreansyah.pinecone.data.publication.conversion.SourcePage>>(
                                it
                            )
                        }.orEmpty()
                        val after =
                            kotlinx.serialization.json.Json.decodeFromString<List<com.javierreansyah.pinecone.data.publication.conversion.SourcePage>>(
                                mapping
                            )

                        fun rebase(locator: String?) =
                            GeneratedEpubLocations.remap(locator, before, after)

                        val updated = (latest ?: PublicationRenditionEntity(
                            bookId,
                            "read-mode",
                            final.absolutePath,
                            "application/epub+zip",
                            "EPUB",
                            hash
                        )).copy(
                            filePath = final.absolutePath,
                            sha256 = hash,
                            converterVersion = PdfReadModeConverter.VERSION,
                            sourceMapJson = mapping,
                            lastLocatorJson = rebase(latest?.lastLocatorJson),
                            furthestLocatorJson = rebase(latest?.furthestLocatorJson),
                            jumpOriginLocatorJson = rebase(latest?.jumpOriginLocatorJson),
                        )
                        bookmarkDao.getByRendition(bookId, "read-mode").forEach { bookmark ->
                            val locator = requireNotNull(rebase(bookmark.locatorJson))
                            if (locator != bookmark.locatorJson) bookmarkDao.updateLocator(
                                bookmark.id,
                                locator
                            )
                        }
                        noteDao.getByRendition(bookId, "read-mode").forEach { note ->
                            val locator = requireNotNull(rebase(note.locatorJson))
                            if (locator != note.locatorJson) noteDao.updateLocator(note.id, locator)
                        }
                        publicationDao.putRenditions(listOf(updated))
                        val book = requireNotNull(bookDao.getById(bookId)).book
                        if (book.activeRendition == "read-mode") bookDao.update(
                            book.copy(
                                lastLocatorJson = updated.lastLocatorJson,
                                furthestLocatorJson = updated.furthestLocatorJson,
                                jumpOriginLocatorJson = updated.jumpOriginLocatorJson,
                            )
                        )
                        updated
                    }.also { committed = true }
                }
                rendition
            } finally {
                generated?.close(); original.close(); temp.delete()
                if (!committed && ownsFinalFile) final?.delete()
            }
        }
    }

    suspend fun openPublication(book: BookWithDetails): Publication? = withContext(ioDispatcher) {
        val file = File(
            publicationDao.rendition(book.book.id, book.book.activeRendition)?.filePath
                ?: book.book.filePath
        )
        if (!file.exists()) return@withContext null
        openPublicationFromFile(file, book)
    }

    suspend fun saveReadingPosition(
        bookId: String,
        locator: Locator,
        renditionKey: String = "original"
    ): Unit = database.withTransaction {
        val rendition = publicationDao.rendition(bookId, renditionKey) ?: return@withTransaction
        val detailsBook = bookDao.getById(bookId)?.book ?: return@withTransaction
        val progression = locator.locations.totalProgression ?: 0.0

        val hasJumpOrigin = !rendition.jumpOriginLocatorJson.isNullOrBlank()
        val isNewProgressionFurther = progression > rendition.furthestProgression

        val newFurthestProgression =
            if (!hasJumpOrigin && isNewProgressionFurther) progression else rendition.furthestProgression
        val newFurthestLocatorJson = if (!hasJumpOrigin && isNewProgressionFurther) locator.toJSON()
            .toString() else rendition.furthestLocatorJson

        publicationDao.putRenditions(
            listOf(
                rendition.copy(
                    lastLocatorJson = locator.toJSON().toString(),
                    progression = progression,
                    furthestProgression = newFurthestProgression,
                    furthestLocatorJson = newFurthestLocatorJson
                )
            )
        )
        if (detailsBook.activeRendition != renditionKey) return@withTransaction

        bookDao.update(
            detailsBook.copy(
                progression = progression,
                lastLocatorJson = locator.toJSON().toString(),
                lastReadDate = System.currentTimeMillis(),
                furthestProgression = newFurthestProgression,
                furthestLocatorJson = newFurthestLocatorJson,
            ),
        )
    }

    suspend fun saveJumpOrigin(bookId: String, locator: Locator): Unit = database.withTransaction {
        val detailsBook2 = bookDao.getById(bookId)?.book ?: return@withTransaction
        if (detailsBook2.jumpOriginLocatorJson.isNullOrBlank()) {
            bookDao.update(
                detailsBook2.copy(
                    jumpOriginLocatorJson = locator.toJSON().toString(),
                ),
            )
            publicationDao.rendition(bookId, detailsBook2.activeRendition)?.let {
                publicationDao.putRenditions(
                    listOf(
                        it.copy(
                            jumpOriginLocatorJson = locator.toJSON().toString()
                        )
                    )
                )
            }
        }
    }

    suspend fun saveJumpReadingPosition(bookId: String, locator: Locator): Unit? =
        database.withTransaction {
            val detailsBook3 = bookDao.getById(bookId)?.book ?: return@withTransaction
            val progression = locator.locations.totalProgression ?: 0.0

            val existingJumpOrigin = detailsBook3.jumpOriginLocatorJson
            val newJumpOriginJson = if (existingJumpOrigin.isNullOrBlank()) {
                detailsBook3.lastLocatorJson
            } else {
                existingJumpOrigin
            }

            bookDao.update(
                detailsBook3.copy(
                    progression = progression,
                    lastLocatorJson = locator.toJSON().toString(),
                    lastReadDate = System.currentTimeMillis(),
                    jumpOriginLocatorJson = newJumpOriginJson,
                ),
            )
            publicationDao.rendition(bookId, detailsBook3.activeRendition)?.let {
                publicationDao.putRenditions(
                    listOf(
                        it.copy(
                            lastLocatorJson = locator.toJSON().toString(),
                            progression = progression,
                            jumpOriginLocatorJson = newJumpOriginJson
                        )
                    )
                )
            }
        }

    suspend fun clearJumpOrigin(bookId: String): Unit = database.withTransaction {
        val detailsBook4 = bookDao.getById(bookId)?.book ?: return@withTransaction
        if (detailsBook4.jumpOriginLocatorJson != null) {
            bookDao.update(
                detailsBook4.copy(
                    jumpOriginLocatorJson = null,
                ),
            )
            publicationDao.rendition(bookId, detailsBook4.activeRendition)?.let {
                publicationDao.putRenditions(listOf(it.copy(jumpOriginLocatorJson = null)))
            }
        }
    }

    suspend fun resetFurthestToCurrent(bookId: String): Unit? = database.withTransaction {
        val detailsBook5 = bookDao.getById(bookId)?.book ?: return@withTransaction
        bookDao.update(
            detailsBook5.copy(
                furthestProgression = detailsBook5.progression,
                furthestLocatorJson = detailsBook5.lastLocatorJson,
            ),
        )
        publicationDao.rendition(bookId, detailsBook5.activeRendition)?.let {
            publicationDao.putRenditions(
                listOf(
                    it.copy(
                        furthestProgression = it.progression,
                        furthestLocatorJson = it.lastLocatorJson
                    )
                )
            )
        }
    }

    suspend fun getLastLocator(bookId: String): Locator? {
        val json = bookDao.getById(bookId)?.book?.lastLocatorJson ?: return null
        return try {
            Locator.fromJSON(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addBookmark(
        bookId: String,
        locator: Locator,
        chapterTitle: String? = null,
        renditionKey: String = "original"
    ) {
        bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                chapterTitle = chapterTitle ?: locator.title,
                renditionKey = renditionKey,
            ),
        )
    }

    fun getBookmarks(bookId: String): Flow<List<BookmarkEntity>> = bookmarkDao.getByBookId(bookId)

    suspend fun removeBookmark(id: Long) {
        bookmarkDao.deleteById(id)
    }

    suspend fun deleteBook(bookId: String): Unit = withContext(ioDispatcher) {
        withAssetTransaction {
            val book = bookDao.getById(bookId)?.book ?: return@withAssetTransaction
            val renditions = publicationDao.renditions(bookId)
            withContext(mainDispatcher) {
                val session =
                    audioSession.get()
                if (session.state.value.bookId == bookId) session.releasePlayer()
            }

            database.withTransaction {
                bookDao.delete(book)
                shelfDao.deleteEmptyShelves()

                bookDao.deleteOrphanAuthors()
                bookDao.deleteOrphanTags()
            }
            val remainingBooks = bookDao.getAllBooksSync()
            val referenced = (publicationDao.allRenditions().map { it.filePath } +
                    remainingBooks.flatMap { listOfNotNull(it.filePath, it.coverPath) })
                .map { File(it).canonicalPath }.toSet()
            (renditions.map { it.filePath } + listOfNotNull(
                book.filePath,
                book.coverPath
            )).distinct().forEach {
                val file = File(it)
                if (file.canonicalPath !in referenced) file.delete()
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
        val bookDetailsBook = bookDao.getById(bookId)?.book ?: return@withContext

        var newCoverPath = bookDetailsBook.coverPath
        if (coverUri != null) {
            val coverFile = File(coversDir, "${bookId}_custom_${System.currentTimeMillis()}.png")
            try {
                context.contentResolver.openInputStream(coverUri)?.use { input ->
                    FileOutputStream(coverFile).use { output ->
                        input.copyTo(output)
                    }
                }
                newCoverPath = coverFile.absolutePath
                bookDetailsBook.coverPath?.let { oldPath ->
                    if (oldPath != newCoverPath) {
                        File(oldPath).delete()
                    }
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e)
            }
        }

        database.withTransaction {
            bookDao.update(
                bookDetailsBook.copy(
                    title = title, description = description, coverPath = newCoverPath,
                    metadataJson = metadata?.encode() ?: bookDetailsBook.metadataJson,
                    language = if (metadata != null) metadata.languages.firstOrNull() else bookDetailsBook.language,
                    identifier = if (metadata != null) metadata.identifiers["identifier"]
                        ?: metadata.identifiers.values.firstOrNull() else bookDetailsBook.identifier,
                    publisher = credits?.filter { it.role == "publisher" }
                        ?.joinToString(", ") { it.name } ?: bookDetailsBook.publisher,
                    published = metadata?.published ?: bookDetailsBook.published,
                ),
            )

            if (credits != null) {
                publicationDao.deleteCredits(bookId)
                publicationDao.putCredits(credits)
            }

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
        val book = bookDao.getById(bookId)?.book ?: return
        bookDao.update(book.copy(isArchived = !book.isArchived))
    }

    suspend fun toggleReadStatus(bookId: String) {
        val book = bookDao.getById(bookId)?.book ?: return
        bookDao.update(book.copy(isRead = !book.isRead))
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
            ensureAllSpace()
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
            ensureAllSpace()
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

    suspend fun addBookToShelf(shelfId: String, bookId: String) {
        database.withTransaction {
            val (_, shelfSpaceId, _, _) = shelfDao.getShelfById(shelfId) ?: return@withTransaction
            ensureAllSpace()
            bookDao.markOrganized(bookId)
            spaceDao.insertBookSpaceCrossRef(BookSpaceCrossRef(bookId, ALL_SPACES_ID))
            shelfDao.insertShelfBookCrossRef(
                ShelfBookCrossRefEntity(
                    shelfId = shelfId, bookId = bookId,
                    orderIndex = shelfDao.getNextBookOrderIndex(shelfId),
                ),
            )
            spaceDao.insertBookSpaceCrossRef(
                BookSpaceCrossRef(
                    bookId = bookId,
                    spaceId = shelfSpaceId,
                ),
            )
        }
    }

    suspend fun removeBookFromShelf(shelfId: String, bookId: String) {
        database.withTransaction {
            shelfDao.deleteShelfBookCrossRef(shelfId = shelfId, bookId = bookId)
            shelfDao.deleteEmptyShelves()
        }
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

    suspend fun addBookToSpace(spaceId: String, bookId: String) {
        database.withTransaction {
            ensureAllSpace()
            bookDao.markOrganized(bookId)
            spaceDao.insertBookSpaceCrossRef(BookSpaceCrossRef(bookId, ALL_SPACES_ID))
            spaceDao.insertBookSpaceCrossRef(
                BookSpaceCrossRef(
                    bookId = bookId,
                    spaceId = spaceId,
                ),
            )
        }
    }

    suspend fun removeBookFromSpace(spaceId: String, bookId: String) {
        if (spaceId == ALL_SPACES_ID) return
        database.withTransaction {
            spaceDao.deleteBookSpaceCrossRef(spaceId = spaceId, bookId = bookId)
            val spaceShelves = shelfDao.getShelvesBySpaceSync(spaceId)
            spaceShelves.forEach { shelf ->
                shelfDao.deleteShelfBookCrossRef(shelfId = shelf.id, bookId = bookId)
            }
            shelfDao.deleteEmptyShelves()
        }
    }

    fun getAllBookSpaceCrossRefs(): Flow<List<BookSpaceCrossRef>> =
        spaceDao.getAllBookSpaceCrossRefs()

    suspend fun addNote(
        bookId: String,
        locator: Locator,
        noteText: String,
        color: Int = -1,
        chapterTitle: String? = null,
        renditionKey: String = "original",
    ) {
        noteDao.insert(
            NoteEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                chapterTitle = chapterTitle ?: locator.title,
                noteText = noteText,
                color = color,
                renditionKey = renditionKey,
            ),
        )
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.update(note)
    }

    fun getNotes(bookId: String): Flow<List<NoteEntity>> = noteDao.getByBookId(bookId)

    suspend fun removeNote(id: Long) {
        noteDao.deleteById(id)
    }

    private suspend fun openPublicationFromFile(
        file: File,
        book: BookWithDetails? = null
    ): Publication? {
        return try {
            val url = file.toUrl(isDirectory = false)
            val asset = assetRetriever.retrieve(url).getOrNull() ?: return null
            publicationOpener.open(asset, allowUserInteraction = false, onCreatePublication = {
                if (book != null) {
                    val details = PublicationMetadata.decode(book.book.metadataJson)
                    manifest = manifest.copy(
                        metadata = manifest.metadata.copy(
                            localizedTitle = org.readium.r2.shared.publication.LocalizedString(book.book.title),
                            localizedSubtitle = details.subtitle.takeIf(String::isNotBlank)
                                ?.let { org.readium.r2.shared.publication.LocalizedString(it) },
                            authors = book.sortedAuthors.map {
                                org.readium.r2.shared.publication.Contributor(
                                    it.name
                                )
                            },
                            narrators = book.credits.filter { it.role == "narrator" }
                                .sortedBy { it.position }
                                .map { org.readium.r2.shared.publication.Contributor(it.name) },
                            description = book.book.description,
                            languages = details.languages,
                            readingProgression = when (details.readingDirection.lowercase()) {
                                "rtl" -> org.readium.r2.shared.publication.ReadingProgression.RTL
                                "ltr" -> org.readium.r2.shared.publication.ReadingProgression.LTR
                                else -> manifest.metadata.readingProgression
                            },
                        )
                    )
                    // The legacy Readium web navigator injects its pagination, selection and
                    // typography bridge only for the EPUB profile. Opt HTML-only WebPubs into
                    // that presentation bridge in memory; their stored media type and raw source
                    // metadata remain unchanged. No EPUB container or OPF is fabricated.
                    if (manifest.readingOrder.isNotEmpty() && manifest.readingOrder.all { it.mediaType?.isHtml == true }) {
                        manifest = manifest.copy(
                            metadata = manifest.metadata.copy(
                                conformsTo = manifest.metadata.conformsTo + Publication.Profile.EPUB,
                            )
                        )
                    }
                }
            }).getOrNull()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun createBookEntity(
        bookId: String, file: File, publication: Publication, mediaType: String? = null,
    ): BookEntity {
        val metadata = publication.metadata

        val coverPath = try {
            val coverBitmap = publication.coverFitting(android.util.Size(800, 1200))

            if (coverBitmap != null) {
                val coverFile = File(coversDir, "$bookId.png")
                withContext(ioDispatcher) {
                    FileOutputStream(coverFile).use { out ->
                        coverBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
                coverFile.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }

        return BookEntity(
            id = bookId,
            title = metadata.title ?: file.nameWithoutExtension,
            coverPath = coverPath,
            filePath = file.absolutePath,
            mediaType = requireNotNull(mediaType),
            identifier = metadata.identifier,
            language = metadata.languages.firstOrNull(),
            addedDate = System.currentTimeMillis(),
            description = metadata.description,
            publisher = metadata.publishers.joinToString(", ") { it.name }
                .takeIf { it.isNotEmpty() },
            published = metadata.published?.toString(),
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
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
