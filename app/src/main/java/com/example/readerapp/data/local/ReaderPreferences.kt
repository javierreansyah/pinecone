package com.example.readerapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ImageFilter
import org.readium.r2.shared.util.Language

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

data class CustomReaderTheme(
    val name: String,
    val backgroundColor: String,
    val textColor: String
)

data class ReaderSettings(
    // App-level settings (not Readium)
    val themeMode: String = "System",
    val colorPalette: String = "Dynamic",
    val locale: String = "System",
    val brightness: Float = 1.0f,
    val autoBrightness: Boolean = false,

    // Readium-mapped settings
    val publisherStyles: Boolean = true,
    val fontSize: Double = 1.0,
    val fontFamily: String = "Original",
    val textAlign: String = "Start",
    val lineHeight: Double = 1.5,
    val paragraphSpacing: Double = 0.0,
    val paragraphIndent: Double = 0.0,
    val wordSpacing: Double = 0.0,
    val letterSpacing: Double = 0.0,
    val fontWeight: Double? = null,
    val hyphens: Boolean = false,
    val scroll: Boolean = false,
    val columnCount: String = "Auto",
    val pageMargins: Double = 1.0,
    val imageFilter: String = "None",
    val typeScale: Double = 1.0,
    val readingProgression: String = "LTR",
    val textNormalization: Boolean = false,

    // Reader theme
    val readerThemePreset: String = "Auto",
    val customBackgroundColor: String = "#FFFFFF",
    val customTextColor: String = "#000000",
    val customThemes: List<CustomReaderTheme> = emptyList()
) {
    /**
     * Converts the app-level reader settings to Readium's [EpubPreferences].
     */
    fun toEpubPreferences(): EpubPreferences {
        return EpubPreferences(
            theme = when (readerThemePreset) {
                "Light" -> Theme.LIGHT
                "Dark" -> Theme.DARK
                "Warm" -> Theme.SEPIA
                else -> null // Auto or Custom will be handled by app theme
            },
            publisherStyles = publisherStyles,
            fontSize = fontSize,
            fontFamily = when (fontFamily) {
                "Serif" -> FontFamily.SERIF
                "Sans-Serif" -> FontFamily.SANS_SERIF
                "OpenDyslexic" -> FontFamily.OPEN_DYSLEXIC
                else -> null
            },
            textAlign = when (textAlign) {
                "Justify" -> TextAlign.JUSTIFY
                "Left" -> TextAlign.LEFT
                "Right" -> TextAlign.RIGHT
                "Start" -> TextAlign.START
                else -> null
            },
            lineHeight = lineHeight,
            paragraphSpacing = paragraphSpacing,
            paragraphIndent = paragraphIndent,
            wordSpacing = wordSpacing,
            letterSpacing = letterSpacing,
            fontWeight = fontWeight,
            hyphens = hyphens,
            scroll = scroll,
            columnCount = when (columnCount) {
                "1" -> ColumnCount.ONE
                "2" -> ColumnCount.TWO
                else -> ColumnCount.AUTO
            },
            pageMargins = pageMargins,
            imageFilter = when (imageFilter) {
                "Darken" -> ImageFilter.DARKEN
                "Invert" -> ImageFilter.INVERT
                else -> null
            },
            typeScale = typeScale,
            readingProgression = when (readingProgression) {
                "LTR" -> ReadingProgression.LTR
                "RTL" -> ReadingProgression.RTL
                else -> null
            },
            textNormalization = textNormalization,
            language = if (locale != "System") Language(locale) else null
        )
    }
}

class ReaderPreferences(private val context: Context) {
    companion object {
        // App-level settings
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val LOCALE = stringPreferencesKey("locale")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val AUTO_BRIGHTNESS = booleanPreferencesKey("auto_brightness")

        // Readium-mapped settings
        val PUBLISHER_STYLES = booleanPreferencesKey("publisher_styles")
        val FONT_SIZE = doublePreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val TEXT_ALIGN = stringPreferencesKey("text_align")
        val LINE_HEIGHT = doublePreferencesKey("line_height")
        val PARAGRAPH_SPACING = doublePreferencesKey("paragraph_spacing")
        val PARAGRAPH_INDENT = doublePreferencesKey("paragraph_indent")
        val WORD_SPACING = doublePreferencesKey("word_spacing")
        val LETTER_SPACING = doublePreferencesKey("letter_spacing")
        val FONT_WEIGHT = doublePreferencesKey("font_weight")
        val HYPHENS = booleanPreferencesKey("hyphens")
        val SCROLL = booleanPreferencesKey("scroll")
        val COLUMN_COUNT = stringPreferencesKey("column_count")
        val PAGE_MARGINS = doublePreferencesKey("page_margins")
        val IMAGE_FILTER = stringPreferencesKey("image_filter")
        val TYPE_SCALE = doublePreferencesKey("type_scale")
        val READING_PROGRESSION = stringPreferencesKey("reading_progression")
        val TEXT_NORMALIZATION = booleanPreferencesKey("text_normalization")

        // Reader theme
        val READER_THEME_PRESET = stringPreferencesKey("reader_theme_preset")
        val CUSTOM_BACKGROUND_COLOR = stringPreferencesKey("custom_background_color")
        val CUSTOM_TEXT_COLOR = stringPreferencesKey("custom_text_color")
        val CUSTOM_THEMES = stringSetPreferencesKey("custom_themes")

        // First-launch flag for bundled book import
        val HAS_IMPORTED_BUNDLED = booleanPreferencesKey("has_imported_bundled")
    }

