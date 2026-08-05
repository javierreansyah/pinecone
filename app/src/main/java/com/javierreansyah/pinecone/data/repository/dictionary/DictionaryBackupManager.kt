package com.javierreansyah.pinecone.data.repository.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryDatabase
import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.model.DictionaryBackupManifest
import com.javierreansyah.pinecone.data.model.DictionaryBackupPayload
import com.javierreansyah.pinecone.data.model.DictionaryIndex
import com.javierreansyah.pinecone.data.model.DictionaryIndexEntry
import com.javierreansyah.pinecone.data.model.DictionaryReference
import com.javierreansyah.pinecone.data.model.LibraryBackupManifest
import com.javierreansyah.pinecone.data.repository.backup.BackupArchiveIO
import com.javierreansyah.pinecone.data.repository.backup.BackupFailure
import com.javierreansyah.pinecone.data.repository.backup.BackupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

class DictionaryBackupManager(
    private val context: Context,
    private val preferences: ReaderPreferences
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun backupDictionaries(): Boolean = backupDictionariesResult().isSuccess

    suspend fun backupDictionariesResult(): BackupResult = withContext(Dispatchers.IO) {
        mutex.withLock { backupAll() }
    }

    suspend fun restoreDictionaries(uri: Uri): Boolean = restoreDictionariesResult(uri).isSuccess

    suspend fun restoreDictionariesResult(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        mutex.withLock { restoreArchive(uri) }
    }

    suspend fun currentReferences(): List<DictionaryReference> = withContext(Dispatchers.IO) {
        val settings = preferences.readerSettings.first()
        val root = settings.backupFolderUri.takeIf { it.isNotBlank() }?.toUri()
            ?.let { DocumentFile.fromTreeUri(context, it) } ?: return@withContext emptyList()
        val folder = root.findFile(DICTIONARY_FOLDER) ?: return@withContext emptyList()
        val indexFile = folder.findFile("dictionary_index.json") ?: return@withContext emptyList()
        val index = context.contentResolver.openInputStream(indexFile.uri)?.use { input ->
            json.decodeFromString<DictionaryIndex>(input.readBytes().toString(Charsets.UTF_8))
        } ?: return@withContext emptyList()
        index.dictionaries.map {
            DictionaryReference(
                id = it.dictionary.id,
                name = it.dictionary.name,
                sha256 = it.sha256,
                fileName = it.fileName
            )
        }
    }

    suspend fun restoreForLibraryBackup(libraryUri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            mutex.withLock { restoreCompanions(libraryUri) }
        }

    suspend fun preflightForLibraryBackup(libraryUri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val manifest = readLibraryManifest(libraryUri)
                val settings = preferences.readerSettings.first()
                val root = settings.backupFolderUri.takeIf { it.isNotBlank() }?.toUri()
                    ?.let { DocumentFile.fromTreeUri(context, it) }
                    ?: return@withContext BackupResult.Partial(BackupFailure.MISSING_DICTIONARY)
                if (manifest == null) {
                    return@withContext BackupResult.Success()
                }
                if (manifest.dictionaries.isEmpty()) return@withContext BackupResult.Success()
                val folder = root.findFile(DICTIONARY_FOLDER)
                    ?: return@withContext BackupResult.Partial(BackupFailure.MISSING_DICTIONARY)
                for (reference in manifest.dictionaries) {
                    val file = folder.findFile(reference.fileName)
                        ?: return@withContext BackupResult.Partial(BackupFailure.MISSING_DICTIONARY)
                    val dictionaryManifest = readDictionaryManifest(file.uri)
                        ?: return@withContext BackupResult.Partial(BackupFailure.MISSING_DICTIONARY)
                    if (dictionaryManifest.dictionary.id != reference.id ||
                        dictionaryManifest.databaseSha256 != reference.sha256) {
                        return@withContext BackupResult.Failure(BackupFailure.CHECKSUM_MISMATCH)
                    }
                }
                BackupResult.Success()
            } catch (e: Exception) {
                BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE, e)
            }
        }

    suspend fun pruneUnreferencedBackups() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val settings = preferences.readerSettings.first()
            val root = settings.backupFolderUri.takeIf { it.isNotBlank() }?.toUri()
                ?.let { DocumentFile.fromTreeUri(context, it) } ?: return@withLock
            val dictionaryFolder = root.findFile(DICTIONARY_FOLDER) ?: return@withLock
            val keep = mutableSetOf<String>()
            dictionaryFolder.findFile("dictionary_index.json")?.let { indexFile ->
                context.contentResolver.openInputStream(indexFile.uri)?.use { input ->
                    val index = json.decodeFromString<DictionaryIndex>(
                        input.readBytes().toString(Charsets.UTF_8)
                    )
                    keep += index.dictionaries.map { it.fileName }
                }
            }
            root.findFile("Library")?.listFiles()
                ?.filter { it.name?.endsWith(".pine") == true }
                ?.forEach { backup ->
                    readLibraryManifest(backup.uri)?.let { manifest ->
                        keep += manifest.dictionaries.map { it.fileName }
                    }
                }
            dictionaryFolder.listFiles().forEach { file ->
                val name = file.name.orEmpty()
                if ((name.endsWith(".pinedict") && name !in keep) || name.endsWith(".partial")) {
                    file.delete()
                }
            }
        }
    }

    private suspend fun restoreCompanions(libraryUri: Uri): BackupResult {
        val manifest = readLibraryManifest(libraryUri)
        val settings = preferences.readerSettings.first()
        val root = settings.backupFolderUri.takeIf { it.isNotBlank() }?.toUri()
            ?.let { DocumentFile.fromTreeUri(context, it) }
            ?: return BackupResult.Partial(BackupFailure.MISSING_DICTIONARY)
        if (manifest == null) {
            val legacy = root.findFile("dictionary_backup.pinedict")
                ?: return BackupResult.Success()
            return restoreArchive(legacy.uri)
        }
        if (manifest.dictionaries.isEmpty()) return BackupResult.Success()
        val folder = root.findFile(DICTIONARY_FOLDER)
            ?: return BackupResult.Partial(BackupFailure.MISSING_DICTIONARY)
        val archives = manifest.dictionaries.map { reference ->
            val file = folder.findFile(reference.fileName)
                ?: return BackupResult.Partial(BackupFailure.MISSING_DICTIONARY)
            val dictionaryManifest = readDictionaryManifest(file.uri)
                ?: return BackupResult.Partial(BackupFailure.MISSING_DICTIONARY)
            if (dictionaryManifest.dictionary.id != reference.id ||
                dictionaryManifest.databaseSha256 != reference.sha256) {
                return BackupResult.Failure(BackupFailure.CHECKSUM_MISMATCH)
            }
            reference to file
        }
        for ((_, file) in archives) {
            val result = restoreArchive(file.uri)
            if (!result.isSuccess) return result
        }
        val restored = preferences.readerSettings.first()
        val wantedIds = manifest.dictionaries.map { it.id }.toSet()
        restored.installedDictionaries.filterNot { it.id in wantedIds }.forEach { dictionary ->
            DictionaryDatabase.closeDatabase(dictionary.id)
            context.getDatabasePath("dict_${dictionary.id}.db").delete()
            context.getDatabasePath("dict_${dictionary.id}.db-wal").delete()
            context.getDatabasePath("dict_${dictionary.id}.db-shm").delete()
        }
        val installed = restored.installedDictionaries.filter { it.id in wantedIds }
        preferences.updateSettings(restored.copy(
            installedDictionaries = installed,
            activeDictionaryId = manifest.activeDictionaryId.takeIf { it in wantedIds }
                ?: installed.firstOrNull()?.id.orEmpty()
        ))
        return BackupResult.Success()
    }

    private fun readLibraryManifest(uri: Uri): LibraryBackupManifest? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == "manifest.json") {
                    val bytes = zip.readBytes()
                    require(bytes.size <= 1024 * 1024)
                    return json.decodeFromString(bytes.toString(Charsets.UTF_8))
                }
                zip.closeEntry()
            }
        }
    }

    private fun readDictionaryManifest(uri: Uri): DictionaryBackupManifest? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == "manifest.json") {
                    val bytes = zip.readBytes()
                    require(bytes.size <= 1024 * 1024)
                    return json.decodeFromString(bytes.toString(Charsets.UTF_8))
                }
                zip.closeEntry()
            }
        }
    }

    private fun dictionaryFolder(root: DocumentFile): DocumentFile? =
        root.findFile(DICTIONARY_FOLDER) ?: root.createDirectory(DICTIONARY_FOLDER)

    private suspend fun backupAll(): BackupResult {
        val settings = preferences.readerSettings.first()
        val root = settings.backupFolderUri.takeIf { it.isNotBlank() }?.toUri()
            ?.let { DocumentFile.fromTreeUri(context, it) }
            ?: return BackupResult.Failure(BackupFailure.BACKUP_LOCATION_MISSING)
        if (!root.canWrite()) return BackupResult.Failure(BackupFailure.PERMISSION_DENIED)
        val folder = dictionaryFolder(root)
            ?: return BackupResult.Failure(BackupFailure.PERMISSION_DENIED)
        val entries = mutableListOf<DictionaryIndexEntry>()
        return try {
            val previous = readIndex(folder)?.dictionaries?.associateBy { it.dictionary.id }.orEmpty()
            for (dictionary in settings.installedDictionaries) {
                entries += backupOne(dictionary, folder, previous[dictionary.id])
            }
            writeIndex(folder, DictionaryIndex(
                activeDictionaryId = settings.activeDictionaryId,
                dictionaries = entries
            ))
            BackupResult.Success()
        } catch (e: SecurityException) {
            BackupResult.Failure(BackupFailure.PERMISSION_DENIED, e)
        } catch (e: Exception) {
            BackupResult.Failure(BackupFailure.IO_ERROR, e)
        }
    }

    private fun backupOne(
        dictionary: InstalledDictionary,
        folder: DocumentFile,
        previous: DictionaryIndexEntry?
    ): DictionaryIndexEntry {
        val temp = cacheDir("dict_backup")
        var partial: DocumentFile? = null
        try {
            val live = context.getDatabasePath("dict_${dictionary.id}.db")
            require(live.isFile)
            if (previous != null && previous.dictionary == dictionary &&
                previous.sourceSize == live.length() &&
                previous.sourceLastModified == live.lastModified() &&
                folder.findFile(previous.fileName) != null) {
                return previous
            }
            DictionaryDatabase.closeDatabase(dictionary.id)
            SQLiteDatabase.openDatabase(live.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
            val snapshot = File(temp, "dictionary.db")
            live.copyTo(snapshot, overwrite = false)
            compactAndValidate(snapshot)
            val hash = BackupArchiveIO.sha256(snapshot)
            val fileName = "${safeId(dictionary.id)}_${hash.take(16)}.pinedict"
            if (folder.findFile(fileName) == null) {
                val manifest = DictionaryBackupManifest(
                    createdAt = System.currentTimeMillis(),
                    dictionary = dictionary,
                    databaseSize = snapshot.length(),
                    databaseSha256 = hash
                )
                val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
                partial = folder.createFile("application/octet-stream", "$fileName.partial")
                    ?: error("Cannot create dictionary backup")
                context.contentResolver.openOutputStream(partial.uri, "wt")?.use { output ->
                    ZipOutputStream(output.buffered()).use { zip ->
                        BackupArchiveIO.writeBytes(zip, "manifest.json", manifestBytes)
                        BackupArchiveIO.writeFile(zip, "dictionary.db", snapshot, false)
                    }
                } ?: error("Cannot open dictionary backup")
                verifyDictionaryArchive(partial.uri, cacheDir("dict_verify"))
                check(partial.renameTo(fileName))
                partial = null
            }
            return DictionaryIndexEntry(
                dictionary, fileName, hash, live.length(), live.lastModified()
            )
        } finally {
            partial?.delete()
            temp.deleteRecursively()
        }
    }

    private fun compactAndValidate(file: File) {
        val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            database.execSQL("PRAGMA user_version = 1")
            database.execSQL("VACUUM")
        } finally {
            database.close()
        }
        validateDatabase(file)
    }

    private fun validateDatabase(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0).equals("ok", true))
            }
            val required = setOf("dictionary_entries", "synonym_entries")
            val found = mutableSetOf<String>()
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'", null
            ).use { cursor ->
                while (cursor.moveToNext()) found += cursor.getString(0)
            }
            require(found.containsAll(required))
        }
    }

    private fun installDatabase(dictionary: InstalledDictionary, source: File) {
        DictionaryDatabase.closeDatabase(dictionary.id)
        val target = context.getDatabasePath("dict_${dictionary.id}.db")
        target.parentFile?.mkdirs()
        val staged = File(target.parentFile, "${target.name}.restore_${UUID.randomUUID()}")
        val rollback = File(target.parentFile, "${target.name}.rollback_${UUID.randomUUID()}")
        source.copyTo(staged, overwrite = false)
        validateDatabase(staged)
        target.takeIf { it.exists() }?.let { check(it.renameTo(rollback)) }
        context.getDatabasePath("${target.name}-wal").delete()
        context.getDatabasePath("${target.name}-shm").delete()
        try {
            check(staged.renameTo(target))
            rollback.delete()
        } catch (error: Throwable) {
            staged.delete()
            if (rollback.exists()) rollback.renameTo(target)
            throw error
        }
    }

    private fun verifyDictionaryArchive(uri: Uri, target: File): DictionaryBackupManifest {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read archive")
            val extracted = BackupArchiveIO.extract(input, target) {
                it == "manifest.json" || it == "dictionary.db"
            }
            val manifestFile = File(target, "manifest.json")
            val databaseFile = File(target, "dictionary.db")
            require(manifestFile.isFile && databaseFile.isFile)
            val manifest = json.decodeFromString<DictionaryBackupManifest>(manifestFile.readText())
            require(manifest.format == "pinecone-dictionary")
            require(manifest.formatVersion == FORMAT_VERSION)
            require(databaseFile.length() == manifest.databaseSize)
            require(BackupArchiveIO.sha256(databaseFile) == manifest.databaseSha256)
            require(extracted == setOf("manifest.json", "dictionary.db"))
            compactAndValidate(databaseFile)
            return manifest
        } finally {
            target.deleteRecursively()
        }
    }

    private fun writeIndex(folder: DocumentFile, index: DictionaryIndex) {
        val bytes = json.encodeToString(index).toByteArray(Charsets.UTF_8)
        val created = folder.createFile(
            "application/json", "dictionary_index.json.partial"
        ) ?: error("Cannot create dictionary index")
        var partial: DocumentFile? = created
        try {
            context.contentResolver.openOutputStream(created.uri, "wt")?.use { it.write(bytes) }
                ?: error("Cannot write dictionary index")
            val decoded = context.contentResolver.openInputStream(created.uri)?.use {
                json.decodeFromString<DictionaryIndex>(it.readBytes().toString(Charsets.UTF_8))
            } ?: error("Cannot verify dictionary index")
            require(decoded == index)
            folder.findFile("dictionary_index.json")?.delete()
            check(created.renameTo("dictionary_index.json"))
            partial = null
        } finally {
            partial?.delete()
        }
    }

    private fun readIndex(folder: DocumentFile): DictionaryIndex? {
        val file = folder.findFile("dictionary_index.json") ?: return null
        return context.contentResolver.openInputStream(file.uri)?.use { input ->
            json.decodeFromString(input.readBytes().toString(Charsets.UTF_8))
        }
    }

    private suspend fun restoreArchive(uri: Uri): BackupResult {
        val temp = cacheDir("dict_restore")
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return BackupResult.Failure(BackupFailure.IO_ERROR)
            val extracted = BackupArchiveIO.extract(input, temp) { path ->
                path in setOf("manifest.json", "metadata.json", "dictionary.db") ||
                    (path.startsWith("dict_") &&
                        (path.endsWith(".db") || path.endsWith(".db-wal") || path.endsWith(".db-shm")))
            }
            val manifestFile = File(temp, "manifest.json")
            if (manifestFile.isFile) {
                require(extracted == setOf("manifest.json", "dictionary.db"))
                restoreCurrentArchive(temp, manifestFile)
            }
            else restoreLegacyArchive(temp)
        } catch (e: SecurityException) {
            BackupResult.Failure(BackupFailure.PERMISSION_DENIED, e)
        } catch (e: IllegalArgumentException) {
            BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE, e)
        } catch (e: Exception) {
            BackupResult.Failure(BackupFailure.IO_ERROR, e)
        } finally {
            temp.deleteRecursively()
        }
    }

    private suspend fun restoreCurrentArchive(
        temp: File,
        manifestFile: File
    ): BackupResult {
        val manifest = json.decodeFromString<DictionaryBackupManifest>(manifestFile.readText())
        if (manifest.format != "pinecone-dictionary") {
            return BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE)
        }
        if (manifest.formatVersion != FORMAT_VERSION) {
            return BackupResult.Failure(BackupFailure.UNSUPPORTED_VERSION)
        }
        val databaseFile = File(temp, "dictionary.db")
        require(databaseFile.isFile && databaseFile.length() == manifest.databaseSize)
        require(BackupArchiveIO.sha256(databaseFile) == manifest.databaseSha256)
        validateDatabase(databaseFile)
        installDatabase(manifest.dictionary, databaseFile)
        val settings = preferences.readerSettings.first()
        val installed = settings.installedDictionaries
            .filterNot { it.id == manifest.dictionary.id } + manifest.dictionary
        preferences.updateSettings(settings.copy(
            installedDictionaries = installed,
            activeDictionaryId = settings.activeDictionaryId.ifBlank { manifest.dictionary.id }
        ))
        return BackupResult.Success()
    }

    private suspend fun restoreLegacyArchive(temp: File): BackupResult {
        val metadata = File(temp, "metadata.json")
        if (!metadata.isFile) return BackupResult.Failure(BackupFailure.MALFORMED_ARCHIVE)
        val payload = json.decodeFromString<DictionaryBackupPayload>(metadata.readText())
        for (dictionary in payload.installedDictionaries) {
            val source = File(temp, "dict_${dictionary.id}.db")
            require(source.isFile)
            SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                db.execSQL("PRAGMA user_version = 1")
            }
            File(source.path + "-wal").delete()
            File(source.path + "-shm").delete()
            validateDatabase(source)
            installDatabase(dictionary, source)
        }
        val current = preferences.readerSettings.first()
        preferences.updateSettings(current.copy(
            installedDictionaries = payload.installedDictionaries,
            activeDictionaryId = payload.activeDictionaryId
                .takeIf { id -> payload.installedDictionaries.any { it.id == id } }
                ?: payload.installedDictionaries.firstOrNull()?.id.orEmpty()
        ))
        return BackupResult.Success()
    }

    private fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun cacheDir(prefix: String) =
        File(context.cacheDir, "${prefix}_${UUID.randomUUID()}").apply {
            deleteRecursively(); check(mkdirs())
        }

    companion object {
        private const val DICTIONARY_FOLDER = "Dictionaries"
        private const val FORMAT_VERSION = 1
        private val mutex = Mutex()

        fun recoverInterruptedRestores(context: Context) {
            val directory = context.getDatabasePath("reader_database").parentFile ?: return
            directory.listFiles()?.filter { it.name.startsWith("dict_") }?.forEach { file ->
                when {
                    ".restore_" in file.name -> file.delete()
                    ".rollback_" in file.name -> {
                        val targetName = file.name.substringBefore(".rollback_")
                        val target = File(directory, targetName)
                        if (target.exists()) file.delete() else file.renameTo(target)
                    }
                }
            }
        }
    }
}
