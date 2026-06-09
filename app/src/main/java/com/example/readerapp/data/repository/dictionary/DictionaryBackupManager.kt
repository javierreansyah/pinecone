package com.example.readerapp.data.repository.dictionary

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.readerapp.data.local.database.dictionary.DictionaryDatabase
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.model.DictionaryBackupPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DictionaryBackupManager(
    private val context: Context, private val preferences: ReaderPreferences
) {
    private val _restoreState = MutableStateFlow<DictionaryState>(DictionaryState.Idle)
    val restoreState: StateFlow<DictionaryState> = _restoreState.asStateFlow()

    private val _backupState = MutableStateFlow<DictionaryState>(DictionaryState.Idle)
    val backupState: StateFlow<DictionaryState> = _backupState.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun resetRestoreState() {
        _restoreState.value = DictionaryState.Idle
    }

    fun resetBackupState() {
        _backupState.value = DictionaryState.Idle
    }

    suspend fun backupDictionaries() = withContext(Dispatchers.IO) {
        _backupState.value = DictionaryState.Loading(0)
        val settings = preferences.readerSettings.first()
        val backupFolderUriString = settings.backupFolderUri
        if (backupFolderUriString.isEmpty()) {
            _backupState.value = DictionaryState.Error("Backup folder not set in Settings")
            return@withContext
        }

        try {
            val backupFolderUri = backupFolderUriString.toUri()
            val backupFolder = DocumentFile.fromTreeUri(context, backupFolderUri)
            if (backupFolder == null || !backupFolder.canWrite()) {
                _backupState.value = DictionaryState.Error("Cannot write to backup folder")
                return@withContext
            }

            val installed = settings.installedDictionaries
            val payload = DictionaryBackupPayload(
                installedDictionaries = installed, activeDictionaryId = settings.activeDictionaryId
            )
            val jsonString = json.encodeToString(payload)

            val backupFileName = "dictionary_backup.pinedict"

            // Find existing to overwrite, or create new
            var backupFile = backupFolder.findFile(backupFileName)
            if (backupFile == null) {
                backupFile = backupFolder.createFile("application/octet-stream", backupFileName)
            }
            if (backupFile == null) {
                _backupState.value = DictionaryState.Error("Failed to create backup file")
                return@withContext
            }

            val resolver = context.contentResolver
            val outputStream = resolver.openOutputStream(backupFile.uri, "wt")
            if (outputStream == null) {
                _backupState.value = DictionaryState.Error("Failed to open output stream")
                return@withContext
            }
            outputStream.use { os ->
                ZipOutputStream(os).use { zos ->
                    zos.setLevel(Deflater.BEST_SPEED)

                    // JSON
                    zos.putNextEntry(ZipEntry("metadata.json"))
                    zos.write(jsonString.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // SQLite DBs
                    for (dict in installed) {
                        val dbFile = context.getDatabasePath("dict_${dict.id}.db")
                        val walFile = context.getDatabasePath("dict_${dict.id}.db-wal")
                        val shmFile = context.getDatabasePath("dict_${dict.id}.db-shm")

                        if (dbFile.exists()) zipFile(dbFile, "dict_${dict.id}.db", zos)
                        if (walFile.exists()) zipFile(walFile, "dict_${dict.id}.db-wal", zos)
                        if (shmFile.exists()) zipFile(shmFile, "dict_${dict.id}.db-shm", zos)
                    }
                }
            }
            _backupState.value = DictionaryState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = DictionaryState.Error(e.message ?: "Failed to backup dictionaries")
        }
    }

    suspend fun restoreDictionaries(uri: Uri) = withContext(Dispatchers.IO) {
        _restoreState.value = DictionaryState.Loading(0)
        try {
            val resolver = context.contentResolver

            val tempDir = File(context.cacheDir, "dict_restore_temp").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            resolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(tempDir, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val metadataFile = File(tempDir, "metadata.json")
            if (!metadataFile.exists()) {
                tempDir.deleteRecursively()
                _restoreState.value = DictionaryState.Error("Invalid dictionary backup format")
                return@withContext
            }

            val jsonString = FileInputStream(metadataFile).use {
                InputStreamReader(
                    it, Charsets.UTF_8
                ).readText()
            }
            val payload = json.decodeFromString<DictionaryBackupPayload>(jsonString)

            // Delete existing dictionary DBs before copying new ones
            val settings = preferences.readerSettings.first()
            for (dict in settings.installedDictionaries) {
                DictionaryDatabase.closeDatabase(dict.id)
                context.getDatabasePath("dict_${dict.id}.db").takeIf { it.exists() }?.delete()
                context.getDatabasePath("dict_${dict.id}.db-wal").takeIf { it.exists() }?.delete()
                context.getDatabasePath("dict_${dict.id}.db-shm").takeIf { it.exists() }?.delete()
            }

            // Copy restored DBs
            tempDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("dict_") && file.name != "metadata.json") {
                    val destFile = context.getDatabasePath(file.name)
                    file.copyTo(destFile, overwrite = true)
                }
            }

            // Update preferences
            preferences.updateSettings(
                settings.copy(
                    installedDictionaries = payload.installedDictionaries,
                    activeDictionaryId = payload.activeDictionaryId
                )
            )

            tempDir.deleteRecursively()
            _restoreState.value = DictionaryState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            _restoreState.value =
                DictionaryState.Error(e.message ?: "Failed to restore dictionaries")
        }
    }

    private fun zipFile(fileToZip: File, zipEntryName: String, zos: ZipOutputStream) {
        FileInputStream(fileToZip).use { fis ->
            val zipEntry = ZipEntry(zipEntryName)
            zos.putNextEntry(zipEntry)
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }
}
