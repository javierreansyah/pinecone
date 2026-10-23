package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity
import com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity
import com.javierreansyah.pinecone.data.local.preferences.FormatPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.local.preferences.withReadingPreferencesFrom
import com.javierreansyah.pinecone.data.model.LibraryBackupPayload
import com.javierreansyah.pinecone.data.model.ShelfBackupRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PublicationMetadataTest {
    @Test
    fun richMetadataRoundTripsWithoutLosingUnknownFieldsOrIssueLabels() {
        val value = PublicationMetadata(
            languages = listOf("en", "ja"),
            identifiers = mapOf("isbn" to "123"),
            volume = "2026",
            issue = "½ special",
            extensions = JsonObject(mapOf("custom:credit" to JsonPrimitive("preserved")))
        )
        assertEquals(value, PublicationMetadata.decode(value.encode()))
    }

    @Test
    fun backupPreservesShelvesRenditionsContributorsAndSourceMapsWithoutSeriesMetadata() {
        val payload = LibraryBackupPayload(
            books = emptyList(),
            bookmarks = emptyList(),
            shelves = listOf(ShelfBackupRecord("favorites", "_all_", "Favorites", 123L)),
            shelfBookCrossRefs = emptyList(),
            notes = emptyList(),
            renditions = listOf(
                PublicationRenditionEntity(
                    "pdf",
                    "original",
                    "/pdf",
                    "application/pdf",
                    "PDF",
                    "a".repeat(64),
                    lastLocatorJson = "pdf-location"
                ),
                PublicationRenditionEntity(
                    "pdf",
                    "read-mode",
                    "/epub",
                    "application/epub+zip",
                    "EPUB",
                    "b".repeat(64),
                    converterVersion = 1,
                    sourceMapJson = "[{\"page\":1,\"href\":\"text/page-1.xhtml\"}]",
                    lastLocatorJson = "epub-location"
                ),
            ),
            credits = listOf(
                PublicationCreditEntity("comic", "writer", 0, "Same name"),
                PublicationCreditEntity("comic", "artist", 0, "Same name")
            ),
        )
        assertEquals(
            payload,
            Json.decodeFromString<LibraryBackupPayload>(Json.encodeToString(payload))
        )
        assertEquals(2, payload.version)
        val json = Json { encodeDefaults = true }
        val encoded = json.parseToJsonElement(json.encodeToString(payload)) as JsonObject
        assertFalse("collections" in encoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidAudioPreferences() {
        FormatPreferences(audioSpeed = Double.NaN)
    }

    @Test
    fun publicationOverridesCannotReplaceGlobalBackupOrDictionarySettings() {
        val global = ReaderSettings(
            backupFolderUri = "content://global",
            activeDictionaryId = "dictionary",
            locale = "id"
        )
        val local = ReaderSettings(
            fontSize = 1.5,
            backupFolderUri = "content://wrong",
            activeDictionaryId = "wrong",
            locale = "en"
        )
        val merged = global.withReadingPreferencesFrom(local)
        assertEquals(1.5, merged.fontSize, 0.0)
        assertEquals("content://global", merged.backupFolderUri)
        assertEquals("dictionary", merged.activeDictionaryId)
        assertEquals("id", merged.locale)
    }
}
