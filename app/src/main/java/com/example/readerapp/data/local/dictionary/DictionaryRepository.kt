package com.example.readerapp.data.local.dictionary

import android.content.Context
import android.net.Uri
import com.example.readerapp.data.local.InstalledDictionary
import com.example.readerapp.data.local.ReaderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import com.example.readerapp.data.model.DictionaryBackupPayload

sealed class ImportState {
    object Idle : ImportState()
    data class Loading(val progress: Int) : ImportState()
    object Success : ImportState()
    data class Error(val message: String) : ImportState()
}

class DictionaryRepository(
    private val context: Context,
    private val preferences: ReaderPreferences
) {
    private val parser = StardictParser(context)
    
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _restoreState = MutableStateFlow<ImportState>(ImportState.Idle)
    val restoreState: StateFlow<ImportState> = _restoreState.asStateFlow()

    private val _backupState = MutableStateFlow<ImportState>(ImportState.Idle)
    val backupState: StateFlow<ImportState> = _backupState.asStateFlow()

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true 
    }

    suspend fun importDictionary(uri: Uri) {
        _importState.value = ImportState.Loading(0)
        try {
            val (dictId, info) = parser.parseDictionary(uri) { progress ->
                _importState.value = ImportState.Loading(progress)
            }

            // Update installed dictionaries
            val currentSettings = preferences.readerSettings.first()
            val newInstalled = currentSettings.installedDictionaries + InstalledDictionary(
                id = dictId,
                name = info.name,
                wordCount = info.wordCount
            )
            
            // Set as active if it's the first one
            val newActiveId = if (currentSettings.activeDictionaryId.isEmpty()) dictId else currentSettings.activeDictionaryId

            preferences.updateSettings(
                currentSettings.copy(
                    installedDictionaries = newInstalled,
                    activeDictionaryId = newActiveId
                )
            )

            _importState.value = ImportState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            _importState.value = ImportState.Error(e.message ?: "Unknown error occurred")
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun resetRestoreState() {
        _restoreState.value = ImportState.Idle
    }

    fun resetBackupState() {
        _backupState.value = ImportState.Idle
    }

    suspend fun lookupWord(dictionaryId: String, word: String): List<DictionaryEntry> {
        if (dictionaryId.isEmpty()) return emptyList()
        val db = DictionaryDatabase.getDatabase(context, dictionaryId)
        val dao = db.dictionaryDao()
        
        val results = mutableListOf<DictionaryEntry>()
        
        // Exact match
        results.addAll(dao.getDefinitions(word))
        
        // Synonyms
        val synonyms = dao.getSynonyms(word)
        for (syn in synonyms) {
            val baseEntries = dao.getDefinitionsByIndex(syn.originalWordIndex)
            results.addAll(baseEntries)
        }
        
        // If not found, try partial/prefix
        if (results.isEmpty()) {
            results.addAll(dao.getPrefixDefinitions(word))
        }
        
        // Return distinct definitions to avoid duplicates
        return results.distinctBy { it.definition }
    }
    
    suspend fun deleteDictionary(dictionaryId: String) {
        // Remove from settings
        val currentSettings = preferences.readerSettings.first()
        val newInstalled = currentSettings.installedDictionaries.filter { it.id != dictionaryId }
        val newActiveId = if (currentSettings.activeDictionaryId == dictionaryId) {
            newInstalled.firstOrNull()?.id ?: ""
        } else {
            currentSettings.activeDictionaryId
        }
        preferences.updateSettings(
            currentSettings.copy(
                installedDictionaries = newInstalled,
                activeDictionaryId = newActiveId
            )
        )
        
        // Delete database files
        val dbFile = context.getDatabasePath("dict_$dictionaryId.db")
        if (dbFile.exists()) dbFile.delete()
        val walFile = context.getDatabasePath("dict_$dictionaryId.db-wal")
        if (walFile.exists()) walFile.delete()
        val shmFile = context.getDatabasePath("dict_$dictionaryId.db-shm")
        if (shmFile.exists()) shmFile.delete()
    }

    suspend fun renameDictionary(dictionaryId: String, newName: String) {
        val currentSettings = preferences.readerSettings.first()
        val newInstalled = currentSettings.installedDictionaries.map {
            if (it.id == dictionaryId) it.copy(name = newName) else it
        }
        preferences.updateSettings(
            currentSettings.copy(installedDictionaries = newInstalled)
        )
    }

    suspend fun backupDictionaries() = withContext(Dispatchers.IO) {
        _backupState.value = ImportState.Loading(0)
        val settings = preferences.readerSettings.first()
        val backupFolderUriString = settings.backupFolderUri
        if (backupFolderUriString.isEmpty()) {
            _backupState.value = ImportState.Error("Backup folder not set in Settings")
            return@withContext
        }

        try {
            val backupFolderUri = backupFolderUriString.toUri()
            val backupFolder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, backupFolderUri)
            if (backupFolder == null || !backupFolder.canWrite()) {
                _backupState.value = ImportState.Error("Cannot write to backup folder")
                return@withContext
            }

            val installed = settings.installedDictionaries
            val payload = DictionaryBackupPayload(
                installedDictionaries = installed,
                activeDictionaryId = settings.activeDictionaryId
            )
            val jsonString = json.encodeToString(payload)

            val backupFileName = "dictionary_backup.pinedict"
            
            // Find existing to overwrite, or create new
            var backupFile = backupFolder.findFile(backupFileName)
            if (backupFile == null) {
                backupFile = backupFolder.createFile("application/octet-stream", backupFileName)
            }
            if (backupFile == null) {
                _backupState.value = ImportState.Error("Failed to create backup file")
                return@withContext
            }

            val resolver = context.contentResolver
            val outputStream = resolver.openOutputStream(backupFile.uri, "wt")
            if (outputStream == null) {
                _backupState.value = ImportState.Error("Failed to open output stream")
                return@withContext
            }
            outputStream.use { os ->
                ZipOutputStream(os).use { zos ->
                    zos.setLevel(java.util.zip.Deflater.BEST_SPEED)
                    
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
            _backupState.value = ImportState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = ImportState.Error(e.message ?: "Failed to backup dictionaries")
        }
    }

    suspend fun restoreDictionaries(uri: Uri) = withContext(Dispatchers.IO) {
        _restoreState.value = ImportState.Loading(0)
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
                _restoreState.value = ImportState.Error("Invalid dictionary backup format")
                return@withContext
            }

            val jsonString = FileInputStream(metadataFile).use { InputStreamReader(it, Charsets.UTF_8).readText() }
            val payload = json.decodeFromString<DictionaryBackupPayload>(jsonString)

            // Delete existing dictionary DBs before copying new ones
            val settings = preferences.readerSettings.first()
            for (dict in settings.installedDictionaries) {
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
            _restoreState.value = ImportState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            _restoreState.value = ImportState.Error(e.message ?: "Failed to restore dictionaries")
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
