package com.example.readerapp.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import androidx.room.withTransaction
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.model.BackupPayload
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRepository(private val context: Context) {

    private val readerPreferences = ReaderPreferences(context)
    private val gson = Gson()

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

        try {
            val database = (context.applicationContext as ReaderApplication).database
            
            // 1. Fetch all data synchronously
            val books = database.bookDao().getAllBooksSync()
            
            // Safety Guard: Never backup an empty library
            if (books.isEmpty()) {
                return@withContext false
            }

            // If not forced, check if changes were made since last backup
            if (!force && lastModified <= lastBackupTime) {
                return@withContext true // Skip backup, but consider it "successful"
            }

            val bookmarks = database.bookmarkDao().getAllBookmarksSync()
            val shelves = database.shelfDao().getAllShelvesSync()
            val crossRefs = database.shelfDao().getAllShelfBookCrossRefsSync()
            val notes = database.noteDao().getAllNotesSync()
            
            val payload = BackupPayload(
                version = 1,
                books = books,
                bookmarks = bookmarks,
                shelves = shelves,
                shelfBookCrossRefs = crossRefs,
                notes = notes
            )
            
            val jsonString = gson.toJson(payload)
            
            // We use MediaStore to save the backup in the Documents folder
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            
            val typeIndicator = if (force) "M" else "A"
            val timeStamp = SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date())
            val backupFileName = "${timeStamp}_${typeIndicator}.pine"

            val details = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Pinecone/")
            }
            val uri = resolver.insert(collection, details) ?: return@withContext false

            resolver.openOutputStream(uri, "wt")?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    zos.setLevel(java.util.zip.Deflater.BEST_SPEED) // Fast zip
                    
                    // Backup JSON data
                    zos.putNextEntry(ZipEntry("data.json"))
                    zos.write(jsonString.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

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

            // Cleanup old backups of the SAME type
            cleanupOldBackups(typeIndicator)

            // Update last backup time
            val newSettings = settings.copy(lastBackupTime = System.currentTimeMillis())
            readerPreferences.updateAllSettings(newSettings)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun cleanupOldBackups(typeIndicator: String) {
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("%_${typeIndicator}.pine", Environment.DIRECTORY_DOCUMENTS + "/Pinecone/")
            // Sort by DISPLAY_NAME descending so the newest files are first
            val sortOrder = "${MediaStore.MediaColumns.DISPLAY_NAME} DESC"

            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME), selection, selectionArgs, sortOrder)?.use { cursor ->
                var count = 0
                while (cursor.moveToNext()) {
                    count++
                    if (count > 3) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val deleteUri = Uri.withAppendedPath(collection, id.toString())
                        try {
                            resolver.delete(deleteUri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            
            val dataJsonFile = File(tempDir, "data.json")
            if (!dataJsonFile.exists()) {
                // If it's the old SQLite backup format, this will fail gracefully.
                tempDir.deleteRecursively()
                return@withContext false
            }
            
            val jsonString = FileInputStream(dataJsonFile).use { InputStreamReader(it, Charsets.UTF_8).readText() }
            val payload = gson.fromJson(jsonString, BackupPayload::class.java)

            val database = (context.applicationContext as ReaderApplication).database
            
            // 1. Atomic Transaction: Clear all data and insert new data
            database.withTransaction {
                // Clear tables
                database.bookDao().deleteAll()
                database.bookmarkDao().deleteAll()
                database.shelfDao().deleteAllShelves()
                database.shelfDao().deleteAllShelfBookCrossRefs()
                database.noteDao().deleteAll()
                
                // Insert from payload
                database.bookDao().insertAll(payload.books)
                database.bookmarkDao().insertAll(payload.bookmarks)
                database.shelfDao().insertAllShelves(payload.shelves)
                database.shelfDao().insertAllShelfBookCrossRefs(payload.shelfBookCrossRefs)
                database.noteDao().insertAll(payload.notes)
                
                // 2. Move files while inside the transaction lock. 
                // This guarantees that Room will NOT emit the new database state to the UI 
                // until all physical files are safely in place, eliminating the Coil caching race condition.
                
                // Move books
                val extractedBooksDir = File(tempDir, "books")
                if (extractedBooksDir.exists()) {
                    val booksDir = File(context.filesDir, "books")
                    if (booksDir.exists()) booksDir.deleteRecursively()
                    booksDir.mkdirs()
                    extractedBooksDir.listFiles()?.forEach { file ->
                        file.copyTo(File(booksDir, file.name), overwrite = true)
                    }
                }

                // Move covers
                val extractedCoversDir = File(tempDir, "covers")
                if (extractedCoversDir.exists()) {
                    val coversDir = File(context.filesDir, "covers")
                    if (coversDir.exists()) coversDir.deleteRecursively()
                    coversDir.mkdirs()
                    extractedCoversDir.listFiles()?.forEach { file ->
                        file.copyTo(File(coversDir, file.name), overwrite = true)
                    }
                }
            }
            
            
            tempDir.deleteRecursively()

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            // Even if we fail halfway through file copy, the database transaction was either completely successful
            // or completely rolled back.
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
