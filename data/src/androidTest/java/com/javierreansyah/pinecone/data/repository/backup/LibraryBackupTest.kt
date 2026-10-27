package com.javierreansyah.pinecone.data.repository.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.DataStoreFactory
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryStore
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity
import com.javierreansyah.pinecone.data.local.preferences.JsonPreferenceSerializer
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferencesData
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.PublicationAssetStore
import com.javierreansyah.pinecone.testing.syntheticPdf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Isolated library test application and a separate vault directory for every test. */
class LibraryBackupTest {
    @Test
    fun pathLikeBookIdentitiesCannotRestoreOrChangeExistingFiles() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val originalBytes = File(source.filePath).readBytes()
            assertSuccess(f.backups.createSnapshot(true))
            val settings = f.preferences.readerSettings.first()
            val saved = f.backups.listSnapshots().single()
            val snapshot = java.util.zip.ZipInputStream(
                requireNotNull(f.context.contentResolver.openInputStream(saved.uri))
            ).use { zip ->
                zip.nextEntry
                snapshotJson.decodeFromString<com.javierreansyah.pinecone.data.model.VaultSnapshot>(
                    zip.readBytes().decodeToString()
                )
            }
            val assetsBefore = f.assets.booksDirectory.list().orEmpty().toSet()
            val coversBefore = f.assets.coversDirectory.list().orEmpty().toSet()
            for (badId in listOf("../escaped", "..\\escaped", "/absolute", "C:drive", "bad\u0000id")) {
                val malicious = snapshot.copy(
                    library = snapshot.library.copy(
                        books = snapshot.library.books.map { it.copy(id = badId) },
                        renditions = snapshot.library.renditions.map { it.copy(bookId = badId) },
                        bookSpaceCrossRefs = snapshot.library.bookSpaceCrossRefs.map { it.copy(bookId = badId) },
                    ),
                    books = snapshot.books.map { it.copy(bookId = badId) },
                    renditions = snapshot.renditions.map { it.copy(bookId = badId) },
                )
                java.util.zip.ZipOutputStream(
                    requireNotNull(f.context.contentResolver.openOutputStream(saved.uri, "wt"))
                ).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry("snapshot.json"))
                    zip.write(snapshotJson.encodeToString(malicious).encodeToByteArray())
                    zip.closeEntry()
                }
                val result = f.backups.restoreSnapshot(saved.uri)
                assertTrue(result.toString(), result is BackupResult.Failure)
                assertEquals("Invalid book identity", (result as BackupResult.Failure).cause?.message)
                assertEquals(source, f.db.bookDao().getEntity(source.id))
                assertEquals(settings, f.preferences.readerSettings.first())
                assertArrayEquals(originalBytes, File(source.filePath).readBytes())
                assertEquals(assetsBefore, f.assets.booksDirectory.list().orEmpty().toSet())
                assertEquals(coversBefore, f.assets.coversDirectory.list().orEmpty().toSet())
            }
        }
    }

    @Test
    fun pathLikeDictionaryIdentitiesAreRejectedBeforeImportingObjects() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val settings = f.preferences.readerSettings.first()
            val snapshot = snapshotJson.decodeFromString<com.javierreansyah.pinecone.data.model.VaultSnapshot>(
                f.context.assets.open("compatibility/v1.0.0/snapshot.json").bufferedReader().use { it.readText() }
            )
            val malicious = snapshot.copy(dictionaries = snapshot.dictionaries.map {
                it.copy(dictionary = it.dictionary.copy(id = "../escape"))
            })
            val portable = f.file(".pinebackup")
            java.util.zip.ZipOutputStream(portable.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zip.write(snapshotJson.encodeToString(
                    com.javierreansyah.pinecone.data.model.PortableBackupManifest(snapshot = malicious)
                ).encodeToByteArray())
                zip.closeEntry()
            }
            val result = f.backups.importPortable(Uri.fromFile(portable))
            assertTrue(result.toString(), result is BackupResult.Failure)
            assertEquals("Invalid dictionary identity", (result as BackupResult.Failure).cause?.message)
            assertTrue(f.backups.listSnapshots().isEmpty())
            val dirs = requireNotNull(f.vault.open(false))
            assertTrue(listOf(dirs.books, dirs.covers, dirs.dictionaries).all { it.listFiles().isEmpty() })
            assertEquals(source, f.db.bookDao().getEntity(source.id))
            assertEquals(settings, f.preferences.readerSettings.first())
        }
    }

    @Test
    fun restoreTargetsCannotFollowLinksOutsideTheAssetDirectory() = runBlocking {
        fixture { f ->
            val outside = f.file(".outside").apply { writeText("keep") }
            val link = File(f.assets.booksDirectory, "link-${UUID.randomUUID()}")
            try {
                android.system.Os.symlink(outside.path, link.path)
                assertThrows(IllegalArgumentException::class.java) {
                    BackupArchiveIO.restoreTarget(f.assets.booksDirectory, link.name)
                }
                assertEquals("keep", outside.readText())
            } finally {
                link.delete()
            }
        }
    }

    @Test
    fun failedPortableImportRemovesPartialObjectsAndKeepsExistingBackup() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            assertSuccess(f.backups.createSnapshot(true))
            val settings = f.preferences.readerSettings.first()
            val snapshotsBefore = f.backups.listSnapshots()
            val dirs = requireNotNull(f.vault.open(false))
            val objectFolders = listOf(dirs.books, dirs.covers, dirs.dictionaries)
            val objectsBefore = objectFolders.map { folder -> folder.listFiles().map { it.name }.toSet() }
            val portable = f.file(".pinebackup")
            var corrupted = false
            java.util.zip.ZipInputStream(f.context.assets.open("compatibility/v1.0.0/portable.pinebackup")).use { input ->
                java.util.zip.ZipOutputStream(portable.outputStream()).use { output ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        var bytes = input.readBytes()
                        if (entry.name != "manifest.json" && !corrupted) {
                            bytes = bytes.copyOf(bytes.size - 1)
                            corrupted = true
                        }
                        output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        output.write(bytes)
                        output.closeEntry()
                    }
                }
            }
            assertTrue(corrupted)
            val result = f.backups.importPortable(Uri.fromFile(portable))
            assertTrue(result.toString(), result is BackupResult.Failure && result.reason == BackupFailure.MALFORMED_ARCHIVE)
            assertEquals(objectsBefore, objectFolders.map { folder -> folder.listFiles().map { it.name }.toSet() })
            assertEquals(snapshotsBefore, f.backups.listSnapshots())
            assertEquals(source, f.db.bookDao().getEntity(source.id))
            assertEquals(settings, f.preferences.readerSettings.first())
            assertTrue(File(source.filePath).isFile)
        }
    }

    @Test
    fun futureVaultFormatIsRejectedBeforeCreatingDirectoriesOrChangingContent() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val dirs = requireNotNull(f.vault.open(true))
            val format = requireNotNull(dirs.vault.findFile("format.json"))
            requireNotNull(f.context.contentResolver.openOutputStream(format.uri, "wt")).use {
                it.write("""{"format":"pinecone-backup-vault","formatVersion":3,"future":{}}""".encodeToByteArray())
            }
            assertTrue(dirs.covers.delete())
            val result = f.backups.createSnapshot(true)
            assertTrue(result.toString(), result is BackupResult.Failure && result.reason == BackupFailure.UNSUPPORTED_VERSION)
            assertNull(dirs.vault.findFile("objects")?.findFile("covers"))
            assertEquals(source, f.db.bookDao().getEntity(source.id))
            assertTrue(File(source.filePath).isFile)
        }
    }

    @Test
    fun versionOnePortableBackupRestoresAllPersistedContent() = runBlocking {
        fixture { f ->
            val portable = f.file(".pinebackup")
            f.context.assets.open("compatibility/v1.0.0/portable.pinebackup").use { input ->
                portable.outputStream().use { input.copyTo(it) }
            }
            assertSuccess(f.backups.importPortable(Uri.fromFile(portable)))
            assertSuccess(f.backups.restoreSnapshot(f.backups.listSnapshots().single().uri))
            assertBaselineRestored(f)
        }
    }

    @Test
    fun versionOneVaultRestoresAllPersistedContent() = runBlocking {
        fixture { f ->
            val root = requireNotNull(f.vault.open(true)).vault
            java.util.zip.ZipInputStream(f.context.assets.open("compatibility/v1.0.0/vault.zip")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val parts = entry.name.split('/')
                    var parent = root
                    parts.dropLast(1).forEach { name ->
                        parent = parent.findFile(name) ?: requireNotNull(parent.createDirectory(name))
                    }
                    val file = parent.findFile(parts.last()) ?: requireNotNull(parent.createFile("application/octet-stream", parts.last()))
                    requireNotNull(f.context.contentResolver.openOutputStream(file.uri, "wt")).use { zip.copyTo(it) }
                }
            }
            assertSuccess(f.backups.restoreSnapshot(f.backups.listSnapshots().single().uri))
            assertBaselineRestored(f)
        }
    }

    @Test
    fun futurePortableFormatCannotChangeTheCurrentLibraryOrSettings() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val settings = f.preferences.readerSettings.first()
            val portable = f.file(".pinebackup")
            java.util.zip.ZipOutputStream(portable.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zip.write("""{"format":"pinecone-portable-backup","formatVersion":3,"unknownFuturePayload":{}}""".encodeToByteArray())
                zip.closeEntry()
            }
            val result = f.backups.importPortable(Uri.fromFile(portable))
            assertTrue(result.toString(), result is BackupResult.Failure && result.reason == BackupFailure.UNSUPPORTED_VERSION)
            assertEquals(source, f.db.bookDao().getEntity(source.id))
            assertEquals(settings, f.preferences.readerSettings.first())
            assertTrue(File(source.filePath).isFile)
        }
    }

    private suspend fun assertBaselineRestored(f: Fixture) {
        val book = f.db.bookDao().getAllBooksSync().single()
        assertEquals("Baseline book", book.title)
        assertEquals(0.5, book.progression, 0.0)
        assertEquals(0.75, book.furthestProgression, 0.0)
        assertTrue(File(book.filePath).isFile)
        assertTrue(File(requireNotNull(book.coverPath)).isFile)
        assertEquals("Retained note", f.db.noteDao().getAllNotesSync().single().noteText)
        assertEquals("Baseline", f.db.bookmarkDao().getAllBookmarksSync().single().chapterTitle)
        assertEquals(book.id, f.db.shelfDao().getShelfRefsSync("favorites").single().bookId)
        assertEquals(book.filePath, f.db.publicationDao().rendition(book.id, "original")?.filePath)
        assertEquals(1.15, f.preferences.readerSettings.first().fontSize, 0.0)
        assertEquals("id", f.preferences.readerSettings.first().locale)
        assertEquals("baseline", f.preferences.readerSettings.first().activeDictionaryId)
        assertEquals("A retained definition", f.dictionaries.withDictionary("baseline") {
            it.getDefinitions("pinecone").single().definition
        })
    }

    @Test
    fun conflictingReferencesToOneObjectCannotRestoreOverTheCurrentLibrary() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            assertSuccess(f.backups.createSnapshot(true))
            val saved = f.backups.listSnapshots().single()
            val snapshot = java.util.zip.ZipInputStream(
                requireNotNull(f.context.contentResolver.openInputStream(saved.uri))
            ).use { zip ->
                zip.nextEntry
                snapshotJson.decodeFromString<com.javierreansyah.pinecone.data.model.VaultSnapshot>(
                    zip.readBytes().decodeToString()
                )
            }
            val conflicting = snapshot.copy(renditions = snapshot.renditions.map {
                it.copy(objectInfo = it.objectInfo.copy(size = it.objectInfo.size + 1))
            })
            java.util.zip.ZipOutputStream(
                requireNotNull(
                    f.context.contentResolver.openOutputStream(
                        saved.uri,
                        "wt"
                    )
                )
            ).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("snapshot.json"))
                zip.write(
                    snapshotJson.encodeToString(conflicting).encodeToByteArray()
                )
                zip.closeEntry()
            }
            val result = f.backups.restoreSnapshot(saved.uri)
            assertTrue(result is BackupResult.Failure)
            assertEquals("Conflicting backup object references", (result as BackupResult.Failure).cause?.message)
            assertEquals(source, f.db.bookDao().getEntity(source.id))
            assertTrue(File(source.filePath).isFile)
        }
    }

    @Test
    fun originalRenditionSharesOneObjectAndIsCountedOnce() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val progress = mutableListOf<BackupProgress>()
            val first = f.backups.createSnapshot(true) { progress += it }
            assertSuccess(first)
            val expectedProgress = (0..3).map { BackupProgress(it, 3) }
            assertEquals(expectedProgress, progress)
            assertEquals(0L, (first as BackupResult.Success).bytesReused)
            progress.clear()
            val second = f.backups.createSnapshot(true) { progress += it }
            assertSuccess(second)
            assertEquals(expectedProgress, progress)
            assertEquals(
                File(source.filePath).length() + File(requireNotNull(source.coverPath)).length(),
                (second as BackupResult.Success).bytesReused
            )
            val dirs = requireNotNull(f.vault.open(false))
            assertEquals(1, dirs.books.listFiles().size)
        }
    }

    @Test
    fun emptyLibraryProgressCompletesWithTheSnapshotFile() = runBlocking {
        fixture { f ->
            val progress = mutableListOf<BackupProgress>()
            assertSuccess(f.backups.createSnapshot(true) { progress += it })
            assertEquals(listOf(BackupProgress(totalFiles = 1), BackupProgress(1, 1)), progress)
            progress.clear()
            assertSuccess(
                f.backups.restoreSnapshot(
                    f.backups.listSnapshots().single().uri
                ) { progress += it })
            assertEquals(listOf(BackupProgress(totalFiles = 1), BackupProgress(1, 1)), progress)
        }
    }

    @Test
    fun restoreProgressCountsReadModeSeparatelyFromTheOriginalFile() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val original = requireNotNull(f.db.publicationDao().rendition(source.id, "original"))
            f.db.publicationDao().putRenditions(listOf(original.copy(key = "read-mode")))
            assertSuccess(f.backups.createSnapshot(true))
            val progress = mutableListOf<BackupProgress>()
            assertSuccess(
                f.backups.restoreSnapshot(
                    f.backups.listSnapshots().single().uri
                ) { progress += it })
            assertEquals((0..4).map { BackupProgress(it, 4) }, progress)
        }
    }

    @Test
    fun missingBookNeverReportsACompleteBackup() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            assertTrue(File(source.filePath).delete())
            val progress = mutableListOf<BackupProgress>()
            assertTrue(f.backups.createSnapshot(true) { progress += it } is BackupResult.Failure)
            assertFalse(progress.any { it.fraction == 1f })
        }
    }

    @Test
    fun portableBookBackupRoundTripsAndCorruptRestorePreservesTheCurrentLibrary() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val bytes = File(source.filePath).readBytes()
            val cover = File(requireNotNull(source.coverPath)).readBytes()
            val shelf = f.library.createShelfWithBooks("_all_", "Saved shelf", listOf(source.id))
            val bookmark = BookmarkEntity(1, source.id, LOCATOR, createdAt = 10)
            val note = NoteEntity(1, source.id, LOCATOR, noteText = "Saved note", createdAt = 20)
            f.db.bookmarkDao().insert(bookmark)
            f.db.noteDao().insert(note)
            f.preferences.updateSettings { it.copy(fontSize = 1.15) }
            assertSuccess(f.backups.createSnapshot(true))
            val snapshot = f.backups.listSnapshots().single()
            val portable = f.file(".pinebackup")
            assertSuccess(f.backups.exportSnapshot(snapshot.uri, Uri.fromFile(portable)))

            f.library.deleteBook(source.id)
            assertTrue(requireNotNull(f.vault.open(false)).vault.delete())
            f.preferences.updateSettings { it.copy(fontSize = 1.7) }
            assertSuccess(f.backups.importPortable(Uri.fromFile(portable)))
            val imported = f.backups.listSnapshots().single()
            val restoreProgress = mutableListOf<BackupProgress>()
            assertSuccess(f.backups.restoreSnapshot(imported.uri) { restoreProgress += it })
            assertEquals((0..3).map { BackupProgress(it, 3) }, restoreProgress)

            val restored = requireNotNull(f.db.bookDao().getEntity(source.id))
            assertEquals(
                source.copy(filePath = restored.filePath, coverPath = restored.coverPath),
                restored
            )
            assertArrayEquals(bytes, File(restored.filePath).readBytes())
            assertArrayEquals(cover, File(requireNotNull(restored.coverPath)).readBytes())
            assertEquals(listOf(bookmark), f.db.bookmarkDao().getAllBookmarksSync())
            assertEquals(listOf(note), f.db.noteDao().getAllNotesSync())
            assertEquals(
                listOf(source.id),
                f.db.shelfDao().getShelfRefsSync(shelf).map { it.bookId })
            assertEquals(1.15, f.preferences.readerSettings.first().fontSize, 0.0)
            assertEquals(
                restored.filePath,
                f.db.publicationDao().rendition(source.id, "original")?.filePath
            )

            val objectFile = requireNotNull(f.vault.open(false)).books.listFiles().single()
            (f.context.contentResolver.openOutputStream(objectFile.uri, "wt") ?: return@fixture)
                .use { it.write(byteArrayOf(0)) }
            val filesBeforeFailure = f.assets.booksDirectory.listFiles().orEmpty().map { it.name }.toSet()
            restoreProgress.clear()
            assertTrue(f.backups.restoreSnapshot(imported.uri) { restoreProgress += it } is BackupResult.Failure)
            assertTrue(restoreProgress.isNotEmpty())
            assertFalse(restoreProgress.any { it.fraction == 1f })
            assertEquals(restored, f.db.bookDao().getEntity(source.id))
            assertEquals(listOf(note), f.db.noteDao().getAllNotesSync())
            assertArrayEquals(bytes, File(restored.filePath).readBytes())
            assertEquals(filesBeforeFailure, f.assets.booksDirectory.listFiles().orEmpty().map { it.name }.toSet())
        }
    }

    @Test
    fun unreadableSnapshotPreventsPruningItsOnlyRemainingObjects() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val bytes = File(source.filePath).readBytes()
            assertSuccess(f.backups.createSnapshot(true))
            val saved = f.backups.listSnapshots().single()
            val metadata =
                (f.context.contentResolver.openInputStream(saved.uri)
                    ?: return@fixture).use { it.readBytes() }
            f.library.deleteBook(source.id)
            (f.context.contentResolver.openOutputStream(saved.uri, "wt") ?: return@fixture)
                .use { it.write(byteArrayOf(0)) }

            val result = f.backups.createSnapshot(true)
            assertTrue(
                result.toString(),
                result is BackupResult.Success && result.warnings.isNotEmpty()
            )
            assertTrue(requireNotNull(f.vault.open(false)).books.listFiles().isNotEmpty())
            assertTrue(requireNotNull(f.vault.open(false)).covers.listFiles().isNotEmpty())

            (f.context.contentResolver.openOutputStream(saved.uri, "wt")
                ?: return@fixture).use { it.write(metadata) }
            assertSuccess(f.backups.restoreSnapshot(saved.uri))
            val restored = requireNotNull(f.db.bookDao().getEntity(source.id))
            assertArrayEquals(bytes, File(restored.filePath).readBytes())
        }
    }

    @Test
    fun replacingACoverKeepsItsBackupLeaseUntilReleased() = runBlocking {
        fixture { f ->
            val source = f.seedBook()
            val old = File(requireNotNull(source.coverPath))
            f.assets.lease(old).use {
                f.library.updateBookMetadata(
                    source.id,
                    source.title,
                    null,
                    Uri.fromFile(old),
                    emptyList(),
                    emptyList()
                )
                assertTrue(old.isFile)
                f.library.cleanupOrphanedFiles()
                assertTrue(old.isFile)
            }
            f.library.cleanupOrphanedFiles()
            assertFalse(old.exists())
            assertTrue(File(requireNotNull(f.db.bookDao().getEntity(source.id)?.coverPath)).isFile)
        }
    }

    private class Fixture {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val files = mutableSetOf<File>()
        private val settings =
            File(context.cacheDir, "audit-${UUID.randomUUID()}.json").also { files += it }
        val preferences = ReaderPreferences(
            DataStoreFactory.create(
                serializer = JsonPreferenceSerializer(
                    ReaderPreferencesData.serializer(),
                    ReaderPreferencesData()
                ),
                scope = scope, produceFile = { settings },
            )
        )
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK).build()
        val assets = PublicationAssetStore(context)
        val library = LibraryRepository(
            context,
            db,
            db.bookDao(),
            db.shelfDao(),
            db.spaceDao(),
            assets,
            Dispatchers.IO
        )
        val vault = BackupVault(context, preferences)
        val dictionaries = DictionaryStore(context, Dispatchers.IO)
        val backups = BackupRepository(
            context,
            preferences,
            db,
            library,
            Dispatchers.IO,
            dictionaries,
            DictionaryRestoreJournal(context),
            vault,
            assets
        )
        private lateinit var root: DocumentFile

        fun file(extension: String, directory: File = context.cacheDir): File =
            File.createTempFile("audit-", extension, directory).also { files += it }

        suspend fun initialize() {
            val tree =
                DocumentsContract.buildTreeDocumentUri("${context.packageName}.backupdocs", "root")
            context.grantUriPermission(
                context.packageName, tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            root = requireNotNull(
                DocumentFile.fromTreeUri(context, tree)
                    ?.createDirectory("audit-${UUID.randomUUID()}")
            )
            val selectedTree = DocumentsContract.buildTreeDocumentUri(
                tree.authority,
                DocumentsContract.getDocumentId(root.uri)
            )
            context.grantUriPermission(
                context.packageName, selectedTree, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            preferences.updateAllSettings(ReaderSettings(backupFolderUri = selectedTree.toString()))
        }

        suspend fun seedBook(): BookEntity {
            val source = file(".pdf", assets.booksDirectory).apply { writeBytes(syntheticPdf()) }
            val cover = file(".png", assets.coversDirectory)
            val bitmap =
                android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                cover.outputStream()
                    .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            val hash = source.inputStream().use { BackupArchiveIO.copyAndDigest(it).sha256 }
            val book = BookEntity(
                hash,
                "Saved book",
                cover.path,
                source.path,
                "application/pdf",
                progression = 0.5,
                lastLocatorJson = LOCATOR,
                furthestProgression = 0.5,
                furthestLocatorJson = LOCATOR
            )
            db.bookDao().insert(book)
            db.publicationDao().putRenditions(
                listOf(
                    PublicationRenditionEntity(
                        hash,
                        "original",
                        source.path,
                        "application/pdf",
                        "PDF",
                        hash,
                        lastLocatorJson = LOCATOR,
                        progression = 0.5,
                        furthestLocatorJson = LOCATOR,
                        furthestProgression = 0.5
                    )
                )
            )
            library.setBooksInSpace("_all_", listOf(hash), true)
            return book
        }

        suspend fun close() {
            preferences.readerSettings.first().installedDictionaries.forEach { dictionaries.delete(it.id) }
            db.bookDao().getAllBooksSync().forEach {
                files += File(it.filePath)
                it.coverPath?.let { path -> files += File(path) }
            }
            db.close()
            scope.coroutineContext.job.cancelAndJoin()
            if (::root.isInitialized) root.delete()
            files.forEach { it.delete() }
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            fixture.initialize()
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private fun assertSuccess(result: BackupResult) {
        val detail = (result as? BackupResult.Failure)?.cause?.stackTraceToString().orEmpty()
        assertTrue("$result\n$detail", result.isSuccess)
    }

    private companion object {
        val snapshotJson = kotlinx.serialization.json.Json { encodeDefaults = true }
        const val LOCATOR =
            """{"href":"book.pdf","type":"application/pdf","locations":{"fragments":["page=1"],"totalProgression":0.5}}"""
    }
}
