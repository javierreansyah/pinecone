package com.javierreansyah.pinecone.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingPreferenceOverridesTest {
    @Test
    fun enabledDefaultSettingsRemainDistinctFromDisabledOverrides() {
        val encoded = ReaderSettings().encodeReadingOverrides()
        assertNotEquals("{}", encoded)
        assertEquals(ReaderSettings(), decodeReadingOverrides(encoded))
    }

    @Test
    fun resettingAudioOverridesToDefaultsDoesNotFallBackToGlobalAudioSettings() {
        val global = ReaderSettings(
            backupFolderUri = "content://backups",
            formatPreferences = FormatPreferences(audioSpeed = 1.7)
        )
        val encoded = ReaderSettings().encodeReadingOverrides()
        val local = decodeReadingOverrides(encoded)
        val effective = if (local == null) global else global.withReadingPreferencesFrom(local)
        assertEquals(1.0, effective.formatPreferences.audioSpeed, 0.0)
        assertEquals(global.backupFolderUri, effective.backupFolderUri)
        assertEquals(1.7, global.formatPreferences.audioSpeed, 0.0)
    }

    @Test
    fun disabledOverridesAndMalformedOverridesRemainDifferent() {
        assertNull(decodeReadingOverrides(null))
        assertNull(decodeReadingOverrides("{}"))
        val global = ReaderSettings(fontSize = 1.7)
        assertEquals(global, global.withReadingPreferencesFrom(null))
        assertThrows(IllegalArgumentException::class.java) { decodeReadingOverrides("not json") }
    }
}
