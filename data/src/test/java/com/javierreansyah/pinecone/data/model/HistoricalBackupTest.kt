package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesData
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferencesData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class HistoricalBackupTest {
    private fun fixture(name: String): ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/compatibility/v1.0.0/$name")
    ).use { it.readBytes() }

    @Test
    fun taggedVersionOneDataStillDecodesWithoutDroppingSavedValues() {
        val snapshot = Json.decodeFromString<VaultSnapshot>(fixture("snapshot.json").decodeToString())
        val reader = Json.decodeFromString<ReaderPreferencesData>(fixture("reader_preferences.json").decodeToString())
        val library = Json.decodeFromString<LibraryPreferencesData>(fixture("library_preferences.json").decodeToString())
        assertEquals(reader.settings, snapshot.settings)
        assertTrue(reader.legacyMigrationComplete)
        assertEquals("personal", library.globalSpaceId)
        assertEquals(1, library.screens.size)
        val screen = library.screens.getValue("personal_library_books")
        assertEquals("Title", screen.sortType.name)
        assertEquals("List", screen.layoutMode.name)
        assertTrue(screen.isAscending)
        assertEquals(1.15, snapshot.settings.fontSize, 0.0)
        assertEquals("id", snapshot.settings.locale)
        assertEquals("Retained note", snapshot.library.notes.single().noteText)
        assertEquals("Baseline author", snapshot.library.credits.single().name)
        assertEquals(0.75, snapshot.library.books.single().furthestProgression, 0.0)
        assertEquals(snapshot.library.books.single().id, snapshot.library.renditions.single().bookId)
    }

    @Test
    fun historicalFixtureBytesHaveNotChanged() {
        val hashes = Json.decodeFromString<Map<String, String>>(fixture("checksums.json").decodeToString())
        hashes.forEach { (name, expected) ->
            val actual = MessageDigest.getInstance("SHA-256").digest(fixture(name))
                .joinToString("") { "%02x".format(it) }
            assertEquals(name, expected, actual)
        }
    }
}
