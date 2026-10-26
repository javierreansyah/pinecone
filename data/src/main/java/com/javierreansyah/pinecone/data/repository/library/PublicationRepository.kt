package com.javierreansyah.pinecone.data.repository.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.BookAuthorCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookSpaceCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookTagCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.model.ReadingSessionToken
import com.javierreansyah.pinecone.data.model.library.ALL_SPACES_ID
import com.javierreansyah.pinecone.data.model.toEntity
import com.javierreansyah.pinecone.data.publication.PublicationFormats
import com.javierreansyah.pinecone.data.publication.PublicationMetadata
import com.javierreansyah.pinecone.data.publication.PublicationMetadataImporter
import com.javierreansyah.pinecone.data.publication.conversion.GeneratedEpubLocations
import com.javierreansyah.pinecone.data.publication.conversion.PdfReadModeConverter
import com.javierreansyah.pinecone.di.IoDispatcher
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val publicationOpener: PublicationOpener,
    private val assetRetriever: AssetRetriever,
    private val preferences: ReaderPreferences,
    @IoDispatcher private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val assets: PublicationAssetStore,
) {
    private val bookDao get() = database.bookDao()
    private val bookmarkDao get() = database.bookmarkDao()
    private val noteDao get() = database.noteDao()
    private val spaceDao get() = database.spaceDao()
    private val publicationDao get() = database.publicationDao()
    private val booksDir get() = assets.booksDirectory
    private val coversDir get() = assets.coversDirectory
    private val storageMutex get() = assets.mutationMutex
    private val conversionMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun importPublicationFolder(
        directory: androidx.documentfile.provider.DocumentFile,
        commit: suspend (BookWithDetails) -> Unit = {}
    ): BookWithDetails =
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
                importBook(Uri.fromFile(staged), "${directory.name.orEmpty()}.webpub", commit)
            } finally {
                staged.delete()
            }
        }

    suspend fun importBook(
        uri: Uri,
        displayName: String? = null,
        commit: suspend (BookWithDetails) -> Unit = {},
    ): BookWithDetails = withContext(ioDispatcher) {
        val generation = database.operationDao().runtime().generation
        assets.stage().use { stage ->
            val filename = displayName ?: originalFilename(uri)
            var source = File(stage.directory, "source")
            context.contentResolver.openInputStream(uri).let { input ->
                requireNotNull(input) { "Cannot read publication" }.use {
                    FileOutputStream(source).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val count = it.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
            }
            val hints = org.readium.r2.shared.util.format.FormatHints(
                fileExtension = filename.substringAfterLast('.', "").takeIf(String::isNotBlank)
                    ?.let { org.readium.r2.shared.util.FileExtension(it) },
                mediaType = context.contentResolver.getType(uri)
                    ?.let { org.readium.r2.shared.util.mediatype.MediaType(it) },
            )
            val format = assetRetriever.sniffFormat(source, hints).getOrNull()
                ?: error("Unsupported publication: $filename")
            val hash = generateFileHash(source)
            val renamed = File(stage.directory, "source.${format.fileExtension}")
            check(source.renameTo(renamed))
            source = renamed
            val publication =
                requireNotNull(openPublicationFromFile(source)) { "Cannot open publication" }
            var finalFile: File? = null
            var finalCover: File? = null
            var committed = false
            try {
                PublicationFormats.requireLocalResources(publication)
                val profile = PublicationFormats.profile(publication)
                val metadata = PublicationMetadataImporter.read(hash, source, publication.metadata)
                val prepared = createBookEntity(
                    hash,
                    source,
                    publication,
                    format.mediaType.toString(),
                    stage.directory
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
                val unsorted = preferences.readerSettings.first().unsortedEnabled
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                storageMutex.withLock {
                    withContext(kotlinx.coroutines.NonCancellable) {
                        database.withTransaction {
                            check(
                                database.operationDao().runtime()
                                    .let { !it.maintenance && it.generation == generation }) { "Library changed during import" }
                            val duplicate = bookDao.getById(hash)
                            if (duplicate != null) {
                                commit(duplicate)
                                committed = true
                                return@withTransaction duplicate
                            }
                            finalFile = File(
                                booksDir,
                                "${hash}_${UUID.randomUUID()}.${format.fileExtension}"
                            )
                            check(source.renameTo(finalFile))
                            prepared.coverPath?.let { path ->
                                finalCover = File(coversDir, "${hash}_${UUID.randomUUID()}.png")
                                check(File(path).renameTo(finalCover))
                            }
                            spaceDao.ensureAllSpace(context)
                            bookDao.insert(
                                prepared.copy(
                                    filePath = finalFile.absolutePath,
                                    coverPath = finalCover?.absolutePath, isUnsorted = unsorted
                                )
                            )
                            spaceDao.insertBookSpaceCrossRef(BookSpaceCrossRef(hash, ALL_SPACES_ID))
                            publicationDao.putRenditions(
                                listOf(
                                    PublicationRenditionEntity(
                                        hash, "original", finalFile.absolutePath,
                                        prepared.mediaType.orEmpty(), profile.name, hash,
                                    )
                                )
                            )
                            publicationDao.putCredits(metadata.credits.map { it.toEntity() })
                            metadata.credits.filter { it.role == "author" || it.role == "writer" }
                                .map { it.name }.distinct().forEachIndexed { index, name ->
                                    val inserted = bookDao.insertAuthor(AuthorEntity(name = name))
                                    val id = if (inserted == -1L) requireNotNull(
                                        bookDao.getAuthorByName(name)
                                    ).id else inserted
                                    bookDao.insertBookAuthorCrossRef(
                                        BookAuthorCrossRef(
                                            hash,
                                            id,
                                            index
                                        )
                                    )
                                }
                            metadata.subjects.distinct().forEach { name ->
                                val inserted = bookDao.insertTag(TagEntity(name = name))
                                val id =
                                    if (inserted == -1L) requireNotNull(bookDao.getTagByName(name)).id else inserted
                                bookDao.insertBookTagCrossRef(BookTagCrossRef(hash, id))
                            }
                            val result = requireNotNull(bookDao.getById(hash))
                            commit(result)
                            result
                        }.also { committed = true }
                    }
                }
            } finally {
                publication.close()
                if (!committed) {
                    finalFile?.delete(); finalCover?.delete()
                }
            }
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
            ?: context.getString(com.javierreansyah.pinecone.data.R.string.storage_untitled)
    }


    private fun PublicationRenditionEntity.isReusableReadMode(): Boolean =
        converterVersion == PdfReadModeConverter.VERSION && File(filePath).isFile

    /** Reuse is a storage mutation, but never a conversion operation. */
    suspend fun tryReuseReadMode(
        bookId: String,
        commit: suspend (PublicationRenditionEntity) -> Unit,
    ): Boolean = withContext(ioDispatcher) {
        storageMutex.withLock {
            database.withTransaction {
                check(
                    !database.operationDao().runtime().maintenance
                ) { "Library restore in progress" }
                if (database.operationDao()
                        .activeConversion(bookId) != null
                ) return@withTransaction false
                val existing = publicationDao.rendition(bookId, "read-mode")
                    ?.takeIf { it.isReusableReadMode() } ?: return@withTransaction false
                commit(existing)
                true
            }
        }
    }

    suspend fun createReadMode(
        bookId: String,
        forceReparse: Boolean = false,
        onConversionStart: () -> Unit = {},
        commit: suspend (PublicationRenditionEntity) -> Unit = {},
        progress: suspend (Int, Int) -> Unit
    ): PublicationRenditionEntity = withContext(ioDispatcher) {
        conversionMutex.withLock {
            val existing = publicationDao.rendition(bookId, "read-mode")
            if (!forceReparse && existing?.isReusableReadMode() == true
            ) return@withLock storageMutex.withLock {
                database.withTransaction {
                    check(
                        !database.operationDao().runtime().maintenance
                    ) { "Library restore in progress" }
                    check(
                        publicationDao.rendition(
                            bookId,
                            "read-mode"
                        ) == existing && File(existing.filePath).isFile
                    ) {
                        "Rendition changed"
                    }
                    commit(existing)
                    existing
                }
            }
            onConversionStart()
            val item = requireNotNull(bookDao.getById(bookId)).book
            val original =
                requireNotNull(openPublication(requireNotNull(bookDao.getById(bookId)), "original"))
            try {
                val token = requireNotNull(original.readingSessionToken())
                require(PublicationFormats.profile(original) == com.javierreansyah.pinecone.data.publication.ReaderProfile.PDF)
                val stage = assets.stage()
                val temp = File(stage.directory, "converted.epub")
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
                    final =
                        File(booksDir, "${bookId}_read-mode_${UUID.randomUUID()}.epub")
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val rendition = storageMutex.withLock {
                        withContext(kotlinx.coroutines.NonCancellable) {
                            database.withTransaction {
                                check(
                                    database.operationDao().runtime()
                                        .let { !it.maintenance && it.generation == token.generation }) { "Library changed" }
                                check(
                                    publicationDao.rendition(
                                        bookId,
                                        "original"
                                    )?.sha256 == token.sha256
                                ) { "Source changed" }
                                // Each attempt publishes a new immutable path. Hashing was completed in staging.
                                check(temp.renameTo(final))
                                ownsFinalFile = true
                                // Migrate only read-mode locators. IDs, labels, notes and PDF locations stay intact.
                                val latest =
                                    publicationDao.rendition(bookId, "read-mode") ?: existing
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
                                bookmarkDao.getByRendition(bookId, "read-mode")
                                    .forEach { bookmark ->
                                        val locator = requireNotNull(rebase(bookmark.locatorJson))
                                        if (locator != bookmark.locatorJson) bookmarkDao.updateLocator(
                                            bookmark.id,
                                            locator
                                        )
                                    }
                                noteDao.getByRendition(bookId, "read-mode").forEach { note ->
                                    val locator = requireNotNull(rebase(note.locatorJson))
                                    if (locator != note.locatorJson) noteDao.updateLocator(
                                        note.id,
                                        locator
                                    )
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
                                commit(updated)
                                updated
                            }.also { committed = true }
                        }
                    }
                    rendition
                } finally {
                    generated?.close(); temp.delete()
                    if (!committed && ownsFinalFile) final?.delete()
                    stage.close()
                }
            } finally {
                original.close()
            }
        }
    }

    suspend fun openPublication(
        book: BookWithDetails,
        renditionKey: String = book.book.activeRendition
    ): Publication? {
        var acquired: Publication? = null
        try {
            return withContext(ioDispatcher) {
                val access = storageMutex.withLock {
                    val current = bookDao.getById(book.book.id) ?: return@withLock null
                    val runtime = database.operationDao().runtime()
                    if (runtime.maintenance) return@withLock null
                    val rendition =
                        publicationDao.rendition(book.book.id, renditionKey) ?: return@withLock null
                    val file = File(rendition.filePath)
                    if (!file.isFile) return@withLock null
                    OpenAccess(
                        current.copy(book = current.book.copy(activeRendition = renditionKey)),
                        file,
                        ReadingSessionToken(
                            runtime.generation,
                            book.book.id,
                            renditionKey,
                            rendition.sha256
                        ),
                        assets.lease(file)
                    )
                } ?: return@withContext null
                try {
                    openPublicationFromFile(access.file, access.book, access)?.also {
                        acquired = it
                    }
                } finally {
                    if (acquired == null) access.lease.close()
                }
            }
        } catch (cancelled: CancellationException) {
            acquired?.close()
            throw cancelled
        }
    }

    private data class OpenAccess(
        val book: BookWithDetails,
        val file: File,
        val token: ReadingSessionToken,
        val lease: AutoCloseable
    )


    private suspend fun openPublicationFromFile(
        file: File,
        book: BookWithDetails? = null,
        access: OpenAccess? = null,
    ): Publication? {
        return try {
            val url = file.toUrl(isDirectory = false)
            val asset = assetRetriever.retrieve(url).getOrNull() ?: return null
            publicationOpener.open(asset, allowUserInteraction = false, onCreatePublication = {
                if (access != null) servicesBuilder[LeasedPublication::class] =
                    { LeasedPublication(access.lease, access.token) }
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
        coverDirectory: File = coversDir,
    ): BookEntity {
        val metadata = publication.metadata

        val coverPath = try {
            val coverBitmap = publication.coverFitting(android.util.Size(800, 1200))

            if (coverBitmap != null) {
                val coverFile = File(coverDirectory, "$bookId.png")
                withContext(ioDispatcher) {
                    FileOutputStream(coverFile).use { out ->
                        coverBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
                coverFile.absolutePath
            } else null
        } catch (cancelled: CancellationException) {
            throw cancelled
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

    private suspend fun generateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
