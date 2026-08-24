package com.javierreansyah.pinecone.data.repository.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryDatabase
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.model.BookObjectReference
import com.javierreansyah.pinecone.data.model.DictionaryObjectReference
import com.javierreansyah.pinecone.data.model.LibraryBackupPayload
import com.javierreansyah.pinecone.data.model.PortableBackupManifest
import com.javierreansyah.pinecone.data.model.VaultFormat
import com.javierreansyah.pinecone.data.model.VaultObject
import com.javierreansyah.pinecone.data.model.VaultObjectKind
import com.javierreansyah.pinecone.data.model.VaultSnapshot
import com.javierreansyah.pinecone.data.model.toBackupRecord
import com.javierreansyah.pinecone.data.model.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSnapshotInfo(
    val uri: Uri,
    val id: String,
    val timestamp: Long,
    val isManual: Boolean
)

class BackupRepository(private val context: Context) {
    private val preferences = ReaderPreferences(context)
    private val app get() = context.applicationContext as PineconeApplication
    private val resolver get() = context.contentResolver
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    suspend fun createSnapshot(manual: Boolean): BackupResult = withContext(Dispatchers.IO) {
        operationMutex.withLock { createSnapshotLocked(manual) }
    }

    suspend fun listSnapshots(): List<BackupSnapshotInfo> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            runCatching {
                val dirs = openVault(false) ?: return@runCatching emptyList()
                readSnapshots(dirs).map { (file, snapshot) ->
                    BackupSnapshotInfo(
                        file.uri, snapshot.id, snapshot.createdAt,
                        snapshot.backupKind == MANUAL
                    )
                }.sortedByDescending { it.timestamp }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun restoreSnapshot(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                completePendingSettingsRestore()
                val dirs = openVault(false)
                    ?: return@withLock BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
                restoreLocked(readSnapshot(uri), dirs)
            } catch (e: UnsupportedBackupException) {
                BackupResult.Failure(BackupFailure.UNSUPPORTED_VERSION, e)
            } catch (e: Exception) {
                BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE, e)
            }
        }
    }

    suspend fun exportSnapshot(snapshotUri: Uri, destination: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    exportLocked(readSnapshot(snapshotUri), destination)
                } catch (e: UnsupportedBackupException) {
                    BackupResult.Failure(BackupFailure.UNSUPPORTED_VERSION, e)
                } catch (e: Exception) {
                    BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE, e)
                }
            }
        }

    suspend fun importPortable(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        operationMutex.withLock { importLocked(source) }
    }

    suspend fun completePendingSettingsRestore() = withContext(Dispatchers.IO) {
        recoverInterruptedRestore()
        val pending = app.database.backupStateDao().pendingSettingsJson() ?: return@withContext
        applyRestoredSettings(json.decodeFromString(pending))
        app.database.backupStateDao().setPendingSettingsJson(null)
    }

    private data class VaultDirs(
        val vault: DocumentFile,
        val snapshots: DocumentFile,
        val books: DocumentFile,
        val covers: DocumentFile,
        val dictionaries: DocumentFile
    )

    private data class CreatedObject(
        val file: DocumentFile,
        val info: VaultObject,
        val reused: Boolean
    )

    private suspend fun createSnapshotLocked(manual: Boolean): BackupResult {
        val started = System.currentTimeMillis()
        var bytesWritten = 0L
        var bytesReused = 0L
        val warnings = mutableListOf<BackupFailure>()
        val phases = linkedMapOf<String, Long>()
        return try {
            val dirs = openVault(true)
                ?: return BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
            val settings = preferences.readerSettings.first()
            var phaseStarted = System.currentTimeMillis()
            val (revision, payload) = librarySnapshot()
            phases["snapshot"] = System.currentTimeMillis() - phaseStarted
            val portableSettings = portableSettings(settings)
            val fingerprint = stateFingerprint(revision, payload, settings)
            val previous = readSnapshots(dirs).maxByOrNull { it.second.createdAt }?.second
            if (!manual && previous?.stateFingerprint == fingerprint) return BackupResult.Skipped

            val priorBooks = previous?.books?.associateBy { it.bookId }.orEmpty()
            phaseStarted = System.currentTimeMillis()
            val bookRefs = payload.books.map { book ->
                val source = File(book.filePath)
                require(source.isFile) { "Missing book ${book.id}" }
                val old = priorBooks[book.id]
                val bookObject = reuseOrWriteRaw(
                    source, VaultObjectKind.BOOK, dirs.books, old?.book
                ).also {
                    if (it.reused) bytesReused += it.info.storedSize else bytesWritten += it.info.storedSize
                }.info
                val coverObject = book.coverPath?.let { path ->
                    val cover = File(path)
                    require(cover.isFile) { "Missing cover ${book.id}" }
                    reuseOrWriteRaw(cover, VaultObjectKind.COVER, dirs.covers, old?.cover).also {
                        if (it.reused) bytesReused += it.info.storedSize else bytesWritten += it.info.storedSize
                    }.info
                }
                BookObjectReference(book.id, bookObject, coverObject)
            }

            val priorDictionaries = previous?.dictionaries
                ?.associateBy { it.dictionary.id }.orEmpty()
            val dictionaryRefs =
                settings.installedDictionaries.sortedBy { it.id }.map { dictionary ->
                    val source = context.getDatabasePath("dict_${dictionary.id}.db")
                    require(source.isFile) { "Missing dictionary ${dictionary.id}" }
                    val prior = priorDictionaries[dictionary.id]
                    val objectResult = if (prior != null && prior.dictionary == dictionary &&
                        sameSource(source, prior.objectInfo) &&
                        dirs.dictionaries.findFile(prior.objectInfo.fileName)?.isFile == true
                    ) CreatedObject(
                        dirs.dictionaries.findFile(prior.objectInfo.fileName)!!,
                        prior.objectInfo, true
                    )
                    else writeDictionaryObject(dictionary, source, dirs.dictionaries)
                    if (objectResult.reused) bytesReused += objectResult.info.storedSize
                    else bytesWritten += objectResult.info.storedSize
                    DictionaryObjectReference(dictionary, objectResult.info)
                }
            phases["objects"] = System.currentTimeMillis() - phaseStarted

            val finalFingerprint = fingerprintFromReferences(
                revision, payload, portableSettings, bookRefs, dictionaryRefs
            )
            val currentSettings = preferences.readerSettings.first()
            if (app.database.backupStateDao().revision() != revision ||
                stateFingerprint(revision, payload, currentSettings) != finalFingerprint
            ) {
                return BackupResult.Failure(BackupFailure.CONCURRENT_CHANGE)
            }
            val createdAt = System.currentTimeMillis()
            val id = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(createdAt))
            val snapshot = VaultSnapshot(
                id = id,
                createdAt = createdAt,
                backupKind = if (manual) MANUAL else AUTOMATIC,
                appVersion = context.packageManager.getPackageInfo(context.packageName, 0)
                    .versionName.orEmpty(),
                databaseRevision = revision,
                stateFingerprint = finalFingerprint,
                recordCounts = recordCounts(payload),
                library = payload,
                settings = portableSettings,
                books = bookRefs,
                dictionaries = dictionaryRefs,
                activeDictionaryId = settings.activeDictionaryId
            )
            validateSnapshot(snapshot)
            phaseStarted = System.currentTimeMillis()
            val snapshotFile = writeSnapshot(dirs.snapshots, snapshot)
            bytesWritten += snapshotFile.length()
            phases["publish"] = System.currentTimeMillis() - phaseStarted
            phaseStarted = System.currentTimeMillis()
            if (!enforceRetention(dirs)) warnings += BackupFailure.IO_ERROR
            if (!pruneObjects(dirs)) warnings += BackupFailure.IO_ERROR
            phases["maintenance"] = System.currentTimeMillis() - phaseStarted
            preferences.updateAllSettings(
                settings.copy(
                    lastBackupTime = createdAt, lastBackupRevision = revision
                )
            )
            val duration = System.currentTimeMillis() - started
            BackupResult.Success(id, bytesWritten, bytesReused, duration, phases, warnings)
        } catch (e: SecurityException) {
            BackupResult.Failure(BackupFailure.PERMISSION_DENIED, e)
        } catch (e: InvalidDictionaryException) {
            BackupResult.Failure(BackupFailure.INVALID_DATABASE, e)
        } catch (e: IllegalArgumentException) {
            val reason = when {
                e.message?.contains("dictionary", ignoreCase = true) == true ->
                    BackupFailure.MISSING_DICTIONARY

                e.message?.startsWith("Missing") == true -> BackupFailure.MISSING_BOOK_FILE
                else -> BackupFailure.CHECKSUM_MISMATCH
            }
            BackupResult.Failure(reason, e)
        } catch (e: Exception) {
            BackupResult.Failure(BackupFailure.IO_ERROR, e)
        }
    }

    private suspend fun librarySnapshot(): Pair<Long, LibraryBackupPayload> =
        app.database.withTransaction {
            val db = app.database
            db.backupStateDao().revision() to LibraryBackupPayload(
                books = db.bookDao().getAllBooksSync().map { it.toBackupRecord() },
                bookmarks = db.bookmarkDao().getAllBookmarksSync().map { it.toBackupRecord() },
                shelves = db.shelfDao().getAllShelvesSync().map { it.toBackupRecord() },
                shelfBookCrossRefs = db.shelfDao().getAllShelfBookCrossRefsSync()
                    .map { it.toBackupRecord() },
                notes = db.noteDao().getAllNotesSync().map { it.toBackupRecord() },
                authors = db.bookDao().getAllAuthorsSync().map { it.toBackupRecord() },
                tags = db.bookDao().getAllTagsSync().map { it.toBackupRecord() },
                bookAuthorCrossRefs = db.bookDao().getAllBookAuthorCrossRefsSync()
                    .map { it.toBackupRecord() },
                bookTagCrossRefs = db.bookDao().getAllBookTagCrossRefsSync()
                    .map { it.toBackupRecord() },
                spaces = db.spaceDao().getAllSpacesSync().map { it.toBackupRecord() },
                bookSpaceCrossRefs = db.spaceDao().getAllBookSpaceCrossRefsSync()
                    .map { it.toBackupRecord() }
            )
        }

    private suspend fun openVault(create: Boolean): VaultDirs? {
        val settings = preferences.readerSettings.first()
        val root = settings.backupFolderUri.takeIf { it.isNotBlank() }?.toUri()
            ?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        if (!root.canRead() || create && !root.canWrite()) return null
        val vault = childDir(root, VAULT_DIR, create) ?: return null
        val snapshots = childDir(vault, "snapshots", create) ?: return null
        val objects = childDir(vault, "objects", create) ?: return null
        val books = childDir(objects, "books", create) ?: return null
        val covers = childDir(objects, "covers", create) ?: return null
        val dictionaries = childDir(objects, "dictionaries", create) ?: return null
        if (create) ensureFormat(vault)
        else validateFormat(vault)
        return VaultDirs(vault, snapshots, books, covers, dictionaries)
    }

    private fun childDir(parent: DocumentFile, name: String, create: Boolean): DocumentFile? =
        parent.findFile(name)?.takeIf { it.isDirectory }
            ?: if (create) parent.createDirectory(name) else null

    private fun ensureFormat(vault: DocumentFile) {
        val existing = vault.findFile("format.json")
        if (existing != null) {
            validateFormat(vault)
            return
        }
        val bytes = json.encodeToString(VaultFormat()).toByteArray()
        val partial = vault.createFile("application/json", "format.json.partial")
            ?: error("Cannot create vault format")
        try {
            resolver.openOutputStream(partial.uri, "wt")?.use { it.write(bytes) }
                ?: error("Cannot write vault format")
            val decoded = resolver.openInputStream(partial.uri)?.use {
                json.decodeFromString<VaultFormat>(it.readBytes().toString(Charsets.UTF_8))
            } ?: error("Cannot verify vault format")
            require(decoded == VaultFormat())
            check(partial.renameTo("format.json"))
        } finally {
            vault.findFile("format.json.partial")?.delete()
        }
    }

    private fun validateFormat(vault: DocumentFile) {
        val file = vault.findFile("format.json") ?: error("Missing vault format")
        val format = resolver.openInputStream(file.uri)?.use {
            json.decodeFromString<VaultFormat>(it.readBytes().toString(Charsets.UTF_8))
        } ?: error("Cannot read vault format")
        require(format.format == VAULT_FORMAT)
        if (format.formatVersion != FORMAT_VERSION) throw UnsupportedBackupException()
    }

    private fun reuseOrWriteRaw(
        source: File,
        kind: VaultObjectKind,
        folder: DocumentFile,
        previous: VaultObject?
    ): CreatedObject {
        if (previous != null && previous.kind == kind && sameSource(source, previous)) {
            folder.findFile(previous.fileName)?.takeIf { it.isFile }?.let {
                return CreatedObject(it, previous, true)
            }
        }
        val partialName = ".${UUID.randomUUID()}.partial"
        val partial = folder.createFile("application/octet-stream", partialName)
            ?: error("Cannot create object")
        try {
            val digest = FileInputStream(source).use { input ->
                resolver.openOutputStream(partial.uri, "wt")?.use { output ->
                    output.buffered().use { buffered ->
                        BackupArchiveIO.copyAndDigest(input, buffered)
                    }
                } ?: error("Cannot write object")
            }
            require(digest.size == source.length())
            val extension = source.extension.lowercase().replace(Regex("[^a-z0-9]"), "")
                .ifBlank { "bin" }
            val name = "${digest.sha256}.$extension"
            val existing = folder.findFile(name)
            val existingIsValid = existing?.let { candidate ->
                runCatching {
                    resolver.openInputStream(candidate.uri)?.use { input ->
                        BackupArchiveIO.copyAndDigest(input) == digest
                    } == true
                }.getOrDefault(false)
            } == true
            val final = if (existingIsValid) {
                partial.delete(); existing
            } else {
                existing?.delete()
                check(partial.renameTo(name)); folder.findFile(name) ?: error("Missing object")
            }
            val verified = resolver.openInputStream(final.uri)?.use(BackupArchiveIO::copyAndDigest)
                ?: error("Cannot verify object")
            require(verified == digest)
            return CreatedObject(
                final, VaultObject(
                    kind, digest.sha256, digest.size, digest.crc32,
                    final.length(), digest.crc32, name, source.absolutePath,
                    source.length(), source.lastModified()
                ), existingIsValid
            )
        } finally {
            folder.findFile(partialName)?.delete()
        }
    }

    private fun writeDictionaryObject(
        dictionary: InstalledDictionary,
        live: File,
        folder: DocumentFile
    ): CreatedObject {
        DictionaryDatabase.closeDatabase(dictionary.id)
        SQLiteDatabase.openDatabase(live.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { require(it.moveToFirst()) }
        }
        val sourceSize = live.length()
        val sourceModified = live.lastModified()
        val temp = File.createTempFile("dictionary", ".db", context.cacheDir)
        val partialName = ".${UUID.randomUUID()}.partial"
        val partial = folder.createFile("application/octet-stream", partialName)
            ?: error("Cannot create dictionary object")
        try {
            FileInputStream(live).use { input -> FileOutputStream(temp).use { input.copyTo(it) } }
            validateDictionary(temp)
            val storageCrc = CRC32()
            lateinit var raw: StreamDigest
            resolver.openOutputStream(partial.uri, "wt")?.use { base ->
                CheckedOutputStream(base.buffered(), storageCrc).use { checked ->
                    ZipOutputStream(checked).use { zip ->
                        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                        zip.putNextEntry(ZipEntry("dictionary.db"))
                        FileInputStream(temp).use { raw = BackupArchiveIO.copyAndDigest(it, zip) }
                        zip.closeEntry()
                    }
                }
            } ?: error("Cannot write dictionary object")
            val name = "${raw.sha256}.pinedict"
            val existing = folder.findFile(name)
            val final = if (existing != null) {
                partial.delete(); existing
            } else {
                check(partial.renameTo(name)); folder.findFile(name) ?: error("Missing dictionary")
            }
            val info = VaultObject(
                VaultObjectKind.DICTIONARY, raw.sha256, raw.size, raw.crc32,
                final.length(), if (existing == null) storageCrc.value else crcOf(final), name,
                live.absolutePath, sourceSize, sourceModified
            )
            verifyDictionaryObject(final, info, null)
            return CreatedObject(final, info, existing != null)
        } finally {
            temp.delete()
            folder.findFile(partialName)?.delete()
        }
    }

    private fun sameSource(file: File, info: VaultObject) =
        file.absolutePath == info.sourcePath && file.length() == info.sourceSize &&
                file.lastModified() == info.sourceLastModified

    private fun writeSnapshot(folder: DocumentFile, snapshot: VaultSnapshot): DocumentFile {
        val finalName =
            "${snapshot.id}_${if (snapshot.backupKind == MANUAL) "M" else "A"}.pinesnapshot"
        val partialName = "$finalName.partial"
        val partial = folder.createFile("application/octet-stream", partialName)
            ?: error("Cannot create snapshot")
        try {
            val bytes = json.encodeToString(snapshot).toByteArray()
            resolver.openOutputStream(partial.uri, "wt")?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                    zip.putNextEntry(ZipEntry("snapshot.json"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            } ?: error("Cannot write snapshot")
            require(readSnapshot(partial.uri) == snapshot)
            check(partial.renameTo(finalName))
            return folder.findFile(finalName) ?: error("Missing snapshot")
        } finally {
            folder.findFile(partialName)?.delete()
        }
    }

    private fun readSnapshot(uri: Uri): VaultSnapshot {
        val input = resolver.openInputStream(uri) ?: error("Cannot read snapshot")
        ZipInputStream(input.buffered()).use { zip ->
            val entry = zip.nextEntry ?: error("Empty snapshot")
            require(entry.name == "snapshot.json" && !entry.isDirectory)
            val bytes = zip.readLimitedMetadata()
            require(zip.nextEntry == null)
            return json.decodeFromString<VaultSnapshot>(bytes.toString(Charsets.UTF_8)).also {
                validateSnapshot(it)
            }
        }
    }

    private fun readSnapshots(dirs: VaultDirs): List<Pair<DocumentFile, VaultSnapshot>> =
        dirs.snapshots.listFiles().filter { it.name?.endsWith(".pinesnapshot") == true }
            .mapNotNull { file -> runCatching { file to readSnapshot(file.uri) }.getOrNull() }

    private fun validateSnapshot(snapshot: VaultSnapshot) {
        require(snapshot.format == SNAPSHOT_FORMAT)
        if (snapshot.formatVersion != FORMAT_VERSION) throw UnsupportedBackupException()
        require(snapshot.id.matches(Regex("[0-9]{8}_[0-9]{6}_[0-9]{3}")))
        require(snapshot.backupKind == MANUAL || snapshot.backupKind == AUTOMATIC)
        require(snapshot.recordCounts == recordCounts(snapshot.library))
        require(validRelationships(snapshot.library))
        require(snapshot.books.map { it.bookId }.toSet() == snapshot.library.books.map { it.id }
            .toSet())
        val all = snapshot.books.flatMap { listOfNotNull(it.book, it.cover) } +
                snapshot.dictionaries.map { it.objectInfo }
        require(all.all {
            it.sha256.matches(SHA_PATTERN) && it.size >= 0 &&
                    it.storedSize >= 0 && it.fileName.matches(FILE_PATTERN)
        })
        require(snapshot.dictionaries.map { it.dictionary.id }
            .toSet().size == snapshot.dictionaries.size)
        require(snapshot.books.all {
            it.book.kind == VaultObjectKind.BOOK &&
                    (it.cover == null || it.cover.kind == VaultObjectKind.COVER)
        })
        require(snapshot.dictionaries.all { it.objectInfo.kind == VaultObjectKind.DICTIONARY })
        require(snapshot.settings.installedDictionaries.sortedBy { it.id } ==
                snapshot.dictionaries.map { it.dictionary }.sortedBy { it.id })
        require(
            snapshot.activeDictionaryId.isBlank() ||
                    snapshot.dictionaries.any { it.dictionary.id == snapshot.activeDictionaryId })
    }

    private fun enforceRetention(dirs: VaultDirs): Boolean = runCatching {
        readSnapshots(dirs).groupBy { it.second.backupKind }.values.forEach { group ->
            group.sortedByDescending { it.second.createdAt }.drop(RETENTION).forEach {
                check(it.first.delete())
            }
        }
    }.isSuccess

    private fun pruneObjects(dirs: VaultDirs): Boolean = runCatching {
        val retained = readSnapshots(dirs).map { it.second }
        val keepBooks = retained.flatMap { it.books }.map { it.book.fileName }.toSet()
        val keepCovers = retained.flatMap { it.books }.mapNotNull { it.cover?.fileName }.toSet()
        val keepDictionaries = retained.flatMap { it.dictionaries }
            .map { it.objectInfo.fileName }.toSet()
        pruneFolder(dirs.books, keepBooks)
        pruneFolder(dirs.covers, keepCovers)
        pruneFolder(dirs.dictionaries, keepDictionaries)
    }.isSuccess

    private fun pruneFolder(folder: DocumentFile, keep: Set<String>) {
        folder.listFiles().forEach { file ->
            val name = file.name.orEmpty()
            if (name.endsWith(".partial") || name !in keep) check(file.delete())
        }
    }

    private suspend fun restoreLocked(snapshot: VaultSnapshot, dirs: VaultDirs): BackupResult {
        val started = System.currentTimeMillis()
        val stagedBooks = mutableListOf<File>()
        val dictionaryStages = mutableListOf<DictionaryStage>()
        var committed = false
        return try {
            validateSnapshot(snapshot)
            val refs = snapshot.books.associateBy { it.bookId }
            val token = UUID.randomUUID().toString().replace("-", "")
            val booksDir = File(context.filesDir, "books").apply { mkdirs() }
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val rebased = snapshot.library.books.map { book ->
                val ref = refs[book.id] ?: error("Missing book object")
                val bookTarget =
                    File(booksDir, "${book.id}_$token.${ref.book.fileName.substringAfterLast('.')}")
                copyRawObject(dirs.books, ref.book, bookTarget)
                stagedBooks += bookTarget
                val coverTarget = ref.cover?.let { cover ->
                    File(
                        coversDir,
                        "${book.id}_$token.${cover.fileName.substringAfterLast('.')}"
                    ).also {
                        copyRawObject(dirs.covers, cover, it); stagedBooks += it
                    }
                }
                book.copy(filePath = bookTarget.absolutePath, coverPath = coverTarget?.absolutePath)
                    .toEntity()
            }
            snapshot.dictionaries.forEach { reference ->
                val objectFile = dirs.dictionaries.findFile(reference.objectInfo.fileName)
                    ?: error("Missing dictionary object")
                val staged = File(context.cacheDir, "dict_${reference.dictionary.id}_$token.db")
                verifyDictionaryObject(objectFile, reference.objectInfo, staged)
                validateDictionary(staged)
                dictionaryStages += DictionaryStage(reference.dictionary, staged)
            }
            val oldBooks = app.database.bookDao().getAllBooksSync()
            val dictionaryRollback = swapDictionaries(
                dictionaryStages,
                snapshot.settings.installedDictionaries.map { it.id }.toSet()
            )
            try {
                val pending = json.encodeToString(snapshot.settings)
                app.database.withTransaction {
                    replaceLibrary(rebased, snapshot.library)
                    app.database.backupStateDao().setPendingSettingsJson(pending)
                }
                committed = true
                applyRestoredSettings(
                    snapshot.settings.copy(
                        installedDictionaries = snapshot.dictionaries.map { it.dictionary },
                        activeDictionaryId = snapshot.activeDictionaryId
                    )
                )
                dictionaryRollback.commit()
                app.database.backupStateDao().setPendingSettingsJson(null)
                oldBooks.forEach { old ->
                    deleteOwnedFile(old.filePath); old.coverPath?.let(::deleteOwnedFile)
                }
                BackupResult.Success(
                    snapshot.id,
                    durationMillis = System.currentTimeMillis() - started
                )
            } catch (error: Throwable) {
                if (!committed) dictionaryRollback.rollback()
                throw error
            }
        } catch (e: SecurityException) {
            BackupResult.Failure(BackupFailure.PERMISSION_DENIED, e)
        } catch (e: InvalidDictionaryException) {
            BackupResult.Failure(BackupFailure.INVALID_DATABASE, e)
        } catch (e: IllegalArgumentException) {
            BackupResult.Failure(BackupFailure.CHECKSUM_MISMATCH, e)
        } catch (e: Exception) {
            if (committed) BackupResult.Partial(BackupFailure.IO_ERROR)
            else BackupResult.Failure(BackupFailure.IO_ERROR, e)
        } finally {
            if (!committed) stagedBooks.forEach { it.delete() }
            dictionaryStages.forEach { it.file.delete() }
        }
    }

    private data class DictionaryStage(val dictionary: InstalledDictionary, val file: File)

    @kotlinx.serialization.Serializable
    private data class RestoreJournal(
        val entries: List<RestoreJournalEntry>
    )

    @kotlinx.serialization.Serializable
    private data class RestoreJournalEntry(
        val targetPath: String,
        val rollbackPath: String,
        val stagedPath: String?,
        val hadOriginal: Boolean
    )

    private inner class DictionaryRollback(private val journal: RestoreJournal) {
        fun commit() {
            finishJournal(journal, commit = true)
            restoreJournalFile().delete()
        }

        fun rollback() {
            finishJournal(journal, commit = false)
            restoreJournalFile().delete()
        }
    }

    private suspend fun swapDictionaries(
        stages: List<DictionaryStage>,
        wantedIds: Set<String>
    ): DictionaryRollback {
        val currentIds =
            preferences.readerSettings.first().installedDictionaries.map { it.id }.toSet()
        val allIds = currentIds + wantedIds
        allIds.forEach(DictionaryDatabase::closeDatabase)
        val entries = allIds.map { id ->
            val target = context.getDatabasePath("dict_$id.db")
            val stage = stages.firstOrNull { it.dictionary.id == id }?.file
            RestoreJournalEntry(
                targetPath = target.absolutePath,
                rollbackPath = File(
                    target.parentFile,
                    "${target.name}.rollback_${UUID.randomUUID()}"
                ).absolutePath,
                stagedPath = stage?.absolutePath,
                hadOriginal = target.exists()
            )
        }
        val journal = RestoreJournal(entries = entries)
        writeRestoreJournal(journal)
        try {
            for (entry in entries) {
                val target = File(entry.targetPath)
                if (entry.hadOriginal) check(target.renameTo(File(entry.rollbackPath)))
                context.getDatabasePath("${target.name}-wal").delete()
                context.getDatabasePath("${target.name}-shm").delete()
                entry.stagedPath?.let { stagedPath ->
                    target.parentFile?.mkdirs(); check(File(stagedPath).renameTo(target))
                }
            }
            return DictionaryRollback(journal)
        } catch (error: Throwable) {
            DictionaryRollback(journal).rollback(); throw error
        }
    }

    private suspend fun recoverInterruptedRestore() {
        val file = restoreJournalFile()
        if (!file.isFile) return
        val journal = runCatching { json.decodeFromString<RestoreJournal>(file.readText()) }
            .getOrElse { file.delete(); return }
        val databaseCommitted = app.database.backupStateDao().pendingSettingsJson() != null
        finishJournal(journal, databaseCommitted)
        file.delete()
    }

    private fun finishJournal(journal: RestoreJournal, commit: Boolean) {
        journal.entries.asReversed().forEach { entry ->
            val target = File(entry.targetPath)
            val rollback = File(entry.rollbackPath)
            val staged = entry.stagedPath?.let(::File)
            if (commit) {
                rollback.delete()
            } else if (rollback.exists()) {
                target.delete(); check(rollback.renameTo(target))
            } else if (!entry.hadOriginal) {
                target.delete()
            }
            staged?.delete()
        }
    }

    private fun writeRestoreJournal(journal: RestoreJournal) {
        val target = restoreJournalFile()
        val partial = File(target.parentFile, "${target.name}.partial")
        partial.writeText(json.encodeToString(journal))
        if (target.exists()) check(target.delete())
        check(partial.renameTo(target))
    }

    private fun restoreJournalFile() = File(context.filesDir, "backup_restore_journal.json")

    private suspend fun replaceLibrary(books: List<BookEntity>, payload: LibraryBackupPayload) {
        val db = app.database
        db.shelfDao().deleteAllShelfBookCrossRefs()
        db.spaceDao().deleteAllBookSpaceCrossRefs()
        db.bookDao().deleteAllBookAuthorCrossRefs()
        db.bookDao().deleteAllBookTagCrossRefs()
        db.bookmarkDao().deleteAll(); db.noteDao().deleteAll(); db.bookDao().deleteAll()
        db.shelfDao().deleteAllShelves(); db.spaceDao().deleteAllSpaces()
        db.bookDao().deleteAllAuthors(); db.bookDao().deleteAllTags()
        db.bookDao().insertAll(books)
        db.shelfDao().insertAllShelves(payload.shelves.map { it.toEntity() })
        db.spaceDao().insertAllSpaces(payload.spaces.map { it.toEntity() })
        db.bookDao().insertAllAuthors(payload.authors.map { it.toEntity() })
        db.bookDao().insertAllTags(payload.tags.map { it.toEntity() })
        db.bookmarkDao().insertAll(payload.bookmarks.map { it.toEntity() })
        db.noteDao().insertAll(payload.notes.map { it.toEntity() })
        db.shelfDao().insertAllShelfBookCrossRefs(payload.shelfBookCrossRefs.map { it.toEntity() })
        db.spaceDao().insertAllBookSpaceCrossRefs(payload.bookSpaceCrossRefs.map { it.toEntity() })
        db.bookDao().insertAllBookAuthorCrossRefs(payload.bookAuthorCrossRefs.map { it.toEntity() })
        db.bookDao().insertAllBookTagCrossRefs(payload.bookTagCrossRefs.map { it.toEntity() })
    }

    private fun copyRawObject(folder: DocumentFile, info: VaultObject, target: File) {
        val source = folder.findFile(info.fileName) ?: error("Missing object")
        val digest = resolver.openInputStream(source.uri)?.use { input ->
            FileOutputStream(target).use { BackupArchiveIO.copyAndDigest(input, it) }
        } ?: error("Cannot read object")
        require(digest.size == info.size && digest.sha256 == info.sha256 && digest.crc32 == info.crc32)
    }

    private fun verifyDictionaryObject(file: DocumentFile, info: VaultObject, output: File?) {
        val input = resolver.openInputStream(file.uri) ?: error("Cannot read dictionary")
        ZipInputStream(input.buffered()).use { zip ->
            val entry = zip.nextEntry ?: error("Empty dictionary")
            require(entry.name == "dictionary.db" && !entry.isDirectory)
            val digest = if (output == null) BackupArchiveIO.copyAndDigest(zip)
            else FileOutputStream(output).use { BackupArchiveIO.copyAndDigest(zip, it) }
            require(digest.size == info.size && digest.sha256 == info.sha256 && digest.crc32 == info.crc32)
            require(zip.nextEntry == null)
        }
    }

    private fun validateDictionary(file: File) {
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use {
                    require(it.moveToFirst() && it.getString(0).equals("ok", true))
                }
                val required = mutableSetOf("dictionary_entries", "synonym_entries")
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use {
                    while (it.moveToNext()) required.remove(it.getString(0))
                }
                require(required.isEmpty())
            }
        } catch (error: Throwable) {
            throw InvalidDictionaryException(error)
        }
    }

    private suspend fun exportLocked(snapshot: VaultSnapshot, destination: Uri): BackupResult {
        val started = System.currentTimeMillis()
        return try {
            val dirs = openVault(false)
                ?: return BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
            validateAllObjects(snapshot, dirs)
            var written = 0L
            resolver.openOutputStream(destination, "wt")?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                    val manifest = json.encodeToString(PortableBackupManifest(snapshot = snapshot))
                        .toByteArray()
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
                    allObjects(snapshot).forEach { info ->
                        val folder = objectFolder(dirs, info.kind)
                        val source = folder.findFile(info.fileName) ?: error("Missing object")
                        val path = "objects/${kindFolder(info.kind)}/${info.fileName}"
                        val entry = ZipEntry(path).apply {
                            method = ZipEntry.STORED
                            size = info.storedSize; compressedSize = info.storedSize
                            crc = info.storedCrc32
                        }
                        zip.putNextEntry(entry)
                        resolver.openInputStream(source.uri)?.use { input ->
                            written += input.copyTo(zip)
                        } ?: error("Cannot export object")
                        zip.closeEntry()
                    }
                }
            } ?: error("Cannot open export destination")
            verifyPortable(destination)
            BackupResult.Success(
                snapshot.id,
                written,
                durationMillis = System.currentTimeMillis() - started
            )
        } catch (e: SecurityException) {
            BackupResult.Failure(BackupFailure.PERMISSION_DENIED, e)
        } catch (e: Exception) {
            BackupResult.Failure(BackupFailure.IO_ERROR, e)
        }
    }

    private suspend fun importLocked(source: Uri): BackupResult {
        val started = System.currentTimeMillis()
        val created = mutableListOf<DocumentFile>()
        return try {
            val dirs = openVault(true)
                ?: return BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
            val input = resolver.openInputStream(source) ?: error("Cannot read import")
            lateinit var snapshot: VaultSnapshot
            var written = 0L
            ZipInputStream(input.buffered()).use { zip ->
                val manifestEntry = zip.nextEntry ?: error("Empty archive")
                require(manifestEntry.name == "manifest.json")
                val portable = json.decodeFromString<PortableBackupManifest>(
                    zip.readLimitedMetadata().toString(Charsets.UTF_8)
                )
                require(portable.format == PORTABLE_FORMAT)
                if (portable.formatVersion != FORMAT_VERSION) throw UnsupportedBackupException()
                snapshot = portable.snapshot.also(::validateSnapshot)
                val expected = allObjects(snapshot).associateBy {
                    "objects/${kindFolder(it.kind)}/${it.fileName}"
                }.toMutableMap()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(BackupArchiveIO.safeArchivePath(entry.name) && !entry.isDirectory)
                    val info = expected.remove(entry.name) ?: error("Unexpected archive entry")
                    val folder = objectFolder(dirs, info.kind)
                    if (folder.findFile(info.fileName) != null) {
                        BackupArchiveIO.copyAndDigest(zip)
                    } else {
                        val partialName = ".${UUID.randomUUID()}.partial"
                        val partial = folder.createFile("application/octet-stream", partialName)
                            ?: error("Cannot create imported object")
                        val stored = resolver.openOutputStream(partial.uri, "wt")?.use {
                            BackupArchiveIO.copyAndDigest(zip, it)
                        } ?: error("Cannot write imported object")
                        require(stored.size == info.storedSize && stored.crc32 == info.storedCrc32)
                        check(partial.renameTo(info.fileName))
                        val final =
                            folder.findFile(info.fileName) ?: error("Missing imported object")
                        created += final; written += final.length()
                    }
                    zip.closeEntry()
                }
                require(expected.isEmpty())
            }
            validateAllObjects(snapshot, dirs)
            val imported = snapshot.copy(backupKind = MANUAL)
            if (dirs.snapshots.listFiles().none {
                    runCatching { readSnapshot(it.uri).id == imported.id }.getOrDefault(false)
                }) {
                writeSnapshot(dirs.snapshots, imported)
            }
            enforceRetention(dirs); pruneObjects(dirs)
            BackupResult.Success(
                imported.id, written,
                durationMillis = System.currentTimeMillis() - started
            )
        } catch (e: UnsupportedBackupException) {
            created.forEach { it.delete() }
            BackupResult.Failure(BackupFailure.UNSUPPORTED_VERSION, e)
        } catch (e: IllegalArgumentException) {
            created.forEach { it.delete() }
            BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE, e)
        } catch (e: Exception) {
            created.forEach { it.delete() }
            BackupResult.Failure(BackupFailure.IO_ERROR, e)
        }
    }

    private fun verifyPortable(uri: Uri) {
        val input = resolver.openInputStream(uri) ?: error("Cannot verify export")
        ZipInputStream(input.buffered()).use { zip ->
            val first = zip.nextEntry ?: error("Empty export")
            require(first.name == "manifest.json")
            val portable = json.decodeFromString<PortableBackupManifest>(
                zip.readLimitedMetadata().toString(Charsets.UTF_8)
            )
            require(portable.format == PORTABLE_FORMAT)
            if (portable.formatVersion != FORMAT_VERSION) throw UnsupportedBackupException()
            val expected = allObjects(portable.snapshot).associateBy {
                "objects/${kindFolder(it.kind)}/${it.fileName}"
            }.toMutableMap()
            while (true) {
                val entry = zip.nextEntry ?: break
                val info = expected.remove(entry.name) ?: error("Unexpected export entry")
                val stored = BackupArchiveIO.copyAndDigest(zip)
                require(stored.size == info.storedSize && stored.crc32 == info.storedCrc32)
            }
            require(expected.isEmpty())
        }
    }

    private fun validateAllObjects(snapshot: VaultSnapshot, dirs: VaultDirs) {
        snapshot.books.forEach { ref ->
            verifyRaw(dirs.books, ref.book); ref.cover?.let { verifyRaw(dirs.covers, it) }
        }
        snapshot.dictionaries.forEach { ref ->
            val file =
                dirs.dictionaries.findFile(ref.objectInfo.fileName) ?: error("Missing dictionary")
            require(file.length() == ref.objectInfo.storedSize)
            verifyDictionaryObject(file, ref.objectInfo, null)
        }
    }

    private fun verifyRaw(folder: DocumentFile, info: VaultObject) {
        val file = folder.findFile(info.fileName) ?: error("Missing object")
        val digest = resolver.openInputStream(file.uri)?.use(BackupArchiveIO::copyAndDigest)
            ?: error("Cannot verify object")
        require(digest.size == info.size && digest.sha256 == info.sha256 && digest.crc32 == info.crc32)
    }

    private fun allObjects(snapshot: VaultSnapshot): List<VaultObject> =
        (snapshot.books.flatMap { listOfNotNull(it.book, it.cover) } +
                snapshot.dictionaries.map { it.objectInfo }).distinctBy { it.kind to it.fileName }

    private fun objectFolder(dirs: VaultDirs, kind: VaultObjectKind) = when (kind) {
        VaultObjectKind.BOOK -> dirs.books
        VaultObjectKind.COVER -> dirs.covers
        VaultObjectKind.DICTIONARY -> dirs.dictionaries
    }

    private fun kindFolder(kind: VaultObjectKind) = when (kind) {
        VaultObjectKind.BOOK -> "books"
        VaultObjectKind.COVER -> "covers"
        VaultObjectKind.DICTIONARY -> "dictionaries"
    }

    private fun crcOf(file: DocumentFile): Long = resolver.openInputStream(file.uri)?.use {
        val crc = CRC32()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
        while (true) {
            val count = it.read(buffer); if (count < 0) break; crc.update(buffer, 0, count)
        }
        crc.value
    } ?: error("Cannot read object")

    private fun portableSettings(settings: ReaderSettings) = settings.copy(
        backupFolderUri = "", lastBackupTime = 0, lastBackupRevision = -1,
        installedDictionaries = settings.installedDictionaries.sortedBy { it.id }
    )

    private fun stateFingerprint(
        revision: Long,
        payload: LibraryBackupPayload,
        settings: ReaderSettings
    ): String {
        val books = payload.books.sortedBy { it.id }.joinToString("|") { book ->
            listOfNotNull(book.filePath, book.coverPath).joinToString(",") { path ->
                val file = File(path); "$path:${file.length()}:${file.lastModified()}"
            }
        }
        val dictionaries =
            settings.installedDictionaries.sortedBy { it.id }.joinToString("|") { dictionary ->
                val file = context.getDatabasePath("dict_${dictionary.id}.db")
                "${dictionary.id}:${dictionary.name}:${dictionary.wordCount}:${file.length()}:${file.lastModified()}"
            }
        val settingsHash = BackupArchiveIO.sha256(
            json.encodeToString(portableSettings(settings)).toByteArray()
        )
        return BackupArchiveIO.sha256("$revision:$settingsHash:$books:$dictionaries".toByteArray())
    }

    private fun fingerprintFromReferences(
        revision: Long,
        payload: LibraryBackupPayload,
        settings: ReaderSettings,
        books: List<BookObjectReference>,
        dictionaries: List<DictionaryObjectReference>
    ): String {
        val byId = books.associateBy { it.bookId }
        val bookState = payload.books.sortedBy { it.id }.joinToString("|") { book ->
            val ref = byId.getValue(book.id)
            listOfNotNull(ref.book, ref.cover).joinToString(",") {
                "${it.sourcePath}:${it.sourceSize}:${it.sourceLastModified}"
            }
        }
        val dictionaryState = dictionaries.sortedBy { it.dictionary.id }.joinToString("|") {
            val source = it.objectInfo
            "${it.dictionary.id}:${it.dictionary.name}:${it.dictionary.wordCount}:" +
                    "${source.sourceSize}:${source.sourceLastModified}"
        }
        val settingsHash = BackupArchiveIO.sha256(json.encodeToString(settings).toByteArray())
        return BackupArchiveIO.sha256(
            "$revision:$settingsHash:$bookState:$dictionaryState".toByteArray()
        )
    }

    private suspend fun applyRestoredSettings(restored: ReaderSettings) {
        val current = preferences.readerSettings.first()
        preferences.updateAllSettings(
            restored.copy(
                backupFolderUri = current.backupFolderUri,
                lastBackupRevision = -1,
                lastBackupTime = current.lastBackupTime
            )
        )
    }

    private fun recordCounts(payload: LibraryBackupPayload) = mapOf(
        "books" to payload.books.size, "bookmarks" to payload.bookmarks.size,
        "shelves" to payload.shelves.size, "shelfBookCrossRefs" to payload.shelfBookCrossRefs.size,
        "notes" to payload.notes.size, "authors" to payload.authors.size,
        "tags" to payload.tags.size, "bookAuthorCrossRefs" to payload.bookAuthorCrossRefs.size,
        "bookTagCrossRefs" to payload.bookTagCrossRefs.size, "spaces" to payload.spaces.size,
        "bookSpaceCrossRefs" to payload.bookSpaceCrossRefs.size
    )

    private fun validRelationships(payload: LibraryBackupPayload): Boolean {
        val books = payload.books.map { it.id }.toSet()
        val shelves = payload.shelves.map { it.id }.toSet()
        val spaces = payload.spaces.map { it.id }.toSet()
        val authors = payload.authors.map { it.id }.toSet()
        val tags = payload.tags.map { it.id }.toSet()
        if (books.size != payload.books.size || shelves.size != payload.shelves.size ||
            spaces.size != payload.spaces.size || authors.size != payload.authors.size ||
            tags.size != payload.tags.size
        ) return false
        return payload.bookmarks.all { it.bookId in books } && payload.notes.all { it.bookId in books } &&
                payload.shelfBookCrossRefs.all { it.bookId in books && it.shelfId in shelves } &&
                payload.bookSpaceCrossRefs.all { it.bookId in books && it.spaceId in spaces } &&
                payload.bookAuthorCrossRefs.all { it.bookId in books && it.authorId in authors } &&
                payload.bookTagCrossRefs.all { it.bookId in books && it.tagId in tags }
    }

    private fun deleteOwnedFile(path: String) {
        val file = File(path).canonicalFile
        val root = context.filesDir.canonicalFile
        if (file.path.startsWith(root.path + File.separator)) file.delete()
    }

    private fun ZipInputStream.readLimitedMetadata(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer); if (count < 0) break
            require(output.size() + count <= MAX_METADATA_BYTES); output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private const val VAULT_DIR = "PineconeBackup"
        private const val VAULT_FORMAT = "pinecone-backup-vault"
        private const val SNAPSHOT_FORMAT = "pinecone-vault-snapshot"
        private const val PORTABLE_FORMAT = "pinecone-portable-backup"
        private const val FORMAT_VERSION = 1
        private const val RETENTION = 3
        private const val AUTOMATIC = "automatic"
        private const val MANUAL = "manual"
        private const val MAX_METADATA_BYTES = 32 * 1024 * 1024
        private val SHA_PATTERN = Regex("[0-9a-f]{64}")
        private val FILE_PATTERN = Regex("[0-9a-f]{64}\\.[A-Za-z0-9]+")
        private val operationMutex = Mutex()

        private class UnsupportedBackupException : IllegalArgumentException()
        private class InvalidDictionaryException(cause: Throwable) : IllegalArgumentException(cause)

    }
}
