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
import com.javierreansyah.pinecone.data.model.RenditionObjectReference
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
    val isManual: Boolean,
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
                val dirs = openVault(create = false) ?: return@runCatching emptyList()
                readSnapshots(dirs).asSequence().map { (file, snapshot) ->
                    BackupSnapshotInfo(
                        file.uri, snapshot.id, snapshot.createdAt,
                        snapshot.backupKind == MANUAL,
                    )
                }.sortedByDescending { (_, _, itTimestamp) -> itTimestamp }.toList()
            }.getOrDefault(emptyList())
        }
    }

    suspend fun restoreSnapshot(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                completePendingSettingsRestore()
                val dirs = openVault(create = false)
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

    suspend fun completePendingSettingsRestore(): Unit = withContext(Dispatchers.IO) {
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
        val dictionaries: DocumentFile,
    )

    private data class CreatedObject(
        val file: DocumentFile,
        val info: VaultObject,
        val reused: Boolean,
    )

    private suspend fun createSnapshotLocked(manual: Boolean): BackupResult {
        val started = System.currentTimeMillis()
        var bytesWritten = 0L
        var bytesReused = 0L
        val warnings = mutableListOf<BackupFailure>()
        val phases = linkedMapOf<String, Long>()
        return try {
            val dirs = openVault(create = true)
                ?: return BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
            val settings = preferences.readerSettings.first()
            var phaseStarted = System.currentTimeMillis()
            val (revision, payload) = librarySnapshot()
            phases["snapshot"] = System.currentTimeMillis() - phaseStarted
            val portableSettings = portableSettings(settings)
            val fingerprint = stateFingerprint(revision, payload, settings)
            val previous =
                readSnapshots(dirs).maxByOrNull { (_, itSecond) -> itSecond.createdAt }?.second
            if (!manual && (previous?.stateFingerprint == fingerprint)) return BackupResult.Skipped

            val priorBooks = previous?.books?.associateBy { (itBookId) -> itBookId }.orEmpty()
            phaseStarted = System.currentTimeMillis()
            val bookRefs = payload.books.map { (bookId2, _, bookCoverPath, bookFilePath) ->
                val source = File(bookFilePath)
                require(source.isFile) { "Missing book $bookId2" }
                val old = priorBooks[bookId2]
                val bookObject = reuseOrWriteRaw(
                    source, VaultObjectKind.BOOK, dirs.books, old?.book,
                ).also { (_, itInfo, itReused) ->
                    if (itReused) bytesReused += itInfo.storedSize else bytesWritten += itInfo.storedSize
                }.info
                val coverObject = bookCoverPath?.let { path ->
                    val cover = File(path)
                    require(cover.isFile) { "Missing cover $bookId2" }
                    reuseOrWriteRaw(
                        cover,
                        VaultObjectKind.COVER,
                        dirs.covers,
                        old?.cover
                    ).also { (_, itInfo2, itReused2) ->
                        if (itReused2) bytesReused += itInfo2.storedSize else bytesWritten += itInfo2.storedSize
                    }.info
                }
                BookObjectReference(bookId2, bookObject, coverObject)
            }

            val priorDictionaries = previous?.dictionaries
                ?.associateBy { (itDictionary) -> itDictionary.id }.orEmpty()
            val priorRenditions = previous?.renditions.orEmpty().associateBy { it.bookId to it.key }
            val renditionRefs = payload.renditions.map { rendition ->
                val source = File(rendition.filePath)
                require(source.isFile) { "Missing rendition ${rendition.bookId}/${rendition.key}" }
                val old = priorRenditions[rendition.bookId to rendition.key]?.objectInfo
                val result = reuseOrWriteRaw(source, VaultObjectKind.BOOK, dirs.books, old)
                if (result.reused) bytesReused += result.info.storedSize else bytesWritten += result.info.storedSize
                RenditionObjectReference(rendition.bookId, rendition.key, result.info)
            }
            val dictionaryRefs =
                settings.installedDictionaries.sortedBy { (itId) -> itId }.map { dictionary ->
                    val source = context.getDatabasePath("dict_${dictionary.id}.db")
                    require(source.isFile) { "Missing dictionary ${dictionary.id}" }
                    val prior = priorDictionaries[dictionary.id]
                    val (_, objectResultInfo, objectResultReused) = if ((prior != null) && (prior.dictionary == dictionary) &&
                        sameSource(source, prior.objectInfo) &&
                        (dirs.dictionaries.findFile(prior.objectInfo.fileName)?.isFile == true)
                    ) CreatedObject(
                        dirs.dictionaries.findFile(prior.objectInfo.fileName)!!,
                        prior.objectInfo, reused = true,
                    )
                    else writeDictionaryObject(dictionary, source, dirs.dictionaries)
                    if (objectResultReused) bytesReused += objectResultInfo.storedSize
                    else bytesWritten += objectResultInfo.storedSize
                    DictionaryObjectReference(dictionary, objectResultInfo)
                }
            phases["objects"] = System.currentTimeMillis() - phaseStarted

            val finalFingerprint = fingerprintFromReferences(
                revision, payload, portableSettings, bookRefs, dictionaryRefs, renditionRefs,
            )
            val currentSettings = preferences.readerSettings.first()
            // The DB payload is an atomic point-in-time snapshot. Reading-position/annotation
            // writes during audio playback need not invalidate it; immutable file identities and
            // settings still must match the objects that were copied.
            if (stateFingerprint(revision, payload, currentSettings) != finalFingerprint) {
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
                activeDictionaryId = settings.activeDictionaryId,
                renditions = renditionRefs,
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
            preferences.markBackup(createdAt, revision)
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
                    .map { it.toBackupRecord() },
                renditions = db.publicationDao().allRenditions(),
                credits = db.publicationDao().allCredits(),
            )
        }

    private suspend fun openVault(create: Boolean): VaultDirs? {
        val (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, settingsBackupFolderUri) = preferences.readerSettings.first()
        val root = settingsBackupFolderUri.takeIf { it.isNotBlank() }?.toUri()
            ?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        if (!root.canRead() || (create && !root.canWrite())) return null
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
        val (formatFormat, formatFormatVersion) = resolver.openInputStream(file.uri)?.use {
            json.decodeFromString<VaultFormat>(it.readBytes().toString(Charsets.UTF_8))
        } ?: error("Cannot read vault format")
        require(formatFormat == VAULT_FORMAT)
        if (formatFormatVersion != FORMAT_VERSION) throw UnsupportedBackupException()
    }

    private fun reuseOrWriteRaw(
        source: File,
        kind: VaultObjectKind,
        folder: DocumentFile,
        previous: VaultObject?,
    ): CreatedObject {
        if ((previous != null) && (previous.kind == kind) && sameSource(source, previous)) {
            folder.findFile(previous.fileName)?.takeIf { it.isFile }?.let {
                return CreatedObject(it, previous, reused = true)
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
                }.getOrDefault(defaultValue = false)
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
                final,
                VaultObject(
                    kind, digest.sha256, digest.size, digest.crc32,
                    final.length(), digest.crc32, name, source.absolutePath,
                    source.length(), source.lastModified(),
                ),
                existingIsValid,
            )
        } finally {
            folder.findFile(partialName)?.delete()
        }
    }

    private fun writeDictionaryObject(
        dictionary: InstalledDictionary,
        live: File,
        folder: DocumentFile,
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
                live.absolutePath, sourceSize, sourceModified,
            )
            verifyDictionaryObject(final, info, null)
            return CreatedObject(final, info, existing != null)
        } finally {
            temp.delete()
            folder.findFile(partialName)?.delete()
        }
    }

    private fun sameSource(file: File, info: VaultObject) =
        (file.absolutePath == info.sourcePath) && (file.length() == info.sourceSize) &&
                (file.lastModified() == info.sourceLastModified)

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
            require((entry.name == "snapshot.json") && !entry.isDirectory)
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
        require((snapshot.backupKind == MANUAL) || (snapshot.backupKind == AUTOMATIC))
        require(snapshot.recordCounts == recordCounts(snapshot.library))
        require(validRelationships(snapshot.library))
        require(snapshot.library.version == FORMAT_VERSION)
        require(snapshot.renditions.map { it.bookId to it.key }
            .toSet() == snapshot.library.renditions.map { it.bookId to it.key }.toSet())
        require(snapshot.renditions.size == snapshot.library.renditions.size)
        require(snapshot.renditions.all { it.objectInfo.kind == VaultObjectKind.BOOK })
        val renditionObjects = snapshot.renditions.associateBy { it.bookId to it.key }
        require(snapshot.library.renditions.all { renditionObjects[it.bookId to it.key]?.objectInfo?.sha256 == it.sha256 })
        require(snapshot.books.all { renditionObjects[it.bookId to "original"]?.objectInfo?.sha256 == it.book.sha256 })
        snapshot.library.books.forEach {
            com.javierreansyah.pinecone.data.publication.PublicationMetadata.decode(it.metadataJson)
            com.javierreansyah.pinecone.data.publication.PublicationMetadata.decode(it.sourceMetadataJson)
        }
        snapshot.library.renditions.forEach {
            require(it.progression.isFinite() && it.progression in 0.0..1.0 && it.furthestProgression.isFinite() && it.furthestProgression in 0.0..1.0)
            require(it.profile in com.javierreansyah.pinecone.data.publication.ReaderProfile.entries.map { profile -> profile.name })
            if (it.preferencesJson != "{}") json.decodeFromString<ReaderSettings>(it.preferencesJson)
            it.sourceMapJson?.let { mapping ->
                json.decodeFromString<List<com.javierreansyah.pinecone.data.publication.conversion.SourcePage>>(
                    mapping
                )
            }
        }
        require(
            snapshot.books.map { (itBookId2) -> itBookId2 }
                .toSet() == snapshot.library.books.map { (itId2) -> itId2 }
                .toSet(),
        )
        val all =
            snapshot.renditions.map { it.objectInfo } + snapshot.books.flatMap { (_, itBook, itCover) ->
                listOfNotNull(
                    itBook,
                    itCover
                )
            } +
                    snapshot.dictionaries.map { (_, itObjectInfo) -> itObjectInfo }
        require(
            all.all { (_, itSha256, itSize, _, itStoredSize, _, itFileName) ->
                itSha256.matches(SHA_PATTERN) && (itSize >= 0) &&
                        (itStoredSize >= 0) && itFileName.matches(FILE_PATTERN)
            },
        )
        require(
            snapshot.dictionaries.map { (itDictionary2) -> itDictionary2.id }
                .toSet().size == snapshot.dictionaries.size,
        )
        require(
            snapshot.books.all { (_, itBook2, itCover2) ->
                (itBook2.kind == VaultObjectKind.BOOK) &&
                        ((itCover2 == null) || (itCover2.kind == VaultObjectKind.COVER))
            },
        )
        require(snapshot.dictionaries.all { (_, itObjectInfo2) -> itObjectInfo2.kind == VaultObjectKind.DICTIONARY })
        require(
            snapshot.settings.installedDictionaries.sortedBy { (itId3) -> itId3 } ==
                    snapshot.dictionaries.map { (itDictionary3) -> itDictionary3 }
                        .sortedBy { (itId4) -> itId4 },
        )
        require(
            snapshot.activeDictionaryId.isBlank() ||
                    snapshot.dictionaries.any { (itDictionary4) -> itDictionary4.id == snapshot.activeDictionaryId },
        )
    }

    private fun enforceRetention(dirs: VaultDirs): Boolean = runCatching {
        readSnapshots(dirs).groupBy { (_, itSecond2) -> itSecond2.backupKind }.values.forEach { group ->
            group.sortedByDescending { (_, itSecond3) -> itSecond3.createdAt }.drop(RETENTION)
                .forEach { (itFirst) ->
                    check(itFirst.delete())
                }
        }
    }.isSuccess

    private fun pruneObjects(dirs: VaultDirs): Boolean = runCatching {
        val retained = readSnapshots(dirs).map { (_, itSecond4) -> itSecond4 }
        val keepBooks = (retained.flatMap { it.books }
            .map { it.book.fileName } + retained.flatMap { it.renditions }
            .map { it.objectInfo.fileName }).toSet()
        val keepCovers =
            retained.flatMap { (_, _, _, _, _, _, _, _, _, _, _, itBooks2) -> itBooks2 }
                .mapNotNull { (_, _, itCover3) -> itCover3?.fileName }.toSet()
        val keepDictionaries =
            retained.flatMap { (_, _, _, _, _, _, _, _, _, _, _, _, itDictionaries) -> itDictionaries }
                .map { (_, itObjectInfo3) -> itObjectInfo3.fileName }.toSet()
        pruneFolder(dirs.books, keepBooks)
        pruneFolder(dirs.covers, keepCovers)
        pruneFolder(dirs.dictionaries, keepDictionaries)
    }.isSuccess

    private fun pruneFolder(folder: DocumentFile, keep: Set<String>) {
        folder.listFiles().forEach { file ->
            val name = file.name.orEmpty()
            if (name.endsWith(".partial") || (name !in keep)) check(file.delete())
        }
    }

    private suspend fun restoreLocked(snapshot: VaultSnapshot, dirs: VaultDirs): BackupResult =
        app.libraryRepository.withAssetTransaction { restoreStaged(snapshot, dirs) }

    private suspend fun restoreStaged(snapshot: VaultSnapshot, dirs: VaultDirs): BackupResult {
        val started = System.currentTimeMillis()
        val stagedBooks = mutableListOf<File>()
        val dictionaryStages = mutableListOf<DictionaryStage>()
        var committed = false
        return try {
            validateSnapshot(snapshot)
            val refs = snapshot.books.associateBy { (itBookId3) -> itBookId3 }
            val token = UUID.randomUUID().toString().replace("-", "")
            val booksDir = File(context.filesDir, "publication-assets").apply { mkdirs() }
            val coversDir = File(context.filesDir, "publication-covers").apply { mkdirs() }
            val rebased = snapshot.library.books.map { book ->
                val (_, refBook, refCover) = refs[book.id] ?: error("Missing book object")
                val bookTarget =
                    File(booksDir, "${book.id}_$token.${refBook.fileName.substringAfterLast('.')}")
                copyRawObject(dirs.books, refBook, bookTarget)
                stagedBooks += bookTarget
                val coverTarget = refCover?.let { cover ->
                    File(
                        coversDir,
                        "${book.id}_$token.${cover.fileName.substringAfterLast('.')}",
                    ).also {
                        copyRawObject(dirs.covers, cover, it); stagedBooks += it
                    }
                }
                book.copy(filePath = bookTarget.absolutePath, coverPath = coverTarget?.absolutePath)
                    .toEntity()
            }
            snapshot.dictionaries.forEach { (referenceDictionary, referenceObjectInfo) ->
                val objectFile = dirs.dictionaries.findFile(referenceObjectInfo.fileName)
                    ?: error("Missing dictionary object")
                val staged = File(context.cacheDir, "dict_${referenceDictionary.id}_$token.db")
                verifyDictionaryObject(objectFile, referenceObjectInfo, staged)
                validateDictionary(staged)
                dictionaryStages += DictionaryStage(referenceDictionary, staged)
            }
            val oldBooks = app.database.bookDao().getAllBooksSync()
            withContext(Dispatchers.Main.immediate) { app.audioSession.releasePlayer() }
            val oldRenditions = app.database.publicationDao().allRenditions()
            val renditionRefs = snapshot.renditions.associateBy { it.bookId to it.key }
            val restoredRenditions = snapshot.library.renditions.map { rendition ->
                val ref = renditionRefs.getValue(rendition.bookId to rendition.key).objectInfo
                val target = if (rendition.key == "original") {
                    File(rebased.first { it.id == rendition.bookId }.filePath)
                } else File(
                    booksDir,
                    "${ref.sha256}_$token.${ref.fileName.substringAfterLast('.')}"
                ).also {
                    copyRawObject(dirs.books, ref, it); stagedBooks += it
                }
                require(
                    target.inputStream()
                        .use { BackupArchiveIO.copyAndDigest(it).sha256 } == rendition.sha256)
                rendition.copy(filePath = target.absolutePath)
            }
            val dictionaryRollback = swapDictionaries(
                dictionaryStages,
                snapshot.settings.installedDictionaries.map { (itId5) -> itId5 }.toSet(),
            )
            try {
                val pending = json.encodeToString(snapshot.settings)
                withContext(kotlinx.coroutines.NonCancellable) {
                    app.database.withTransaction {
                        replaceLibrary(
                            rebased,
                            snapshot.library.copy(renditions = restoredRenditions)
                        )
                        app.database.backupStateDao().setPendingSettingsJson(pending)
                    }
                    committed = true
                }
                applyRestoredSettings(
                    snapshot.settings.copy(
                        installedDictionaries = snapshot.dictionaries.map { (itDictionary5) -> itDictionary5 },
                        activeDictionaryId = snapshot.activeDictionaryId,
                    ),
                )
                dictionaryRollback.commit()
                app.database.backupStateDao().setPendingSettingsJson(null)
                oldBooks.forEach { (_, _, oldCoverPath, oldFilePath) ->
                    deleteOwnedFile(oldFilePath); oldCoverPath?.let(::deleteOwnedFile)
                }
                oldRenditions.forEach { deleteOwnedFile(it.filePath) }
                BackupResult.Success(
                    snapshot.id,
                    durationMillis = System.currentTimeMillis() - started,
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
            dictionaryStages.forEach { (_, itFile) -> itFile.delete() }
        }
    }

    private data class DictionaryStage(val dictionary: InstalledDictionary, val file: File)

    @kotlinx.serialization.Serializable
    private data class RestoreJournal(
        val entries: List<RestoreJournalEntry>,
    )

    @kotlinx.serialization.Serializable
    private data class RestoreJournalEntry(
        val targetPath: String,
        val rollbackPath: String,
        val stagedPath: String?,
        val hadOriginal: Boolean,
    )

    private class DictionaryRollback(private val finish: (Boolean) -> Unit) {
        fun commit() {
            finish(true)
        }

        fun rollback() {
            finish(false)
        }
    }

    private suspend fun swapDictionaries(
        stages: List<DictionaryStage>,
        wantedIds: Set<String>,
    ): DictionaryRollback {
        val currentIds =
            preferences.readerSettings.first().installedDictionaries.map { (itId6) -> itId6 }
                .toSet()
        val allIds = currentIds + wantedIds
        allIds.forEach(DictionaryDatabase::closeDatabase)
        val entries = allIds.map { id ->
            val target = context.getDatabasePath("dict_$id.db")
            val stage = stages.firstOrNull { (itDictionary6) -> itDictionary6.id == id }?.file
            RestoreJournalEntry(
                targetPath = target.absolutePath,
                rollbackPath = File(
                    target.parentFile,
                    "${target.name}.rollback_${UUID.randomUUID()}",
                ).absolutePath,
                stagedPath = stage?.absolutePath,
                hadOriginal = target.exists(),
            )
        }
        val journal = RestoreJournal(entries = entries)
        writeRestoreJournal(journal)
        val rollback = DictionaryRollback { commit ->
            finishJournal(journal, commit)
            restoreJournalFile().delete()
        }
        return try {
            for ((entryTargetPath, entryRollbackPath, entryStagedPath, entryHadOriginal) in entries) {
                val target = File(entryTargetPath)
                if (entryHadOriginal) check(target.renameTo(File(entryRollbackPath)))
                context.getDatabasePath("${target.name}-wal").delete()
                context.getDatabasePath("${target.name}-shm").delete()
                entryStagedPath?.let { stagedPath ->
                    target.parentFile?.mkdirs(); check(File(stagedPath).renameTo(target))
                }
            }
            rollback
        } catch (error: Throwable) {
            rollback.rollback(); throw error
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
        journal.entries.asReversed()
            .forEach { (entryTargetPath2, entryRollbackPath2, entryStagedPath2, entryHadOriginal2) ->
                val target = File(entryTargetPath2)
                val rollback = File(entryRollbackPath2)
                val staged = entryStagedPath2?.let(::File)
                if (commit) {
                    rollback.delete()
                } else if (rollback.exists()) {
                    target.delete(); check(rollback.renameTo(target))
                } else if (!entryHadOriginal2) {
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
        db.publicationDao().putRenditions(payload.renditions)
        db.publicationDao().putCredits(payload.credits)
        db.spaceDao().insertAllSpaces(payload.spaces.map { it.toEntity() })
        if (payload.spaces.none { it.id == "_all_" }) {
            db.spaceDao().insertSpace(
                com.javierreansyah.pinecone.data.local.database.library.SpaceEntity(
                    "_all_",
                    context.getString(com.javierreansyah.pinecone.R.string.action_all),
                    0L
                )
            )
        }
        db.shelfDao().insertAllShelves(payload.shelves.map { it.toEntity() })
        db.bookDao().insertAllAuthors(payload.authors.map { it.toEntity() })
        db.bookDao().insertAllTags(payload.tags.map { it.toEntity() })
        db.bookmarkDao().insertAll(payload.bookmarks.map { it.toEntity() })
        db.noteDao().insertAll(payload.notes.map { it.toEntity() })
        db.shelfDao().insertAllShelfBookCrossRefs(payload.shelfBookCrossRefs.map { it.toEntity() })
        db.spaceDao().insertAllBookSpaceCrossRefs(payload.bookSpaceCrossRefs.map { it.toEntity() })
        db.spaceDao().insertAllBookSpaceCrossRefs(books.map {
            com.javierreansyah.pinecone.data.local.database.library.BookSpaceCrossRef(
                it.id,
                "_all_"
            )
        })
        db.bookDao().insertAllBookAuthorCrossRefs(payload.bookAuthorCrossRefs.map { it.toEntity() })
        db.bookDao().insertAllBookTagCrossRefs(payload.bookTagCrossRefs.map { it.toEntity() })
    }

    private fun copyRawObject(folder: DocumentFile, info: VaultObject, target: File) {
        val source = folder.findFile(info.fileName) ?: error("Missing object")
        val (digestSize, digestSha256, digestCrc32) = resolver.openInputStream(source.uri)
            ?.use { input ->
                FileOutputStream(target).use { BackupArchiveIO.copyAndDigest(input, it) }
            } ?: error("Cannot read object")
        require((digestSize == info.size) && (digestSha256 == info.sha256) && (digestCrc32 == info.crc32))
    }

    private fun verifyDictionaryObject(file: DocumentFile, info: VaultObject, output: File?) {
        val input = resolver.openInputStream(file.uri) ?: error("Cannot read dictionary")
        ZipInputStream(input.buffered()).use { zip ->
            val entry = zip.nextEntry ?: error("Empty dictionary")
            require((entry.name == "dictionary.db") && !entry.isDirectory)
            val (digestSize2, digestSha2562, digestCrc322) = if (output == null) BackupArchiveIO.copyAndDigest(
                zip
            )
            else FileOutputStream(output).use { BackupArchiveIO.copyAndDigest(zip, it) }
            require((digestSize2 == info.size) && (digestSha2562 == info.sha256) && (digestCrc322 == info.crc32))
            require(zip.nextEntry == null)
        }
    }

    private fun validateDictionary(file: File) {
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use {
                    require(it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true))
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
            val dirs = openVault(create = false)
                ?: return BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
            validateAllObjects(snapshot, dirs)
            var written = 0L
            resolver.openOutputStream(destination, "wt")?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                    val manifest = json.encodeToString(PortableBackupManifest(snapshot = snapshot))
                        .toByteArray()
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
                    allObjects(snapshot).forEach { (infoKind, _, _, _, infoStoredSize, infoStoredCrc32, infoFileName) ->
                        val folder = objectFolder(dirs, infoKind)
                        val source = folder.findFile(infoFileName) ?: error("Missing object")
                        val path = "objects/${kindFolder(infoKind)}/${infoFileName}"
                        val entry = ZipEntry(path).apply {
                            method = ZipEntry.STORED
                            size = infoStoredSize; compressedSize = infoStoredSize
                            crc = infoStoredCrc32
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
                durationMillis = System.currentTimeMillis() - started,
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
            val dirs = openVault(create = true)
                ?: return BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
            val input = resolver.openInputStream(source) ?: error("Cannot read import")
            lateinit var snapshot: VaultSnapshot
            var written = 0L
            ZipInputStream(input.buffered()).use { zip ->
                val manifestEntry = zip.nextEntry ?: error("Empty archive")
                require(manifestEntry.name == "manifest.json")
                val (portableFormat, portableFormatVersion, portableSnapshot) = json.decodeFromString<PortableBackupManifest>(
                    zip.readLimitedMetadata().toString(Charsets.UTF_8),
                )
                require(portableFormat == PORTABLE_FORMAT)
                if (portableFormatVersion != FORMAT_VERSION) throw UnsupportedBackupException()
                snapshot = portableSnapshot.also(::validateSnapshot)
                val expected =
                    allObjects(snapshot).associateBy { (itKind, _, _, _, _, _, itFileName2) ->
                        "objects/${kindFolder(itKind)}/${itFileName2}"
                    }.toMutableMap()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(BackupArchiveIO.safeArchivePath(entry.name) && !entry.isDirectory)
                    val (infoKind2, _, _, _, infoStoredSize2, infoStoredCrc322, infoFileName2) = expected.remove(
                        entry.name
                    ) ?: error("Unexpected archive entry")
                    val folder = objectFolder(dirs, infoKind2)
                    if (folder.findFile(infoFileName2) != null) {
                        BackupArchiveIO.copyAndDigest(zip)
                    } else {
                        val partialName = ".${UUID.randomUUID()}.partial"
                        val partial = folder.createFile("application/octet-stream", partialName)
                            ?: error("Cannot create imported object")
                        val (storedSize2, _, storedCrc322) = resolver.openOutputStream(
                            partial.uri,
                            "wt"
                        )?.use {
                            BackupArchiveIO.copyAndDigest(zip, it)
                        } ?: error("Cannot write imported object")
                        require((storedSize2 == infoStoredSize2) && (storedCrc322 == infoStoredCrc322))
                        check(partial.renameTo(infoFileName2))
                        val final =
                            folder.findFile(infoFileName2) ?: error("Missing imported object")
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
                durationMillis = System.currentTimeMillis() - started,
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
            val (portableFormat2, portableFormatVersion2, portableSnapshot2) = json.decodeFromString<PortableBackupManifest>(
                zip.readLimitedMetadata().toString(Charsets.UTF_8),
            )
            require(portableFormat2 == PORTABLE_FORMAT)
            if (portableFormatVersion2 != FORMAT_VERSION) throw UnsupportedBackupException()
            val expected =
                allObjects(portableSnapshot2).associateBy { (itKind2, _, _, _, _, _, itFileName3) ->
                    "objects/${kindFolder(itKind2)}/${itFileName3}"
                }.toMutableMap()
            while (true) {
                val entry = zip.nextEntry ?: break
                val (_, _, _, _, infoStoredSize3, infoStoredCrc323) = expected.remove(entry.name)
                    ?: error("Unexpected export entry")
                val (storedSize3, _, storedCrc323) = BackupArchiveIO.copyAndDigest(zip)
                require((storedSize3 == infoStoredSize3) && (storedCrc323 == infoStoredCrc323))
            }
            require(expected.isEmpty())
        }
    }

    private fun validateAllObjects(snapshot: VaultSnapshot, dirs: VaultDirs) {
        snapshot.renditions.forEach { verifyRaw(dirs.books, it.objectInfo) }
        snapshot.books.forEach { (_, refBook2, refCover2) ->
            verifyRaw(dirs.books, refBook2); refCover2?.let { verifyRaw(dirs.covers, it) }
        }
        snapshot.dictionaries.forEach { (_, refObjectInfo) ->
            val file =
                dirs.dictionaries.findFile(refObjectInfo.fileName) ?: error("Missing dictionary")
            require(file.length() == refObjectInfo.storedSize)
            verifyDictionaryObject(file, refObjectInfo, null)
        }
    }

    private fun verifyRaw(folder: DocumentFile, info: VaultObject) {
        val file = folder.findFile(info.fileName) ?: error("Missing object")
        val (digestSize3, digestSha2563, digestCrc323) = resolver.openInputStream(file.uri)
            ?.use(BackupArchiveIO::copyAndDigest)
            ?: error("Cannot verify object")
        require((digestSize3 == info.size) && (digestSha2563 == info.sha256) && (digestCrc323 == info.crc32))
    }

    private fun allObjects(snapshot: VaultSnapshot): List<VaultObject> =
        (snapshot.renditions.map { it.objectInfo } + snapshot.books.flatMap { (_, itBook4, itCover4) ->
            listOfNotNull(
                itBook4,
                itCover4
            )
        } +
                snapshot.dictionaries.map { (_, itObjectInfo4) -> itObjectInfo4 }).distinctBy { (itKind3, _, _, _, _, _, itFileName4) -> itKind3 to itFileName4 }

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
        installedDictionaries = settings.installedDictionaries.sortedBy { (itId7) -> itId7 },
    )

    private fun stateFingerprint(
        revision: Long,
        payload: LibraryBackupPayload,
        settings: ReaderSettings,
    ): String {
        val books = payload.books.sortedBy { (itId8) -> itId8 }
            .joinToString("|") { (_, _, bookCoverPath2, bookFilePath2) ->
                listOfNotNull(bookFilePath2, bookCoverPath2).joinToString(",") { path ->
                    val file = File(path); "$path:${file.length()}:${file.lastModified()}"
                }
            }
        val renditions =
            payload.renditions.sortedWith(compareBy({ it.bookId }, { it.key })).joinToString("|") {
                val file =
                    File(it.filePath); "${file.absolutePath}:${file.length()}:${file.lastModified()}"
            }
        val dictionaries =
            settings.installedDictionaries.sortedBy { (itId9) -> itId9 }
                .joinToString("|") { (dictionaryId, dictionaryName, dictionaryWordCount) ->
                    val file = context.getDatabasePath("dict_${dictionaryId}.db")
                    "${dictionaryId}:${dictionaryName}:${dictionaryWordCount}:${file.length()}:${file.lastModified()}"
                }
        val settingsHash = BackupArchiveIO.sha256(
            json.encodeToString(portableSettings(settings)).toByteArray(),
        )
        return BackupArchiveIO.sha256("$revision:$settingsHash:$books:$dictionaries:$renditions".toByteArray())
    }

    private fun fingerprintFromReferences(
        revision: Long,
        payload: LibraryBackupPayload,
        settings: ReaderSettings,
        books: List<BookObjectReference>,
        dictionaries: List<DictionaryObjectReference>,
        renditions: List<RenditionObjectReference>,
    ): String {
        val byId = books.associateBy { (itBookId4) -> itBookId4 }
        val bookState =
            payload.books.sortedBy { (itId10) -> itId10 }.joinToString("|") { (bookId3) ->
                val (_, refBook3, refCover3) = byId.getValue(bookId3)
                listOfNotNull(
                    refBook3,
                    refCover3
                ).joinToString(",") { (_, _, _, _, _, _, _, itSourcePath, itSourceSize, itSourceLastModified) ->
                    "${itSourcePath}:${itSourceSize}:${itSourceLastModified}"
                }
            }
        val dictionaryState = dictionaries.sortedBy { (itDictionary7) -> itDictionary7.id }
            .joinToString("|") { (itDictionary8, itObjectInfo5) ->
                val (_, _, _, _, _, _, _, _, sourceSourceSize, sourceSourceLastModified) = itObjectInfo5
                "${itDictionary8.id}:${itDictionary8.name}:${itDictionary8.wordCount}:" +
                        "${sourceSourceSize}:${sourceSourceLastModified}"
            }
        val settingsHash = BackupArchiveIO.sha256(json.encodeToString(settings).toByteArray())
        val renditionState =
            renditions.sortedWith(compareBy({ it.bookId }, { it.key })).joinToString("|") {
                val ref =
                    it.objectInfo; "${ref.sourcePath}:${ref.sourceSize}:${ref.sourceLastModified}"
            }
        return BackupArchiveIO.sha256(
            "$revision:$settingsHash:$bookState:$dictionaryState:$renditionState".toByteArray(),
        )
    }

    private suspend fun applyRestoredSettings(restored: ReaderSettings) {
        val (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, currentLastBackupTime, _, currentBackupFolderUri) = preferences.readerSettings.first()
        preferences.updateAllSettings(
            restored.copy(
                backupFolderUri = currentBackupFolderUri,
                lastBackupRevision = -1,
                lastBackupTime = currentLastBackupTime,
            ),
        )
        if (!restored.unsortedEnabled) app.libraryRepository.releaseUnsortedBooks()
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
        "bookSpaceCrossRefs" to payload.bookSpaceCrossRefs.size,
        "renditions" to payload.renditions.size,
        "credits" to payload.credits.size,
    )

    private fun validRelationships(payload: LibraryBackupPayload): Boolean {
        val books = payload.books.map { (itId11) -> itId11 }.toSet()
        val shelves = payload.shelves.map { (itId12) -> itId12 }.toSet()
        val spaces = payload.spaces.map { (itId13) -> itId13 }.toSet()
        val authors = payload.authors.map { (itId14) -> itId14 }.toSet()
        val tags = payload.tags.map { (itId15) -> itId15 }.toSet()
        val renditions = payload.renditions.map { it.bookId to it.key }.toSet()
        return (books.size == payload.books.size) && (shelves.size == payload.shelves.size) &&
                payload.credits.map { Triple(it.bookId, it.role, it.position) }
                    .toSet().size == payload.credits.size &&
                (renditions.size == payload.renditions.size) &&
                payload.renditions.all {
                    it.bookId in books && it.key in setOf(
                        "original",
                        "read-mode"
                    )
                } &&
                payload.books.all { (it.id to it.activeRendition) in renditions && (it.id to "original") in renditions } &&
                payload.credits.all { it.bookId in books } &&
                payload.bookmarks.all { (it.bookId to it.renditionKey) in renditions } && payload.notes.all { (it.bookId to it.renditionKey) in renditions } &&
                (spaces.size == payload.spaces.size) && (authors.size == payload.authors.size) &&
                (tags.size == payload.tags.size) &&
                payload.bookmarks.all { (_, itBookId5) -> itBookId5 in books } && payload.notes.all { (_, itBookId6) -> itBookId6 in books } &&
                payload.shelves.all { (_, itSpaceId) -> itSpaceId in spaces } &&
                payload.shelfBookCrossRefs.all { (itShelfId, itBookId7) -> (itBookId7 in books) && (itShelfId in shelves) } &&
                payload.bookSpaceCrossRefs.all { (itBookId8, itSpaceId2) -> (itBookId8 in books) && (itSpaceId2 in spaces) } &&
                payload.bookAuthorCrossRefs.all { (itBookId9, itAuthorId) -> (itBookId9 in books) && (itAuthorId in authors) } &&
                payload.bookTagCrossRefs.all { (itBookId10, itTagId) -> (itBookId10 in books) && (itTagId in tags) }
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
            require((output.size() + count) <= MAX_METADATA_BYTES); output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private const val VAULT_DIR = "PineconeBackup"
        private const val VAULT_FORMAT = "pinecone-backup-vault"
        private const val SNAPSHOT_FORMAT = "pinecone-vault-snapshot"
        private const val PORTABLE_FORMAT = "pinecone-portable-backup"
        private const val FORMAT_VERSION = 2
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
