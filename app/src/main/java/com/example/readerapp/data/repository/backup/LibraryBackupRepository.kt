package com.example.readerapp.data.repository.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.model.LibraryBackupPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LibraryBackupRepository(private val context: Context) {

    private val readerPreferences = ReaderPreferences(context)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun performBackup(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val settings = readerPreferences.readerSettings.first()
        val lastBackupTime = settings.lastBackupTime

        val dbFile = context.getDatabasePath("reader_database")
        val booksDir = File(context.filesDir, "books")
        val coversDir = File(context.filesDir, "covers")

        val lastModified = maxOf(
            dbFile.lastModified(), booksDir.lastModified(), coversDir.lastModified()
        )

        try {
            val database = (context.applicationContext as ReaderApplication).database

            // If not forced, check if changes were made since last backup
            if (!force && lastModified <= lastBackupTime) {
                return@withContext true // Skip backup, but consider it "successful"
            }

            // 1. Fetch all data synchronously in an atomic transaction
            val payload = database.withTransaction {
                val books = database.bookDao().getAllBooksSync()

                // Safety Guard: Never backup an empty library
                if (books.isEmpty()) {
                    return@withTransaction null
                }

                val bookmarks = database.bookmarkDao().getAllBookmarksSync()
                val shelves = database.shelfDao().getAllShelvesSync()
                val crossRefs = database.shelfDao().getAllShelfBookCrossRefsSync()
                val notes = database.noteDao().getAllNotesSync()

                val authors = database.bookDao().getAllAuthorsSync()
                val tags = database.bookDao().getAllTagsSync()
                val bookAuthorCrossRefs = database.bookDao().getAllBookAuthorCrossRefsSync()
                val bookTagCrossRefs = database.bookDao().getAllBookTagCrossRefsSync()

                LibraryBackupPayload(
                    version = 1,
                    books = books,
                    bookmarks = bookmarks,
                    shelves = shelves,
                    shelfBookCrossRefs = crossRefs,
                    notes = notes,
                    authors = authors,
                    tags = tags,
                    bookAuthorCrossRefs = bookAuthorCrossRefs,
                    bookTagCrossRefs = bookTagCrossRefs
                )
            } ?: return@withContext false

            val jsonString = json.encodeToString(payload)

            val backupFolderUriString = settings.backupFolderUri
            if (backupFolderUriString.isEmpty()) {
                return@withContext false
            }
            val backupFolderUri = backupFolderUriString.toUri()
            val backupFolder = DocumentFile.fromTreeUri(context, backupFolderUri)
            if (backupFolder == null || !backupFolder.canWrite()) {
                return@withContext false
            }

            val typeIndicator = if (force) "M" else "A"
            val timeStamp = SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date())
            val backupFileName = "${timeStamp}_${typeIndicator}.pine"

            val backupFile = backupFolder.createFile("application/octet-stream", backupFileName)
                ?: return@withContext false

            val resolver = context.contentResolver
            resolver.openOutputStream(backupFile.uri, "wt")?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    zos.setLevel(Deflater.BEST_SPEED) // Fast zip

                    // Backup JSON data
                    zos.putNextEntry(ZipEntry("data.json"))
                    zos.write(jsonString.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // Backup Settings
                    val settingsToBackup = settings.copy(
                        installedDictionaries = emptyList(), activeDictionaryId = ""
                    )
                    val settingsJsonString = json.encodeToString(settingsToBackup)
                    zos.putNextEntry(ZipEntry("settings.json"))
                    zos.write(settingsJsonString.toByteArray(Charsets.UTF_8))
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
            cleanupOldBackups(typeIndicator, backupFolderUriString)

            // Update last backup time
            val newSettings = settings.copy(lastBackupTime = System.currentTimeMillis())
            readerPreferences.updateAllSettings(newSettings)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun cleanupOldBackups(typeIndicator: String, backupFolderUriString: String) {
        try {
            if (backupFolderUriString.isEmpty()) return
            val backupFolderUri = backupFolderUriString.toUri()
            val backupFolder = DocumentFile.fromTreeUri(context, backupFolderUri) ?: return

            // List all files in the backup folder
            val files = backupFolder.listFiles()

            // Filter files by type indicator (.pine)
            val backupFiles = files.filter { it.name?.endsWith("_${typeIndicator}.pine") == true }
                .sortedByDescending { it.name } // Newest first based on timestamp in name

            // Delete oldest files beyond the limit of 3
            if (backupFiles.size > 3) {
                for (i in 3 until backupFiles.size) {
                    backupFiles[i].delete()
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

            val jsonString = FileInputStream(dataJsonFile).use {
                InputStreamReader(
                    it, Charsets.UTF_8
                ).readText()
            }
            val payload = json.decodeFromString<LibraryBackupPayload>(jsonString)

            // Restore Settings
            val settingsJsonFile = File(tempDir, "settings.json")
            if (settingsJsonFile.exists()) {
                try {
                    val settingsJsonString = FileInputStream(settingsJsonFile).use {
                        InputStreamReader(
                            it, Charsets.UTF_8
                        ).readText()
                    }
                    val restoredSettings = json.decodeFromString<ReaderSettings>(settingsJsonString)
                    // Preserve the current backup folder URI because permissions are tied to the current installation,
                    // and preserve dictionary settings (backed up separately)
                    val currentSettings = readerPreferences.readerSettings.first()
                    val settingsToApply = restoredSettings.copy(
                        backupFolderUri = currentSettings.backupFolderUri,
                        installedDictionaries = currentSettings.installedDictionaries,
                        activeDictionaryId = currentSettings.activeDictionaryId
                    )
                    readerPreferences.updateAllSettings(settingsToApply)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val database = (context.applicationContext as ReaderApplication).database

            // 1. Atomic Transaction: Clear all data and insert new data
            database.withTransaction {
                // Clear tables
                database.bookDao().deleteAll()
                database.bookmarkDao().deleteAll()
                database.shelfDao().deleteAllShelves()
                database.shelfDao().deleteAllShelfBookCrossRefs()
                database.noteDao().deleteAll()
                database.bookDao().deleteAllAuthors()
                database.bookDao().deleteAllTags()
                database.bookDao().deleteAllBookAuthorCrossRefs()
                database.bookDao().deleteAllBookTagCrossRefs()

                // Insert from payload
                database.bookDao().insertAll(payload.books)
                database.bookmarkDao().insertAll(payload.bookmarks)
                database.shelfDao().insertAllShelves(payload.shelves)
                database.shelfDao().insertAllShelfBookCrossRefs(payload.shelfBookCrossRefs)
                database.noteDao().insertAll(payload.notes)
                database.bookDao().insertAllAuthors(payload.authors)
                database.bookDao().insertAllTags(payload.tags)
                database.bookDao().insertAllBookAuthorCrossRefs(payload.bookAuthorCrossRefs)
                database.bookDao().insertAllBookTagCrossRefs(payload.bookTagCrossRefs)

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