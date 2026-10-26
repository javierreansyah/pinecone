package com.javierreansyah.pinecone.data.local.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlin.math.roundToInt

/** Read-only compatibility adapter. New settings are persisted as typed JSON. */
internal object LegacyReaderSettings {


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
    val SPACES_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("spaces_enabled")
    val UNSORTED_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("unsorted_enabled")
    val SHOW_LIBRARY_BOTTOM_NAVIGATION: Preferences.Key<Boolean> =
        booleanPreferencesKey("show_library_bottom_navigation")
    val COVER_DISPLAY_MODE: Preferences.Key<String> = stringPreferencesKey("cover_display_mode")
    val FORMAT_PREFERENCES: Preferences.Key<String> = stringPreferencesKey("format_preferences")

    fun read(preferences: Preferences): ReaderSettings {
        return ReaderSettings(
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
            fontWeights = preferences[FONT_WEIGHTS].orEmpty().split(",").mapNotNull { item ->
                val parts = item.split(":", limit = 2)
                val weight = parts.getOrNull(1)?.toDoubleOrNull()?.takeIf(Double::isFinite)
                weight?.let { parts[0] to if (it > 10.0) 1.0 else it }
            }.toMap(),
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
            customThemes = preferences[CUSTOM_THEMES]?.mapNotNull {
                val parts = it.split("|")
                if (parts.size < 3) return@mapNotNull null
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
                if (parts.size == 3 && parts[0].isNotEmpty()) {
                    InstalledDictionary(parts[0], parts[1], parts[2].toIntOrNull() ?: 0)
                } else {
                    null
                }
            } ?: emptyList(),
            jumpHistoryMode = preferences[JUMP_HISTORY_MODE] ?: "explicit",
            showAllSpaces = preferences[SHOW_ALL_SPACES] ?: true,
            spacesEnabled = preferences[SPACES_ENABLED] ?: true,
            unsortedEnabled = preferences[UNSORTED_ENABLED] ?: true,
            showLibraryBottomNavigation = preferences[SHOW_LIBRARY_BOTTOM_NAVIGATION] ?: true,
            coverDisplayMode = CoverDisplayMode.entries.firstOrNull {
                it.name == preferences[COVER_DISPLAY_MODE]
            } ?: CoverDisplayMode.Contain,
            formatPreferences = preferences[FORMAT_PREFERENCES]?.let {
                try {
                    PreferenceJson.decodeFromString<FormatPreferences>(it)
                } catch (_: kotlinx.serialization.SerializationException) {
                    FormatPreferences()
                } catch (_: IllegalArgumentException) {
                    FormatPreferences()
                }
            } ?: FormatPreferences(),
        )
    }
}
