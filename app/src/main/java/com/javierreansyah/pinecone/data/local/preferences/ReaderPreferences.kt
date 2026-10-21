package com.javierreansyah.pinecone.data.local.preferences

import android.content.Context
import androidx.core.graphics.toColorInt
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ImageFilter
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import kotlin.math.roundToInt

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

@Serializable
data class CustomPineconeTheme(
    val name: String,
    val backgroundColor: String,
    val textColor: String,
    val uiTheme: String = "Setting",
)

@Serializable
data class InstalledDictionary(
    val id: String, val name: String, val wordCount: Int,
)

@Serializable
data class ReaderSettings(

    val themeMode: String = "System",
    val colorPalette: String = "Dynamic",
    val themeContrast: String = "Standard",
    val locale: String = "System",
    val brightness: Float = 0.5f,
    val autoBrightness: Boolean = true,
    val forceOrientation: String = "Auto",
    val preventScreenTimeout: Boolean = false,
    val alwaysShowStatusBar: Boolean = false,

    val publisherStyles: Boolean = true,
    val fontSize: Double = 1.0,
    val fontFamily: String = "Source Serif 4",
    val textAlign: String = "Start",
    val lineHeight: Double = 1.5,
    val paragraphSpacing: Double = 0.0,
    val paragraphIndent: Double = 0.0,
    val wordSpacing: Double = 0.0,
    val letterSpacing: Double = 0.0,
    val fontWeights: Map<String, Double> = emptyMap(),
    val hyphens: Boolean = false,
    val scroll: Boolean = false,
    val columnCount: String = "Auto",
    val pageMargins: Double = 1.0,
    val imageFilter: String = "None",
    val typeScale: Double = 1.0,
    val verticalMargin: Double = 32.0,
    val readingProgression: String = "LTR",
    val textNormalization: Boolean = false,

    val pineconeThemePreset: String = "Auto",
    val customBackgroundColor: String = "#FFFFFF",
    val customTextColor: String = "#000000",
    val customThemes: List<CustomPineconeTheme> = emptyList(),

    val autoBackupFrequency: String = "12h",
    val lastBackupTime: Long = 0L,
    val lastBackupRevision: Long = -1L,
    val backupFolderUri: String = "",

    val activeDictionaryId: String = "",
    val installedDictionaries: List<InstalledDictionary> = emptyList(),
    val jumpHistoryMode: String = "explicit",
    val showAllSpaces: Boolean = true,
    val formatPreferences: FormatPreferences = FormatPreferences(),
    val spacesEnabled: Boolean = true,
    val unsortedEnabled: Boolean = true,
) {

    @OptIn(ExperimentalReadiumApi::class)
    fun toEpubPreferences(isSystemDark: Boolean): EpubPreferences {
        val resolvedTheme = when (pineconeThemePreset) {
            "Light" -> Theme.LIGHT
            "Dark" -> Theme.DARK
            "Warm" -> Theme.SEPIA
            "Auto" -> {
                val isDark = when (themeMode) {
                    "Dark" -> true
                    "Light" -> false
                    else -> isSystemDark
                }
                if (isDark) Theme.DARK else Theme.LIGHT
            }

            else -> null
        }

        val bgColor = if (pineconeThemePreset !in listOf("Light", "Dark", "Warm", "Auto")) {
            try {
                Color(customBackgroundColor.toColorInt())
            } catch (_: Exception) {
                null
            }
        } else null

        val txtColor = if (pineconeThemePreset !in listOf("Light", "Dark", "Warm", "Auto")) {
            try {
                Color(customTextColor.toColorInt())
            } catch (_: Exception) {
                null
            }
        } else null

        return EpubPreferences(
            theme = resolvedTheme,
            backgroundColor = bgColor,
            textColor = txtColor,
            publisherStyles = publisherStyles,
            fontSize = fontSize,
            fontFamily = when (fontFamily) {
                "Serif" -> FontFamily("serif")
                "Sans-Serif" -> FontFamily("sans-serif")
                "Literata" -> FontFamily("Literata")
                "Source Serif 4" -> FontFamily("Source Serif 4")
                "Source Sans 3" -> FontFamily("Source Sans 3")
                "Atkinson Hyperlegible" -> FontFamily("Atkinson Hyperlegible")
                "Source Code" -> FontFamily("Source Code")
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
            fontWeight = fontWeights[fontFamily],
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
            ligatures = formatPreferences.epubLigatures,
            verticalText = formatPreferences.epubVerticalText,
            spread = when (formatPreferences.epubSpread) {
                "always" -> org.readium.r2.navigator.preferences.Spread.ALWAYS
                "never" -> org.readium.r2.navigator.preferences.Spread.NEVER
                else -> null
            },
            // UI locale is not the language of the publication.
            language = null,
        )
    }
}

class ReaderPreferences(private val context: Context) {
    companion object {

        val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("theme_mode")
        val COLOR_PALETTE: Preferences.Key<String> = stringPreferencesKey("color_palette")
        val THEME_CONTRAST: Preferences.Key<String> = stringPreferencesKey("theme_contrast")
        val LOCALE: Preferences.Key<String> = stringPreferencesKey("locale")
        val BRIGHTNESS: Preferences.Key<Float> = floatPreferencesKey("brightness")
        val AUTO_BRIGHTNESS: Preferences.Key<Boolean> = booleanPreferencesKey("auto_brightness")
        val FORCE_ORIENTATION: Preferences.Key<String> = stringPreferencesKey("force_orientation")
        val PREVENT_SCREEN_TIMEOUT: Preferences.Key<Boolean> =
            booleanPreferencesKey("prevent_screen_timeout")
        val ALWAYS_SHOW_STATUS_BAR: Preferences.Key<Boolean> =
            booleanPreferencesKey("always_show_status_bar")

        val PUBLISHER_STYLES: Preferences.Key<Boolean> = booleanPreferencesKey("publisher_styles")
        val FONT_SIZE: Preferences.Key<Double> = doublePreferencesKey("font_size")
        val FONT_FAMILY: Preferences.Key<String> = stringPreferencesKey("font_family")
        val TEXT_ALIGN: Preferences.Key<String> = stringPreferencesKey("text_align")
        val LINE_HEIGHT: Preferences.Key<Double> = doublePreferencesKey("line_height")
        val PARAGRAPH_SPACING: Preferences.Key<Double> = doublePreferencesKey("paragraph_spacing")
        val PARAGRAPH_INDENT: Preferences.Key<Double> = doublePreferencesKey("paragraph_indent")
        val WORD_SPACING: Preferences.Key<Double> = doublePreferencesKey("word_spacing")
        val LETTER_SPACING: Preferences.Key<Double> = doublePreferencesKey("letter_spacing")
        val FONT_WEIGHTS: Preferences.Key<String> = stringPreferencesKey("font_weights")
        val HYPHENS: Preferences.Key<Boolean> = booleanPreferencesKey("hyphens")
        val SCROLL: Preferences.Key<Boolean> = booleanPreferencesKey("scroll")
        val COLUMN_COUNT: Preferences.Key<String> = stringPreferencesKey("column_count")
        val PAGE_MARGINS: Preferences.Key<Double> = doublePreferencesKey("page_margins")
        val IMAGE_FILTER: Preferences.Key<String> = stringPreferencesKey("image_filter")
        val TYPE_SCALE: Preferences.Key<Double> = doublePreferencesKey("type_scale")
        val VERTICAL_MARGIN: Preferences.Key<Double> = doublePreferencesKey("vertical_margin")
        val READING_PROGRESSION: Preferences.Key<String> =
            stringPreferencesKey("reading_progression")
        val TEXT_NORMALIZATION: Preferences.Key<Boolean> =
            booleanPreferencesKey("text_normalization")

        val READER_THEME_PRESET: Preferences.Key<String> =
            stringPreferencesKey("reader_theme_preset")
        val CUSTOM_BACKGROUND_COLOR: Preferences.Key<String> =
            stringPreferencesKey("custom_background_color")
        val CUSTOM_TEXT_COLOR: Preferences.Key<String> = stringPreferencesKey("custom_text_color")
        val CUSTOM_THEMES: Preferences.Key<Set<String>> = stringSetPreferencesKey("custom_themes")

        val AUTO_BACKUP_FREQUENCY: Preferences.Key<String> =
            stringPreferencesKey("auto_backup_frequency")
        val LAST_BACKUP_TIME: Preferences.Key<Long> = longPreferencesKey("last_backup_time")
        val LAST_BACKUP_REVISION: Preferences.Key<Long> = longPreferencesKey("last_backup_revision")
        val BACKUP_FOLDER_URI: Preferences.Key<String> = stringPreferencesKey("backup_folder_uri")

        val ACTIVE_DICTIONARY_ID: Preferences.Key<String> =
            stringPreferencesKey("active_dictionary_id")
        val INSTALLED_DICTIONARIES: Preferences.Key<Set<String>> =
            stringSetPreferencesKey("installed_dictionaries")

        val JUMP_HISTORY_MODE: Preferences.Key<String> = stringPreferencesKey("jump_history_mode")
        val SHOW_ALL_SPACES: Preferences.Key<Boolean> = booleanPreferencesKey("show_all_spaces")
        val SPACES_ENABLED = booleanPreferencesKey("spaces_enabled")
        val UNSORTED_ENABLED = booleanPreferencesKey("unsorted_enabled")
        val FORMAT_PREFERENCES: Preferences.Key<String> = stringPreferencesKey("format_preferences")
    }

    val readerSettings: Flow<ReaderSettings> = context.dataStore.data.map { preferences ->
        ReaderSettings(
            themeMode = preferences[THEME_MODE] ?: "System",
            colorPalette = preferences[COLOR_PALETTE] ?: "Dynamic",
            themeContrast = preferences[THEME_CONTRAST] ?: "Standard",
            locale = preferences[LOCALE] ?: "System",
            brightness = preferences[BRIGHTNESS] ?: 0.5f,
            autoBrightness = preferences[AUTO_BRIGHTNESS] ?: true,
            forceOrientation = preferences[FORCE_ORIENTATION] ?: "Auto",
            preventScreenTimeout = preferences[PREVENT_SCREEN_TIMEOUT] ?: false,
            alwaysShowStatusBar = preferences[ALWAYS_SHOW_STATUS_BAR] ?: false,

            publisherStyles = preferences[PUBLISHER_STYLES] ?: true,
            fontSize = (preferences[FONT_SIZE] ?: 1.0).let { (it * 100.0).roundToInt() / 100.0 },
            fontFamily = preferences[FONT_FAMILY] ?: "Source Serif 4",
            textAlign = preferences[TEXT_ALIGN] ?: "Start",
            lineHeight = (preferences[LINE_HEIGHT] ?: 1.5).let { (it * 10.0).roundToInt() / 10.0 },
            paragraphSpacing = (preferences[PARAGRAPH_SPACING]
                ?: 0.0).let { (it * 100.0).roundToInt() / 100.0 },
            paragraphIndent = (preferences[PARAGRAPH_INDENT]
                ?: 0.0).let { (it * 100.0).roundToInt() / 100.0 },
            wordSpacing = (preferences[WORD_SPACING]
                ?: 0.0).let { (it * 100.0).roundToInt() / 100.0 },
            letterSpacing = (preferences[LETTER_SPACING]
                ?: 0.0).let { (it * 1000.0).roundToInt() / 1000.0 },
            fontWeights = preferences[FONT_WEIGHTS]?.let { str ->
                if (str.isNotBlank()) {
                    str.split(",").associate { pair ->
                        val parts = pair.split(":")
                        val weight = parts[1].toDouble()
                        parts[0] to (if (weight > 10.0) 1.0 else weight)
                    }
                } else emptyMap()
            } ?: emptyMap(),
            hyphens = preferences[HYPHENS] ?: false,
            scroll = preferences[SCROLL] ?: false,
            columnCount = preferences[COLUMN_COUNT] ?: "Auto",
            pageMargins = (preferences[PAGE_MARGINS]
                ?: 1.0).let { (it * 100.0).roundToInt() / 100.0 },
            imageFilter = preferences[IMAGE_FILTER] ?: "None",
            typeScale = (preferences[TYPE_SCALE] ?: 1.0).let { (it * 100.0).roundToInt() / 100.0 },
            verticalMargin = (preferences[VERTICAL_MARGIN]
                ?: 32.0).let { (it * 10.0).roundToInt() / 10.0 },
            readingProgression = preferences[READING_PROGRESSION] ?: "LTR",
            textNormalization = preferences[TEXT_NORMALIZATION] ?: false,

            pineconeThemePreset = preferences[READER_THEME_PRESET] ?: "Auto",
            customBackgroundColor = preferences[CUSTOM_BACKGROUND_COLOR] ?: "#FFFFFF",
            customTextColor = preferences[CUSTOM_TEXT_COLOR] ?: "#000000",
            customThemes = preferences[CUSTOM_THEMES]?.map {
                val parts = it.split("|")
                val uiTheme = if (parts.size >= 4) parts[3] else "Setting"
                CustomPineconeTheme(parts[0], parts[1], parts[2], uiTheme)
            } ?: emptyList(),

            autoBackupFrequency = preferences[AUTO_BACKUP_FREQUENCY] ?: "12h",
            lastBackupTime = preferences[LAST_BACKUP_TIME] ?: 0L,
            lastBackupRevision = preferences[LAST_BACKUP_REVISION] ?: -1L,
            backupFolderUri = preferences[BACKUP_FOLDER_URI] ?: "",

            activeDictionaryId = preferences[ACTIVE_DICTIONARY_ID] ?: "",
            installedDictionaries = preferences[INSTALLED_DICTIONARIES]?.mapNotNull {
                val parts = it.split("|")
                if ((parts.size == 3) && parts[0].isNotEmpty()) {
                    InstalledDictionary(parts[0], parts[1], parts[2].toIntOrNull() ?: 0)
                } else {
                    null
                }
            } ?: emptyList(),
            jumpHistoryMode = preferences[JUMP_HISTORY_MODE] ?: "explicit",
            showAllSpaces = preferences[SHOW_ALL_SPACES] ?: true,
            spacesEnabled = preferences[SPACES_ENABLED] ?: true,
            unsortedEnabled = preferences[UNSORTED_ENABLED] ?: true,
            formatPreferences = preferences[FORMAT_PREFERENCES]?.let {
                kotlinx.serialization.json.Json.decodeFromString<FormatPreferences>(
                    it
                )
            } ?: FormatPreferences(),
        )
    }

    suspend fun updateSettings(settings: ReaderSettings) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = settings.themeMode
            preferences[COLOR_PALETTE] = settings.colorPalette
            preferences[THEME_CONTRAST] = settings.themeContrast
            preferences[LOCALE] = settings.locale
            preferences[BRIGHTNESS] = settings.brightness
            preferences[AUTO_BRIGHTNESS] = settings.autoBrightness
            preferences[FORCE_ORIENTATION] = settings.forceOrientation
            preferences[PREVENT_SCREEN_TIMEOUT] = settings.preventScreenTimeout
            preferences[ALWAYS_SHOW_STATUS_BAR] = settings.alwaysShowStatusBar

            preferences[PUBLISHER_STYLES] = settings.publisherStyles
            preferences[FONT_SIZE] = (settings.fontSize * 100.0).roundToInt() / 100.0
            preferences[FONT_FAMILY] = settings.fontFamily
            preferences[TEXT_ALIGN] = settings.textAlign
            preferences[LINE_HEIGHT] = (settings.lineHeight * 10.0).roundToInt() / 10.0
            preferences[PARAGRAPH_SPACING] =
                (settings.paragraphSpacing * 100.0).roundToInt() / 100.0
            preferences[PARAGRAPH_INDENT] = (settings.paragraphIndent * 100.0).roundToInt() / 100.0
            preferences[WORD_SPACING] = (settings.wordSpacing * 100.0).roundToInt() / 100.0
            preferences[LETTER_SPACING] = (settings.letterSpacing * 1000.0).roundToInt() / 1000.0
            if (settings.fontWeights.isNotEmpty()) {
                preferences[FONT_WEIGHTS] =
                    settings.fontWeights.entries.joinToString(",") { (itKey, itValue) -> "${itKey}:${itValue}" }
            } else {
                preferences.remove(FONT_WEIGHTS)
            }
            preferences[HYPHENS] = settings.hyphens
            preferences[SCROLL] = settings.scroll
            preferences[COLUMN_COUNT] = settings.columnCount
            preferences[PAGE_MARGINS] = (settings.pageMargins * 100.0).roundToInt() / 100.0
            preferences[IMAGE_FILTER] = settings.imageFilter
            preferences[TYPE_SCALE] = (settings.typeScale * 100.0).roundToInt() / 100.0
            preferences[VERTICAL_MARGIN] = (settings.verticalMargin * 10.0).roundToInt() / 10.0
            preferences[READING_PROGRESSION] = settings.readingProgression
            preferences[TEXT_NORMALIZATION] = settings.textNormalization

            preferences[READER_THEME_PRESET] = settings.pineconeThemePreset
            preferences[CUSTOM_BACKGROUND_COLOR] = settings.customBackgroundColor
            preferences[CUSTOM_TEXT_COLOR] = settings.customTextColor
            preferences[CUSTOM_THEMES] =
                settings.customThemes.map { (itName, itBackgroundColor, itTextColor, itUiTheme) ->
                    "${itName}|${itBackgroundColor}|${itTextColor}|${itUiTheme}"
                }.toSet()

            preferences[AUTO_BACKUP_FREQUENCY] = settings.autoBackupFrequency
            preferences[LAST_BACKUP_TIME] = settings.lastBackupTime
            preferences[LAST_BACKUP_REVISION] = settings.lastBackupRevision
            preferences[BACKUP_FOLDER_URI] = settings.backupFolderUri

            preferences[ACTIVE_DICTIONARY_ID] = settings.activeDictionaryId
            preferences[INSTALLED_DICTIONARIES] =
                settings.installedDictionaries.map { (itId2, itName2, itWordCount) ->
                    "${itId2}|${itName2}|${itWordCount}"
                }.toSet()
            preferences[JUMP_HISTORY_MODE] = settings.jumpHistoryMode
            preferences[SHOW_ALL_SPACES] = settings.showAllSpaces
            preferences[SPACES_ENABLED] = settings.spacesEnabled
            preferences[UNSORTED_ENABLED] = settings.unsortedEnabled
            preferences[FORMAT_PREFERENCES] =
                kotlinx.serialization.json.Json.encodeToString(settings.formatPreferences)
        }
    }

    suspend fun updateAllSettings(settings: ReaderSettings) {
        updateSettings(settings)
    }

    suspend fun markBackup(timestamp: Long, revision: Long) {
        context.dataStore.edit {
            it[LAST_BACKUP_TIME] = timestamp
            it[LAST_BACKUP_REVISION] = revision
        }
    }
}
