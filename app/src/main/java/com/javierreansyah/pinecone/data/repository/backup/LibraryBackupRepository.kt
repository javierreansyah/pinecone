package com.javierreansyah.pinecone.data.repository.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipOutputStream

class LibraryBackupRepository(private val context: Context) {
    private val preferences = ReaderPreferences(context)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    }

    suspend fun performBackup(force: Boolean = false): Boolean =
        performBackupResult(force).isSuccess

    suspend fun performBackupResult(force: Boolean = false): BackupResult =
        withContext(Dispatchers.IO) {
            operationMutex.withLock { createBackup(force) }
        }

    suspend fun restoreBackup(uri: Uri): Boolean = restoreBackupResult(uri).isSuccess

    suspend fun restoreBackupResult(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        operationMutex.withLock { restore(uri) }
    }

    suspend fun completePendingSettingsRestore() = withContext(Dispatchers.IO) {
        val pending = app.database.backupStateDao().pendingSettingsJson() ?: return@withContext
        applyRestoredSettings(json.decodeFromString<ReaderSettings>(pending))
        app.database.backupStateDao().setPendingSettingsJson(null)
    }

    private suspend fun snapshot(): Pair<Long, LibraryBackupPayload> {
        val database = app.database
        return database.withTransaction {
            val revision = database.backupStateDao().revision()
            revision to LibraryBackupPayload(
                books = database.bookDao().getAllBooksSync().map { it.toBackupRecord() },
                bookmarks = database.bookmarkDao().getAllBookmarksSync().map { it.toBackupRecord() },
                shelves = database.shelfDao().getAllShelvesSync().map { it.toBackupRecord() },
                shelfBookCrossRefs = database.shelfDao().getAllShelfBookCrossRefsSync()
                    .map { it.toBackupRecord() },
                notes = database.noteDao().getAllNotesSync().map { it.toBackupRecord() },
                authors = database.bookDao().getAllAuthorsSync().map { it.toBackupRecord() },
                tags = database.bookDao().getAllTagsSync().map { it.toBackupRecord() },
                bookAuthorCrossRefs = database.bookDao().getAllBookAuthorCrossRefsSync()
                    .map { it.toBackupRecord() },
                bookTagCrossRefs = database.bookDao().getAllBookTagCrossRefsSync()
                    .map { it.toBackupRecord() },
                spaces = database.spaceDao().getAllSpacesSync().map { it.toBackupRecord() },
                bookSpaceCrossRefs = database.spaceDao().getAllBookSpaceCrossRefsSync()
                    .map { it.toBackupRecord() }
            )
        }
    }

    private val app: PineconeApplication
        get() = context.applicationContext as PineconeApplication

    private fun libraryFolder(root: DocumentFile): DocumentFile? =
        root.findFile(LIBRARY_FOLDER) ?: root.createDirectory(LIBRARY_FOLDER)

    private suspend fun createBackup(force: Boolean): BackupResult {
        var partial: DocumentFile? = null
        var verificationDir: File? = null
        var published = false
        return try {
            val settings = preferences.readerSettings.first()
            val root = settings.backupFolderUri.takeIf { it.isNotBlank() }
                ?.toUri()?.let { DocumentFile.fromTreeUri(context, it) }
                ?: return BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
            if (!root.canWrite()) return BackupResult.Failure(BackupFailure.PERMISSION_DENIED)

            val (revision, payload) = snapshot()
            if (!force && revision == settings.lastBackupRevision) return BackupResult.Skipped

            val sourceFiles = linkedMapOf<String, File>()
            for (book in payload.books) {
                val bookFile = File(book.filePath)
                if (!bookFile.isFile) return BackupResult.Failure(BackupFailure.MISSING_BOOK_FILE)
                sourceFiles["books/${bookFile.name}"] = bookFile
                book.coverPath?.let { coverPath ->
                    val cover = File(coverPath)
                    if (!cover.isFile) return BackupResult.Failure(BackupFailure.MISSING_BOOK_FILE)
                    sourceFiles["covers/${cover.name}"] = cover
                }
            }

            val dataBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            val portableSettings = settings.copy(
                backupFolderUri = "", lastBackupTime = 0L, lastBackupRevision = -1L,
                installedDictionaries = emptyList(), activeDictionaryId = ""
            )
            val settingsBytes = json.encodeToString(portableSettings).toByteArray(Charsets.UTF_8)
            val entries = mutableListOf(
                BackupArchiveIO.descriptor("library.json", dataBytes),
                BackupArchiveIO.descriptor("settings.json", settingsBytes)
            )
            sourceFiles.forEach { (path, file) -> entries += BackupArchiveIO.descriptor(path, file) }

            val kind = if (force) "manual" else "automatic"
            val manifest = LibraryBackupManifest(
                createdAt = System.currentTimeMillis(),
                backupKind = kind,
                appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                databaseRevision = revision,
                recordCounts = recordCounts(payload),
                entries = entries,
                dictionaries = app.dictionaryBackupManager.currentReferences(),
                activeDictionaryId = settings.activeDictionaryId
            )
            val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            val folder = libraryFolder(root)
                ?: return BackupResult.Failure(BackupFailure.PERMISSION_DENIED)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val indicator = if (force) "M" else "A"
            val finalName = "${stamp}_${indicator}.pine"
            partial = folder.createFile("application/octet-stream", "$finalName.partial")
                ?: return BackupResult.Failure(BackupFailure.IO_ERROR)
            val output = context.contentResolver.openOutputStream(partial.uri, "wt")
                ?: return BackupResult.Failure(BackupFailure.IO_ERROR)
            output.use { stream ->
                ZipOutputStream(stream.buffered()).use { zip ->
                    BackupArchiveIO.writeBytes(zip, "manifest.json", manifestBytes)
                    BackupArchiveIO.writeBytes(zip, "library.json", dataBytes)
                    BackupArchiveIO.writeBytes(zip, "settings.json", settingsBytes)
                    sourceFiles.forEach { (path, file) ->
                        BackupArchiveIO.writeFile(zip, path, file, compressed(path))
                    }
                }
            }

            verificationDir = uniqueCacheDir("backup_verify")
            verifyNewArchive(partial.uri, verificationDir)
            if (app.database.backupStateDao().revision() != revision) {
                return BackupResult.Failure(BackupFailure.CONCURRENT_CHANGE)
            }
            if (!partial.renameTo(finalName)) return BackupResult.Failure(BackupFailure.IO_ERROR)
            partial = null
            published = true
            cleanupOldBackups(folder, indicator)
            preferences.updateAllSettings(settings.copy(
                lastBackupTime = System.currentTimeMillis(), lastBackupRevision = revision
            ))
            try {
                app.dictionaryBackupManager.pruneUnreferencedBackups()
            } catch (_: Exception) {
                // The verified backup is already published; pruning is best-effort maintenance.
            }
            BackupResult.Success(finalName)
        } catch (e: SecurityException) {
            if (published) BackupResult.Partial(BackupFailure.PERMISSION_DENIED)
            else BackupResult.Failure(BackupFailure.PERMISSION_DENIED, e)
        } catch (e: IllegalArgumentException) {
            if (published) BackupResult.Partial(BackupFailure.IO_ERROR)
            else BackupResult.Failure(BackupFailure.CHECKSUM_MISMATCH, e)
        } catch (e: Exception) {
            if (published) BackupResult.Partial(BackupFailure.IO_ERROR)
            else BackupResult.Failure(BackupFailure.IO_ERROR, e)
        } finally {
            partial?.delete()
            verificationDir?.deleteRecursively()
        }
    }

    private fun recordCounts(payload: LibraryBackupPayload) = mapOf(
        "books" to payload.books.size,
        "bookmarks" to payload.bookmarks.size,
        "shelves" to payload.shelves.size,
        "shelfBookCrossRefs" to payload.shelfBookCrossRefs.size,
        "notes" to payload.notes.size,
        "authors" to payload.authors.size,
        "tags" to payload.tags.size,
        "bookAuthorCrossRefs" to payload.bookAuthorCrossRefs.size,
        "bookTagCrossRefs" to payload.bookTagCrossRefs.size,
        "spaces" to payload.spaces.size,
        "bookSpaceCrossRefs" to payload.bookSpaceCrossRefs.size
    )

    private fun uniqueCacheDir(prefix: String) =
        File(context.cacheDir, "${prefix}_${UUID.randomUUID()}").apply {
            deleteRecursively()
            check(mkdirs())
        }

    private fun verifyNewArchive(uri: Uri, target: File): LibraryBackupManifest {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Archive cannot be opened")
        val extracted = BackupArchiveIO.extract(input, target, ::isAllowedLibraryEntry)
        val manifestFile = File(target, "manifest.json")
        require(manifestFile.isFile)
        val manifest = json.decodeFromString<LibraryBackupManifest>(manifestFile.readText())
        require(manifest.format == "pinecone-library")
        require(manifest.formatVersion == FORMAT_VERSION)
        BackupArchiveIO.verify(target, manifest.entries)
        require(extracted == manifest.entries.map { it.path }.toSet() + "manifest.json")
        return manifest
    }

    private fun isAllowedLibraryEntry(path: String): Boolean =
        path in setOf("manifest.json", "library.json", "settings.json", "data.json") ||
            path.startsWith("books/") || path.startsWith("covers/")

    private fun cleanupOldBackups(folder: DocumentFile, indicator: String) {
        folder.listFiles()
            .filter { it.name?.endsWith("_${indicator}.pine") == true }
            .sortedByDescending { it.name }
            .drop(3)
            .forEach { it.delete() }
    }

    private suspend fun restore(uri: Uri): BackupResult {
        val temp = uniqueCacheDir("library_restore")
        val staged = mutableListOf<File>()
        var committed = false
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return BackupResult.Failure(BackupFailure.IO_ERROR)
            val extracted = BackupArchiveIO.extract(input, temp, ::isAllowedLibraryEntry)

            val manifestFile = File(temp, "manifest.json")
            val dataFile: File
            var manifest: LibraryBackupManifest? = null
            if (manifestFile.isFile) {
                manifest = json.decodeFromString<LibraryBackupManifest>(manifestFile.readText())
                if (manifest.format != "pinecone-library") {
                    return BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE)
                }
                if (manifest.formatVersion != FORMAT_VERSION) {
                    return BackupResult.Failure(BackupFailure.UNSUPPORTED_VERSION)
                }
                BackupArchiveIO.verify(temp, manifest.entries)
                require(extracted == manifest.entries.map { it.path }.toSet() + "manifest.json")
                dataFile = File(temp, "library.json")
                if (!dataFile.isFile) return BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE)
            } else {
                dataFile = File(temp, "data.json")
                if (!dataFile.isFile) return BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE)
            }

            val payload = json.decodeFromString<LibraryBackupPayload>(dataFile.readText())
            require(manifest == null || manifest.recordCounts == recordCounts(payload))
            if (!validRelationships(payload)) {
                return BackupResult.Failure(BackupFailure.INVALID_RELATIONSHIP)
            }
            val restoredSettings = File(temp, "settings.json").takeIf { it.isFile }?.let {
                json.decodeFromString<ReaderSettings>(it.readText())
            }
            val pendingSettingsJson = restoredSettings?.let { json.encodeToString(it) }
            val rebasedBooks = stageBookFiles(payload, temp, staged)
                ?: return BackupResult.Failure(BackupFailure.MISSING_BOOK_FILE)
            val database = app.database
            val oldBooks = database.bookDao().getAllBooksSync()

            database.withTransaction {
                database.shelfDao().deleteAllShelfBookCrossRefs()
                database.spaceDao().deleteAllBookSpaceCrossRefs()
                database.bookDao().deleteAllBookAuthorCrossRefs()
                database.bookDao().deleteAllBookTagCrossRefs()
                database.bookmarkDao().deleteAll()
                database.noteDao().deleteAll()
                database.bookDao().deleteAll()
                database.shelfDao().deleteAllShelves()
                database.spaceDao().deleteAllSpaces()
                database.bookDao().deleteAllAuthors()
                database.bookDao().deleteAllTags()

                database.bookDao().insertAll(rebasedBooks)
                database.shelfDao().insertAllShelves(payload.shelves.map { it.toEntity() })
                database.spaceDao().insertAllSpaces(payload.spaces.map { it.toEntity() })
                database.bookDao().insertAllAuthors(payload.authors.map { it.toEntity() })
                database.bookDao().insertAllTags(payload.tags.map { it.toEntity() })
                database.bookmarkDao().insertAll(payload.bookmarks.map { it.toEntity() })
                database.noteDao().insertAll(payload.notes.map { it.toEntity() })
                database.shelfDao().insertAllShelfBookCrossRefs(
                    payload.shelfBookCrossRefs.map { it.toEntity() })
                database.spaceDao().insertAllBookSpaceCrossRefs(
                    payload.bookSpaceCrossRefs.map { it.toEntity() })
                database.bookDao().insertAllBookAuthorCrossRefs(
                    payload.bookAuthorCrossRefs.map { it.toEntity() })
                database.bookDao().insertAllBookTagCrossRefs(
                    payload.bookTagCrossRefs.map { it.toEntity() })
                database.backupStateDao().setPendingSettingsJson(pendingSettingsJson)
            }
            committed = true
            oldBooks.forEach { book ->
                deleteOwnedFile(book.filePath)
                book.coverPath?.let(::deleteOwnedFile)
            }
            applyRestoredSettings(restoredSettings)
            database.backupStateDao().setPendingSettingsJson(null)
            BackupResult.Success()
        } catch (e: SecurityException) {
            if (committed) BackupResult.Partial(BackupFailure.PERMISSION_DENIED)
            else BackupResult.Failure(BackupFailure.PERMISSION_DENIED, e)
        } catch (e: IllegalArgumentException) {
            if (committed) BackupResult.Partial(BackupFailure.IO_ERROR)
            else BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE, e)
        } catch (e: Exception) {
            if (committed) BackupResult.Partial(BackupFailure.IO_ERROR)
            else BackupResult.Failure(BackupFailure.IO_ERROR, e)
        } finally {
            if (!committed) staged.forEach { it.delete() }
            temp.deleteRecursively()
        }
    }

    private fun stageBookFiles(
        payload: LibraryBackupPayload,
        extracted: File,
        staged: MutableList<File>
    ): List<BookEntity>? {
        val token = UUID.randomUUID().toString().replace("-", "")
        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        return payload.books.map { book ->
            val originalName = File(book.filePath).name.takeIf { it.isNotBlank() } ?: return null
            val sourceBook = File(extracted, "books/$originalName")
            if (!sourceBook.isFile) return null
            val extension = originalName.substringAfterLast('.', "epub")
            val targetBook = File(booksDir, "${book.id}_${token}.$extension")
            sourceBook.copyTo(targetBook, overwrite = false)
            staged += targetBook

            val targetCover = book.coverPath?.let { oldCoverPath ->
                val coverName = File(oldCoverPath).name.takeIf { it.isNotBlank() } ?: return null
                val sourceCover = File(extracted, "covers/$coverName")
                if (!sourceCover.isFile) return null
                val coverExtension = coverName.substringAfterLast('.', "png")
                File(coversDir, "${book.id}_${token}.$coverExtension").also { target ->
                    sourceCover.copyTo(target, overwrite = false)
                    staged += target
                }
            }
            book.copy(
                filePath = targetBook.absolutePath,
                coverPath = targetCover?.absolutePath
            ).toEntity()
        }
    }

    private fun validRelationships(payload: LibraryBackupPayload): Boolean {
        val books = payload.books.map { it.id }.toSet()
        val shelves = payload.shelves.map { it.id }.toSet()
        val spaces = payload.spaces.map { it.id }.toSet()
        val authors = payload.authors.map { it.id }.toSet()
        val tags = payload.tags.map { it.id }.toSet()
        if (books.size != payload.books.size || shelves.size != payload.shelves.size ||
            spaces.size != payload.spaces.size || authors.size != payload.authors.size ||
            tags.size != payload.tags.size) return false
        return payload.bookmarks.all { it.bookId in books } &&
            payload.notes.all { it.bookId in books } &&
            payload.shelfBookCrossRefs.all { it.bookId in books && it.shelfId in shelves } &&
            payload.bookSpaceCrossRefs.all { it.bookId in books && it.spaceId in spaces } &&
            payload.bookAuthorCrossRefs.all { it.bookId in books && it.authorId in authors } &&
            payload.bookTagCrossRefs.all { it.bookId in books && it.tagId in tags }
    }

    private suspend fun applyRestoredSettings(restored: ReaderSettings?) {
        if (restored == null) return
        val current = preferences.readerSettings.first()
        preferences.updateAllSettings(restored.copy(
            backupFolderUri = current.backupFolderUri,
            installedDictionaries = current.installedDictionaries,
            activeDictionaryId = current.activeDictionaryId,
            lastBackupRevision = -1L,
            lastBackupTime = current.lastBackupTime
        ))
    }

    private fun deleteOwnedFile(path: String) {
        val file = File(path).canonicalFile
        val root = context.filesDir.canonicalFile
        if (file.path.startsWith(root.path + File.separator)) file.delete()
    }

    private fun compressed(path: String): Boolean {
        val extension = path.substringAfterLast('.', "").lowercase()
        return extension in setOf("epub", "pdf", "zip", "png", "jpg", "jpeg", "webp", "gif")
    }

    companion object {
        private const val LIBRARY_FOLDER = "Library"
        private const val FORMAT_VERSION = 1
        private val operationMutex = Mutex()
    }
}
