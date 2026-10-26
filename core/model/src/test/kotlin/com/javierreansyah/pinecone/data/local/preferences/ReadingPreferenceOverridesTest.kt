package com.javierreansyah.pinecone.data.local.preferences

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReadingPreferenceOverridesTest {
    @Test
    fun enabledDefaultSettingsRemainDistinctFromDisabledOverrides() {
        val encoded = ReaderSettings().encodeReadingOverrides()
        assertNotEquals("{}", encoded)
        assertEquals(ReaderSettings(), Json.decodeFromString<ReaderSettings>(encoded))
    }

    @Test
    fun resettingAudioOverridesToDefaultsDoesNotFallBackToGlobalAudioSettings() {
        val global = ReaderSettings(
            backupFolderUri = "content://backups",
            formatPreferences = FormatPreferences(audioSpeed = 1.7)
        )
        val encoded = ReaderSettings().encodeReadingOverrides()
        val local =
            encoded.takeUnless { it == "{}" }?.let { Json.decodeFromString<ReaderSettings>(it) }
        val effective = if (local == null) global else global.withReadingPreferencesFrom(local)
        assertEquals(1.0, effective.formatPreferences.audioSpeed, 0.0)
        assertEquals(global.backupFolderUri, effective.backupFolderUri)
        assertEquals(1.7, global.formatPreferences.audioSpeed, 0.0)
    }
}
