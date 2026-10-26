package com.javierreansyah.pinecone.data.local.preferences

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryNavigationSettingsTest {
    @Test
    fun `library bottom navigation remains enabled for existing settings`() {
        assertTrue(ReaderSettings().showLibraryBottomNavigation)
        assertTrue(Json.decodeFromString<ReaderSettings>("{}").showLibraryBottomNavigation)
    }

    @Test
    fun `hidden bottom navigation survives settings serialization`() {
        val settings = ReaderSettings(showLibraryBottomNavigation = false)
        val restored = Json.decodeFromString<ReaderSettings>(Json.encodeToString(settings))
        assertFalse(restored.showLibraryBottomNavigation)
    }

    @Test
    fun `book reading overrides do not change library navigation`() {
        val global = ReaderSettings(showLibraryBottomNavigation = false)
        assertFalse(global.withReadingPreferencesFrom(ReaderSettings()).showLibraryBottomNavigation)
    }
}
