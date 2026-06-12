package com.example.readerapp.data.repository.dictionary

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.readerapp.data.local.database.dictionary.DictionaryDatabase
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.model.DictionaryBackupPayload
import kotlinx.coroutines.Dispatchers
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
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun backupDictionaries(): Boolean = withContext(Dispatchers.IO) {
        val settings = preferences.readerSettings.first()
        val backupFolderUriString = settings.backupFolderUri
        if (backupFolderUriString.isEmpty()) {
            return@withContext false
        }

        try {
            val backupFolderUri = backupFolderUriString.toUri()
            val backupFolder = DocumentFile.fromTreeUri(context, backupFolderUri)
            if (backupFolder == null || !backupFolder.canWrite()) {
                return@withContext false
            }

            val installed = settings.installedDictionaries
            val backupFileName = "dictionary_backup.pinedict"

            // Find existing to overwrite, or create new
            var backupFile = backupFolder.findFile(backupFileName)

            if (backupFile != null) {
                val backupLastModified = backupFile.lastModified()
                var maxDbModified = 0L
                for (dict in installed) {
                    val dbFile = context.getDatabasePath("dict_${dict.id}.db")
                    val walFile = context.getDatabasePath("dict_${dict.id}.db-wal")
                    val shmFile = context.getDatabasePath("dict_${dict.id}.db-shm")
                    if (dbFile.exists()) maxDbModified = maxOf(maxDbModified, dbFile.lastModified())
                    if (walFile.exists()) maxDbModified =
                        maxOf(maxDbModified, walFile.lastModified())
                    if (shmFile.exists()) maxDbModified =
                        maxOf(maxDbModified, shmFile.lastModified())
                }

                // Verify if the dictionary configurations match the backup file
                val backupPayload = readBackupMetadata(backupFile.uri)
                val isMetadataUnchanged = backupPayload != null &&
                        backupPayload.activeDictionaryId == settings.activeDictionaryId &&
                        backupPayload.installedDictionaries == installed

                if (isMetadataUnchanged && backupLastModified >= maxDbModified) {
                    // Skip backup, dictionaries and metadata haven't changed
                    return@withContext true
                }
            }

            if (backupFile == null) {
                backupFile = backupFolder.createFile("application/octet-stream", backupFileName)
            }
            if (backupFile == null) {
                return@withContext false
            }

            val payload = DictionaryBackupPayload(
                installedDictionaries = installed, activeDictionaryId = settings.activeDictionaryId
            )
            val jsonString = json.encodeToString(payload)

            val resolver = context.contentResolver
            val outputStream =
                resolver.openOutputStream(backupFile.uri, "wt") ?: return@withContext false
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
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun restoreDictionaries(uri: Uri): Boolean = withContext(Dispatchers.IO) {
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
                return@withContext false
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
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
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

    private fun readBackupMetadata(uri: Uri): DictionaryBackupPayload? {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "metadata.json") {
                            val jsonBytes = zis.readBytes()
                            val jsonString = jsonBytes.toString(Charsets.UTF_8)
                            return json.decodeFromString<DictionaryBackupPayload>(jsonString)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
