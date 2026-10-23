package com.javierreansyah.pinecone.data.local.preferences

import com.javierreansyah.pinecone.reader.pdf.MuPdfPreferences
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageFiltersTest {
    @Test
    fun `PDF defaults to continuous scrolling without overriding saved paged viewing`() {
        assertTrue(MuPdfPreferences().scroll)
        assertTrue(Json.decodeFromString<MuPdfPreferences>("{}").scroll)
        val paged = Json.decodeFromString<MuPdfPreferences>("""{"scroll":false}""")
        assertFalse(paged.scroll)
        assertEquals(paged, Json.decodeFromString<MuPdfPreferences>(Json.encodeToString(paged)))
    }

    @Test
    fun `EPUB image filters exclude theme colored formula SVGs and their descendants`() {
        listOf("Invert" to "invert(1)", "Darken" to "brightness(0.7)").forEach { (mode, filter) ->
            val settings = ReaderSettings(pineconeThemePreset = "Dark", imageFilter = mode)
            assertEquals(
                "img, svg:not(.math-layout):not(.math-layout *) { filter: $filter !important; }",
                settings.epubImageFilterCss(false),
            )
        }
    }

    @Test
    fun `PDF follow theme responds to theme changes while explicit choices stay fixed`() {
        val follow = MuPdfPreferences()
        assertEquals(false, follow.resolveTheme(false).invert)
        assertEquals(true, follow.resolveTheme(true).invert)
        assertEquals(true, follow.copy(invert = true).resolveTheme(false).invert)
        assertEquals(false, follow.copy(invert = false).resolveTheme(true).invert)
        assertEquals(null, follow.invert)
    }

    @Test
    fun `reading surface follows explicit presets ahead of app or system theme`() {
        val settings = ReaderSettings(themeMode = "Dark")
        assertFalse(settings.copy(pineconeThemePreset = "Light").readingThemeIsDark(true))
        assertFalse(settings.copy(pineconeThemePreset = "Warm").readingThemeIsDark(true))
        assertTrue(
            settings.copy(themeMode = "Light", pineconeThemePreset = "Dark")
                .readingThemeIsDark(false)
        )
        assertTrue(settings.readingThemeIsDark(false))
        assertFalse(settings.copy(themeMode = "Light").readingThemeIsDark(true))
        assertTrue(settings.copy(themeMode = "System").readingThemeIsDark(true))
        assertFalse(settings.copy(themeMode = "System").readingThemeIsDark(false))
    }

    @Test
    fun `custom reading backgrounds determine filter activation independently of toolbar theme`() {
        val custom = ReaderSettings(
            pineconeThemePreset = "Custom",
            themeMode = "Light",
            customBackgroundColor = "#181818"
        )
        assertTrue(custom.readingThemeIsDark(false))
        assertFalse(
            custom.copy(customBackgroundColor = "#FAF4E8", themeMode = "Dark")
                .readingThemeIsDark(true)
        )
    }

    @Test
    fun `EPUB filters follow dark surfaces or can be active in light surfaces`() {
        val settings = ReaderSettings(pineconeThemePreset = "Light", imageFilter = "Invert")
        assertEquals("", settings.epubImageFilterCss(true))
        val dark = settings.copy(pineconeThemePreset = "Dark")
        assertTrue(dark.epubImageFilterCss(false).contains("invert(1)"))
        val always = settings.copy(
            formatPreferences = settings.formatPreferences.copy(epubImageFilterFollowTheme = false)
        )
        assertTrue(always.epubImageFilterCss(false).contains("invert(1)"))
        assertTrue(
            always.copy(imageFilter = "Darken").epubImageFilterCss(false).contains("brightness(")
        )
        assertEquals("", always.copy(imageFilter = "None").epubImageFilterCss(false))
    }

    @Test
    fun `saved settings preserve activation modes and existing PDF inversion choices`() {
        val modes = listOf(null, true, false)
        modes.forEach { invert ->
            val settings = ReaderSettings(
                formatPreferences = FormatPreferences(
                    pdf = MuPdfPreferences(invert = invert), epubImageFilterFollowTheme = false,
                )
            )
            assertEquals(
                settings,
                Json.decodeFromString<ReaderSettings>(Json.encodeToString(settings))
            )
        }
        assertEquals(true, Json.decodeFromString<MuPdfPreferences>("""{"invert":true}""").invert)
        assertEquals(false, Json.decodeFromString<MuPdfPreferences>("""{"invert":false}""").invert)
    }

    @Test
    fun `rendition overrides retain image filter modes without replacing app settings`() {
        val global = ReaderSettings(themeMode = "Light", spacesEnabled = false)
        val local = ReaderSettings(
            imageFilter = "Darken", formatPreferences = FormatPreferences(
                pdf = MuPdfPreferences(invert = false), epubImageFilterFollowTheme = false,
            )
        )
        val merged = global.withReadingPreferencesFrom(local)
        assertEquals(global.themeMode, merged.themeMode)
        assertEquals(global.spacesEnabled, merged.spacesEnabled)
        assertEquals(local.formatPreferences, merged.formatPreferences)
        assertTrue(merged.epubImageFilterCss(false).contains("brightness("))
    }
}
