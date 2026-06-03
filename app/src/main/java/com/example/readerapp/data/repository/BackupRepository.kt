package com.example.readerapp.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.example.readerapp.data.local.ReaderPreferences
import kotlinx.coroutines.flow.first

class BackupRepository(private val context: Context) {

    private val readerPreferences = ReaderPreferences(context)
    private val backupFileName = "Pinecone_Backup.bak"

    suspend fun performBackup(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val settings = readerPreferences.readerSettings.first()
        val lastBackupTime = settings.lastBackupTime

        val dbFile = context.getDatabasePath("reader_database")
        val booksDir = File(context.filesDir, "books")
        val coversDir = File(context.filesDir, "covers")

        val lastModified = maxOf(
            dbFile.lastModified(),
            booksDir.lastModified(),
            coversDir.lastModified()
        )

        // If not forced, check if changes were made since last backup
        if (!force && lastModified <= lastBackupTime) {
            return@withContext true // Skip backup, but consider it "successful"
        }

        try {
            // We use MediaStore to save the backup in the Documents folder
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            
            // Check if file already exists in Documents/Pinecone
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("Pinecone_Backup%", Environment.DIRECTORY_DOCUMENTS + "/Pinecone/")
            
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val existingUri = Uri.withAppendedPath(collection, id.toString())
                    try {
                        resolver.delete(existingUri, null, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Create new
            val details = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Pinecone/")
            }
            val uri = resolver.insert(collection, details) ?: return@withContext false

            resolver.openOutputStream(uri, "wt")?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    zos.setLevel(java.util.zip.Deflater.BEST_SPEED) // Fast zip
                    
                    // Backup DB files
                    val dbFiles = listOf(
                        context.getDatabasePath("reader_database"),
                        context.getDatabasePath("reader_database-shm"),
                        context.getDatabasePath("reader_database-wal")
                    )
                    
                    for (file in dbFiles) {
                        if (file.exists()) {
                            zipFile(file, "db/${file.name}", zos)
                        }
                    }

                    // Backup books
                    if (booksDir.exists()) {
                        booksDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                zipFile(file, "books/${file.name}", zos)
                            }
                        }
                    }

                    // Backup covers
                    if (coversDir.exists()) {
                        coversDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                zipFile(file, "covers/${file.name}", zos)
                            }
                        }
                    }
                }
            }

            // Update last backup time
            val newSettings = settings.copy(lastBackupTime = System.currentTimeMillis())
            readerPreferences.updateAllSettings(newSettings)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun restoreBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            
            // Temporary directory for extraction
            val tempDir = File(context.cacheDir, "restore_temp").apply {
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

            // Move extracted files to their final destination
            
            // 1. Move DB files
            val extractedDbDir = File(tempDir, "db")
            if (extractedDbDir.exists()) {
                val dbDir = context.getDatabasePath("reader_database").parentFile
                extractedDbDir.listFiles()?.forEach { file ->
                    val targetFile = File(dbDir, file.name)
                    if (targetFile.exists()) targetFile.delete()
                    file.copyTo(targetFile, overwrite = true)
                }
            }

            // 2. Move books
            val extractedBooksDir = File(tempDir, "books")
            if (extractedBooksDir.exists()) {
                val booksDir = File(context.filesDir, "books")
                if (booksDir.exists()) booksDir.deleteRecursively()
                booksDir.mkdirs()
                extractedBooksDir.listFiles()?.forEach { file ->
                    file.copyTo(File(booksDir, file.name), overwrite = true)
                }
            }

            // 3. Move covers
            val extractedCoversDir = File(tempDir, "covers")
            if (extractedCoversDir.exists()) {
                val coversDir = File(context.filesDir, "covers")
                if (coversDir.exists()) coversDir.deleteRecursively()
                coversDir.mkdirs()
                extractedCoversDir.listFiles()?.forEach { file ->
                    file.copyTo(File(coversDir, file.name), overwrite = true)
                }
            }
            
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
}
