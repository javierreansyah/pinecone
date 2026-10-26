package com.javierreansyah.pinecone.data.local.preferences

import kotlinx.serialization.Serializable

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

/** Persisted settings contract shared by storage and presentation; keep JSON fields compatible. */
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
    val showLibraryBottomNavigation: Boolean = true,
    val coverDisplayMode: CoverDisplayMode = CoverDisplayMode.Contain,
)