    val readerSettings: Flow<ReaderSettings> = context.dataStore.data.map { preferences ->
        ReaderSettings(
            themeMode = preferences[THEME_MODE] ?: "System",
            colorPalette = preferences[COLOR_PALETTE] ?: "Dynamic",
            locale = preferences[LOCALE] ?: "System",
            brightness = preferences[BRIGHTNESS] ?: 1.0f,
            autoBrightness = preferences[AUTO_BRIGHTNESS] ?: false,

            publisherStyles = preferences[PUBLISHER_STYLES] ?: true,
            fontSize = (preferences[FONT_SIZE] ?: 1.0).let { Math.round(it * 100.0) / 100.0 },
            fontFamily = preferences[FONT_FAMILY] ?: "Original",
            textAlign = preferences[TEXT_ALIGN] ?: "Start",
            lineHeight = (preferences[LINE_HEIGHT] ?: 1.5).let { Math.round(it * 10.0) / 10.0 },
            paragraphSpacing = (preferences[PARAGRAPH_SPACING] ?: 0.0).let { Math.round(it * 100.0) / 100.0 },
            paragraphIndent = (preferences[PARAGRAPH_INDENT] ?: 0.0).let { Math.round(it * 100.0) / 100.0 },
            wordSpacing = (preferences[WORD_SPACING] ?: 0.0).let { Math.round(it * 100.0) / 100.0 },
            letterSpacing = (preferences[LETTER_SPACING] ?: 0.0).let { Math.round(it * 100.0) / 100.0 },
            fontWeight = preferences[FONT_WEIGHT],
            hyphens = preferences[HYPHENS] ?: false,
            scroll = preferences[SCROLL] ?: false,
            columnCount = preferences[COLUMN_COUNT] ?: "Auto",
            pageMargins = (preferences[PAGE_MARGINS] ?: 1.0).let { Math.round(it * 100.0) / 100.0 },
            imageFilter = preferences[IMAGE_FILTER] ?: "None",
            typeScale = (preferences[TYPE_SCALE] ?: 1.0).let { Math.round(it * 100.0) / 100.0 },
            readingProgression = preferences[READING_PROGRESSION] ?: "LTR",
            textNormalization = preferences[TEXT_NORMALIZATION] ?: false,

            readerThemePreset = preferences[READER_THEME_PRESET] ?: "Auto",
            customBackgroundColor = preferences[CUSTOM_BACKGROUND_COLOR] ?: "#FFFFFF",
            customTextColor = preferences[CUSTOM_TEXT_COLOR] ?: "#000000",
            customThemes = preferences[CUSTOM_THEMES]?.map {
                val parts = it.split("|")
                CustomReaderTheme(parts[0], parts[1], parts[2])
            } ?: emptyList()
        )
    }

    suspend fun updateSettings(settings: ReaderSettings) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = settings.themeMode
            preferences[COLOR_PALETTE] = settings.colorPalette
            preferences[LOCALE] = settings.locale
            preferences[BRIGHTNESS] = settings.brightness
            preferences[AUTO_BRIGHTNESS] = settings.autoBrightness

            preferences[PUBLISHER_STYLES] = settings.publisherStyles
            preferences[FONT_SIZE] = Math.round(settings.fontSize * 100.0) / 100.0
            preferences[FONT_FAMILY] = settings.fontFamily
            preferences[TEXT_ALIGN] = settings.textAlign
            preferences[LINE_HEIGHT] = Math.round(settings.lineHeight * 10.0) / 10.0
            preferences[PARAGRAPH_SPACING] = Math.round(settings.paragraphSpacing * 100.0) / 100.0
            preferences[PARAGRAPH_INDENT] = Math.round(settings.paragraphIndent * 100.0) / 100.0
            preferences[WORD_SPACING] = Math.round(settings.wordSpacing * 100.0) / 100.0
            preferences[LETTER_SPACING] = Math.round(settings.letterSpacing * 100.0) / 100.0
            if (settings.fontWeight != null) {
                preferences[FONT_WEIGHT] = settings.fontWeight
            } else {
                preferences.remove(FONT_WEIGHT)
            }
            preferences[HYPHENS] = settings.hyphens
            preferences[SCROLL] = settings.scroll
            preferences[COLUMN_COUNT] = settings.columnCount
            preferences[PAGE_MARGINS] = Math.round(settings.pageMargins * 100.0) / 100.0
            preferences[IMAGE_FILTER] = settings.imageFilter
            preferences[TYPE_SCALE] = Math.round(settings.typeScale * 100.0) / 100.0
            preferences[READING_PROGRESSION] = settings.readingProgression
            preferences[TEXT_NORMALIZATION] = settings.textNormalization

            preferences[READER_THEME_PRESET] = settings.readerThemePreset
            preferences[CUSTOM_BACKGROUND_COLOR] = settings.customBackgroundColor
            preferences[CUSTOM_TEXT_COLOR] = settings.customTextColor
            preferences[CUSTOM_THEMES] = settings.customThemes.map {
                "${it.name}|${it.backgroundColor}|${it.textColor}"
            }.toSet()
        }
    }

    suspend fun updateAllSettings(settings: ReaderSettings) {
        updateSettings(settings)
    }

    val hasImportedBundled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_IMPORTED_BUNDLED] ?: false
    }

    suspend fun setHasImportedBundled(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_IMPORTED_BUNDLED] = value
        }
    }
}
