package com.javierreansyah.pinecone.ui.features.reader

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings

data class PineconeThemeColors(
    val backgroundColor: Color,
    val textColor: Color,
    val backgroundColorInt: Int,
    val isDarkTheme: Boolean,
)

/** Only settings that affect the reading colors and the independent toolbar theme. */
internal data class ReaderThemeSettings(
    val themeMode: String = "System",
    val preset: String = "Auto",
    val customBackgroundColor: String = "#FFFFFF",
    val customTextColor: String = "#000000",
    val customUiTheme: String = "Setting",
) {
    fun isUiDark(isSystemDark: Boolean): Boolean {
        val mode = when (customUiTheme) {
            "Setting", "setting" -> themeMode
            else -> customUiTheme
        }
        return when (mode) {
            "Dark" -> true
            "Light" -> false
            else -> isSystemDark
        }
    }

    fun resolveColors(isSystemDark: Boolean): PineconeThemeColors {
        val isDark = isUiDark(isSystemDark)
        val background = when (preset) {
            "Light" -> WHITE
            "Warm" -> WARM_BACKGROUND
            "Dark" -> BLACK
            "Auto" -> if (isDark) BLACK else WHITE
            else -> customBackgroundColor.colorOrDefault(WHITE)
        }
        val text = when (preset) {
            "Light" -> BLACK
            "Warm" -> WARM_TEXT
            "Dark" -> WHITE
            "Auto" -> if (isDark) WHITE else BLACK
            else -> customTextColor.colorOrDefault(if (isDark) WHITE else BLACK)
        }
        return PineconeThemeColors(
            backgroundColor = Color(background),
            textColor = Color(text),
            backgroundColorInt = background,
            isDarkTheme = isDark,
        )
    }

    private companion object {
        const val WHITE: Int = -1
        const val BLACK: Int = -16777216
        const val WARM_BACKGROUND: Int = 0xFFFAF4E8.toInt()
        const val WARM_TEXT: Int = 0xFF121212.toInt()
    }
}

internal fun ReaderSettings.toReaderThemeSettings(): ReaderThemeSettings = ReaderThemeSettings(
    themeMode = themeMode,
    preset = pineconeThemePreset,
    customBackgroundColor = customBackgroundColor,
    customTextColor = customTextColor,
    customUiTheme = customThemes.firstOrNull { it.name == pineconeThemePreset }?.uiTheme
        ?: "Setting",
)

internal fun String.colorOrDefault(fallback: Int): Int = try {
    toColorInt()
} catch (_: IllegalArgumentException) {
    fallback
}
