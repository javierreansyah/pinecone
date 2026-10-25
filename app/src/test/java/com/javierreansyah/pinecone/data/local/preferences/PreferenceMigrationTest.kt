package com.javierreansyah.pinecone.data.local.preferences

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.SortType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class PreferenceMigrationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun populatedLegacyReaderSettingsMigrateOnceAndPreserveBackupFields() = runTest {
        val legacy = mutablePreferencesOf(
            LegacyReaderSettings.FONT_SIZE to 1.234,
            LegacyReaderSettings.LAST_BACKUP_REVISION to 42L,
            LegacyReaderSettings.LAST_BACKUP_TIME to 123L,
            LegacyReaderSettings.BACKUP_FOLDER_URI to "content://backup/tree/books",
            LegacyReaderSettings.CUSTOM_THEMES to setOf("Warm|#ffffff|#000000|Dark", "broken"),
            LegacyReaderSettings.INSTALLED_DICTIONARIES to setOf("dict|English|99"),
            LegacyReaderSettings.FONT_WEIGHTS to "Source Serif 4:1.4,bad,Other:NaN",
            LegacyReaderSettings.FORMAT_PREFERENCES to "broken json",
        )
        val migration = ReaderPreferencesMigration { legacy }
        val store = DataStoreFactory.create(
            storage = storage("settings.json"),
            migrations = listOf(migration),
            scope = backgroundScope,
        )
        val preferences = ReaderPreferences(store)
        val settings = preferences.readerSettings.first()
        assertEquals(1.23, settings.fontSize, 0.0)
        assertEquals(42L, settings.lastBackupRevision)
        assertEquals(123L, settings.lastBackupTime)
        assertEquals("content://backup/tree/books", settings.backupFolderUri)
        assertEquals("Warm", settings.customThemes.single().name)
        assertEquals("dict", settings.installedDictionaries.single().id)
        assertEquals(mapOf("Source Serif 4" to 1.4), settings.fontWeights)
        assertEquals(FormatPreferences(), settings.formatPreferences)
        assertFalse(migration.shouldMigrate(store.data.first()))
        assertEquals("broken json", legacy[LegacyReaderSettings.FORMAT_PREFERENCES])
    }

    @Test
    fun concurrentEditsPreserveUnrelatedFieldsAndDelimiterContainingRecords() = runTest {
        val store = DataStoreFactory.create(
            storage = storage("concurrent.json"),
            scope = backgroundScope,
        )
        val preferences = ReaderPreferences(store)
        val start = CompletableDeferred<Unit>()
        listOf(
            async { start.await(); preferences.markBackup(123L, 7L) },
            async { start.await(); preferences.updateSettings { it.copy(fontSize = 1.5) } },
            async {
                start.await()
                preferences.updateSettings {
                    it.copy(installedDictionaries = listOf(InstalledDictionary("id", "A|B,C:D", 3)))
                }
            },
        ).also { start.complete(Unit) }.awaitAll()
        val actual = preferences.readerSettings.first()
        assertEquals(123L, actual.lastBackupTime)
        assertEquals(7L, actual.lastBackupRevision)
        assertEquals(1.5, actual.fontSize, 0.0)
        assertEquals("A|B,C:D", actual.installedDictionaries.single().name)
        assertEquals(actual, PreferenceJson.decodeFromString<ReaderSettings>(PreferenceJson.encodeToString(actual)))
    }

    @Test
    fun migrationCanBeRetriedAfterAnInterruptedLegacyRead() = runTest {
        var reads = 0
        val migration = ReaderPreferencesMigration {
            if (reads++ == 0) throw IOException("interrupted")
            mutablePreferencesOf(LegacyReaderSettings.THEME_MODE to "Dark")
        }
        val current = ReaderPreferencesData()
        assertTrue(migration.shouldMigrate(current))
        try {
            migration.migrate(current)
            throw AssertionError("Expected read failure")
        } catch (_: IOException) {
            assertTrue(migration.shouldMigrate(current))
        }
        val migrated = migration.migrate(current)
        assertEquals("Dark", migrated.settings.themeMode)
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun libraryMigrationPreservesSpaceKeysAndScreenSpecificDefaults() {
        val migrated = LegacyLibraryPreferences.read(
            mapOf(
                "global_space" to "space",
                "space_shelf_detail_layout" to "List",
                "library_books_sort" to "unknown",
                "library_shelves_show" to "Both",
                "archive_formats" to emptySet<String>(),
            ),
        )
        assertEquals("space", migrated.globalSpaceId)
        assertEquals(LayoutMode.List, migrated.screens.getValue("space_shelf_detail").layoutMode)
        assertEquals(SortType.Custom, migrated.screens.getValue("space_shelf_detail").sortType)
        assertTrue(migrated.screens.getValue("space_shelf_detail").isAscending)
        assertEquals(SortType.LastRead, migrated.screens.getValue("library_books").sortType)
        assertEquals(LayoutMode.BigList, migrated.screens.getValue("library_shelves").layoutMode)
        assertTrue(migrated.screens.getValue("archive").selectedFormats.isEmpty())
        assertEquals(ReaderSettings(), LegacyReaderSettings.read(mutablePreferencesOf()))
    }
    @Test
    fun libraryEditsPreserveOtherScreensAndFollowTheSpacesSetting() = runTest {
        val reader = ReaderPreferences(DataStoreFactory.create(
            storage = storage("reader.json"), scope = backgroundScope,
        ))
        val store = DataStoreFactory.create(
            storage = storage("library.json", JsonPreferenceSerializer(
                LibraryPreferencesData.serializer(), LibraryPreferencesData(),
            )),
            scope = backgroundScope,
        )
        val preferences = LibraryPreferencesManager(store, reader)
        preferences.setGlobalSpace("work")
        listOf(
            async { preferences.updatePreferences("work", "library_books") { it.copy(layoutMode = LayoutMode.List) } },
            async { preferences.updatePreferences("work", "library_books") { it.copy(sortType = SortType.Author) } },
            async { preferences.updatePreferences(null, "shelf_detail") { it.copy(layoutMode = LayoutMode.BigGrid) } },
        ).awaitAll()
        val books = preferences.preferences("work", "library_books").first()
        assertEquals(LayoutMode.List, books.layoutMode)
        assertEquals(SortType.Author, books.sortType)
        assertEquals(LayoutMode.BigGrid, preferences.preferences(null, "shelf_detail").first().layoutMode)
        assertEquals("work", preferences.getEffectiveSpaceFlow().first())
        reader.updateSettings { it.copy(spacesEnabled = false) }
        assertEquals("_all_", preferences.getEffectiveSpaceFlow().first())
    }

    @Test
    fun backupSettingsJsonRemainsIndependentOfTheStorageEnvelope() = runTest {
        val preferences = ReaderPreferences(DataStoreFactory.create(
            storage = storage("backup.json"), scope = backgroundScope,
        ))
        val original = ReaderSettings(
            themeMode = "Dark", backupFolderUri = "content://vault",
            customThemes = listOf(CustomPineconeTheme("A|B,C:D", "#ffffff", "#000000")),
            installedDictionaries = listOf(InstalledDictionary("dictionary", "Name|with,delimiters", 2)),
            lastBackupTime = 123L, lastBackupRevision = 7L,
        )
        val portableJson = kotlinx.serialization.json.Json.encodeToString(original)
        assertFalse(portableJson.contains("legacyMigrationComplete"))
        preferences.updateAllSettings(kotlinx.serialization.json.Json.decodeFromString(portableJson))
        assertEquals(original, preferences.readerSettings.first())
        preferences.updateSettings { it.copy(themeMode = "Light") }
        preferences.updateAllSettings(kotlinx.serialization.json.Json.decodeFromString(portableJson))
        assertEquals(original, preferences.readerSettings.first())
    }

    // File.renameTo cannot replace existing files on Windows; Okio uses the platform atomic move.
    private fun storage(name: String) = storage(
        name, JsonPreferenceSerializer(ReaderPreferencesData.serializer(), ReaderPreferencesData()),
    )

    private fun <T> storage(name: String, delegate: Serializer<T>) = OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = object : OkioSerializer<T> {
            override val defaultValue = delegate.defaultValue
            override suspend fun readFrom(source: BufferedSource) = delegate.readFrom(source.inputStream())
            override suspend fun writeTo(t: T, sink: BufferedSink) =
                delegate.writeTo(t, sink.outputStream())
        },
        producePath = { temporary.root.resolve(name).absolutePath.toPath() },
    )

}
