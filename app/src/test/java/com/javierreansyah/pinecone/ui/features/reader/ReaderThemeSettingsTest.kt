package com.javierreansyah.pinecone.ui.features.reader

import com.javierreansyah.pinecone.data.local.preferences.CustomPineconeTheme
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemeSettingsTest {
    @Test
    fun `auto colors follow the system when the app theme is system`() {
        val settings = ReaderThemeSettings()
        val light = settings.resolveColors(false)
        val dark = settings.resolveColors(true)

        assertEquals(0xFFFFFFFF.toInt(), light.backgroundColorInt)
        assertEquals(0xFF000000.toInt(), dark.backgroundColorInt)
        assertFalse(light.isDarkTheme)
        assertTrue(dark.isDarkTheme)
        assertEquals(light.textColor, dark.backgroundColor)
        assertEquals(dark.textColor, light.backgroundColor)
    }

    @Test
    fun `explicit app theme overrides the system for auto colors`() {
        assertTrue(ReaderThemeSettings(themeMode = "Dark").resolveColors(false).isDarkTheme)
        assertFalse(ReaderThemeSettings(themeMode = "Light").resolveColors(true).isDarkTheme)
    }

    @Test
    fun `fixed reading presets keep their colors independently of toolbar appearance`() {
        val presets = listOf(
            "Light" to (0xFFFFFFFF.toInt() to 0xFF000000.toInt()),
            "Warm" to (0xFFFAF4E8.toInt() to 0xFF121212.toInt()),
            "Dark" to (0xFF000000.toInt() to 0xFFFFFFFF.toInt()),
        )
        for ((preset, colors) in presets) {
            for (systemDark in listOf(false, true)) {
                val resolved = ReaderThemeSettings(preset = preset).resolveColors(systemDark)
                assertEquals(colors.first, resolved.backgroundColorInt)
                assertEquals(androidx.compose.ui.graphics.Color(colors.second), resolved.textColor)
                assertEquals(systemDark, resolved.isDarkTheme)
            }
        }
    }

    @Test
    fun `saved custom theme chooses toolbar appearance without replacing selected colors`() {
        val selected = CustomPineconeTheme("Paper", "#FAF4E8", "#121212", "Dark")
        val settings = ReaderSettings(
            themeMode = "Light",
            pineconeThemePreset = selected.name,
            customBackgroundColor = "#EFEFEF",
            customTextColor = "#111111",
            customThemes = listOf(selected),
        ).toReaderThemeSettings()

        assertTrue(settings.isUiDark(false))
        assertEquals("#EFEFEF", settings.customBackgroundColor)
        assertEquals("#111111", settings.customTextColor)
    }

    @Test
    fun `both persisted setting aliases inherit app appearance`() {
        for (alias in listOf("Setting", "setting")) {
            assertTrue(ReaderThemeSettings(themeMode = "Dark", customUiTheme = alias).isUiDark(false))
            assertFalse(ReaderThemeSettings(themeMode = "Light", customUiTheme = alias).isUiDark(true))
        }
    }

    @Test
    fun `custom system appearance follows the system rather than the app override`() {
        val settings = ReaderThemeSettings(themeMode = "Dark", customUiTheme = "System")
        assertFalse(settings.isUiDark(false))
        assertTrue(settings.isUiDark(true))
    }

    @Test
    fun `unrelated settings and unselected custom themes do not invalidate theme inputs`() = runBlocking {
        val original = ReaderSettings(customThemes = listOf(CustomPineconeTheme("Paper", "#FFF", "#000")))
        val unrelatedChanges = original.copy(
            fontSize = 2.0,
            brightness = 0.2f,
            jumpHistoryMode = "disabled",
            lastBackupTime = 123L,
            customThemes = emptyList(),
        )
        val emitted = flowOf(original, unrelatedChanges, unrelatedChanges.copy(themeMode = "Dark"))
            .map(ReaderSettings::toReaderThemeSettings)
            .distinctUntilChanged()
            .toList()

        assertEquals(2, emitted.size)
        assertEquals("System", emitted[0].themeMode)
        assertEquals("Dark", emitted[1].themeMode)
    }

    @Test
    fun `editing the selected custom toolbar theme invalidates theme inputs`() {
        val theme = CustomPineconeTheme("Paper", "#FFFFFF", "#000000", "Light")
        val settings = ReaderSettings(pineconeThemePreset = "Paper", customThemes = listOf(theme))
        val updated = settings.copy(customThemes = listOf(theme.copy(uiTheme = "Dark")))

        assertFalse(settings.toReaderThemeSettings().isUiDark(true))
        assertTrue(updated.toReaderThemeSettings().isUiDark(false))
    }
}
