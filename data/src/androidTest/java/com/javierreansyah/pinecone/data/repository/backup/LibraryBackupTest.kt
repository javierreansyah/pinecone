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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Isolated library test application and a separate vault directory for every test. */
class LibraryBackupTest {
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
            assertSuccess(f.backups.restoreSnapshot(imported.uri))

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
            assertTrue(f.backups.restoreSnapshot(imported.uri) is BackupResult.Failure)
            assertEquals(restored, f.db.bookDao().getEntity(source.id))
            assertEquals(listOf(note), f.db.noteDao().getAllNotesSync())
            assertArrayEquals(bytes, File(restored.filePath).readBytes())
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
        val context: Context? = InstrumentationRegistry.getInstrumentation().targetContext
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
        val backups = BackupRepository(
            context,
            preferences,
            db,
            library,
            Dispatchers.IO,
            DictionaryStore(context, Dispatchers.IO),
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
        const val LOCATOR =
            """{"href":"book.pdf","type":"application/pdf","locations":{"fragments":["page=1"],"totalProgression":0.5}}"""
    }
}
