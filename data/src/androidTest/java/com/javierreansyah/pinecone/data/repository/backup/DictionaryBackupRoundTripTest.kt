package com.javierreansyah.pinecone.data.repository.backup

import android.provider.DocumentsContract
import androidx.datastore.core.DataStoreFactory
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryEntry
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryStore
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import com.javierreansyah.pinecone.data.local.preferences.JsonPreferenceSerializer
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferencesData
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.PublicationAssetStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/** Uses the library test APK's isolated application, never the installed reader's database/settings. */
class DictionaryBackupRoundTripTest {
    @Test
    fun snapshotWaitsForLookupAndRestoreReopensTheSavedDictionary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val tree =
            DocumentsContract.buildTreeDocumentUri("${context.packageName}.backupdocs", "root")
        context.grantUriPermission(
            context.packageName, tree,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settingsFile = File(context.cacheDir, "backup-preferences-$id.json")
        val preferences = ReaderPreferences(
            DataStoreFactory.create(
                serializer = JsonPreferenceSerializer(
                    ReaderPreferencesData.serializer(),
                    ReaderPreferencesData()
                ),
                scope = scope,
                produceFile = { settingsFile },
            )
        )
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK).build()
        val dictionaries = DictionaryStore(context, Dispatchers.IO)
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
        val backups = BackupRepository(
            context, preferences, db, library, Dispatchers.IO, dictionaries,
            DictionaryRestoreJournal(context), BackupVault(context, preferences), assets
        )
        try {
            preferences.updateAllSettings(
                ReaderSettings(
                    backupFolderUri = tree.toString(), fontSize = 1.15,
                    installedDictionaries = listOf(InstalledDictionary(id, "Saved dictionary", 1)),
                    activeDictionaryId = id,
                )
            )
            dictionaries.create(id) {
                it.dictionaryDao()
                    .insertAll(listOf(DictionaryEntry(word = "word", definition = "saved")))
            }
            val acquired = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val lookup = async {
                dictionaries.withDictionary(id) { dao ->
                    acquired.complete(Unit)
                    release.await()
                    assertEquals("saved", dao.getDefinitions("word").single().definition)
                }
            }
            acquired.await()
            val snapshot = async { backups.createSnapshot(manual = true) }
            try {
                delay(100.milliseconds)
                assertFalse(
                    if (snapshot.isCompleted) snapshot.await()
                        .toString() else "Snapshot must wait for lookup", snapshot.isCompleted
                )
            } finally {
                release.complete(Unit)
            }
            lookup.await()
            val result = snapshot.await()
            assertTrue(result.toString(), result is BackupResult.Success)
            val saved = backups.listSnapshots().single()
            dictionaries.delete(id)
            dictionaries.create(id) {
                it.dictionaryDao()
                    .insertAll(listOf(DictionaryEntry(word = "word", definition = "changed")))
            }
            preferences.updateSettings { it.copy(fontSize = 1.7) }

            val restored = backups.restoreSnapshot(saved.uri)
            assertTrue(restored.toString(), restored is BackupResult.Success)
            assertEquals(
                "saved",
                dictionaries.withDictionary(id) { it.getDefinitions("word").single().definition })
            assertEquals(1.15, preferences.readerSettings.first().fontSize, 0.0)
            assertEquals(id, preferences.readerSettings.first().activeDictionaryId)
            assertFalse(File(context.filesDir, "backup_restore_journal.json").exists())
        } finally {
            dictionaries.delete(id)
            db.close()
            scope.coroutineContext.job.cancelAndJoin()
            DocumentFile.fromTreeUri(context, tree)?.findFile("PineconeBackup")?.delete()
            settingsFile.delete()
        }
    }
}
