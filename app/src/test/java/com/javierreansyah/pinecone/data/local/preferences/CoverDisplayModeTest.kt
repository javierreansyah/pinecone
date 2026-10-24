package com.javierreansyah.pinecone.data.local.preferences

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverDisplayModeTest {
    @Test
    fun existingSettingsKeepFullCoverDisplay() {
        assertEquals(CoverDisplayMode.Contain, ReaderSettings().coverDisplayMode)
        assertEquals(
            CoverDisplayMode.Contain,
            Json.decodeFromString<ReaderSettings>("{}").coverDisplayMode
        )
    }

    @Test
    fun croppedCoverDisplaySurvivesSettingsSerialization() {
        val settings = ReaderSettings(coverDisplayMode = CoverDisplayMode.Cover)
        val restored = Json.decodeFromString<ReaderSettings>(Json.encodeToString(settings))
        assertEquals(CoverDisplayMode.Cover, restored.coverDisplayMode)
    }

    @Test
    fun bookReadingOverridesPreserveTheGlobalCoverDisplayMode() {
        val global = ReaderSettings(coverDisplayMode = CoverDisplayMode.Cover)
        assertEquals(
            CoverDisplayMode.Cover,
            global.withReadingPreferencesFrom(ReaderSettings()).coverDisplayMode,
        )
    }
}
