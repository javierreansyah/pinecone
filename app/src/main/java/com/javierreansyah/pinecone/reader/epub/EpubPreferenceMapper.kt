package com.javierreansyah.pinecone.reader.epub

import androidx.core.graphics.toColorInt
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
fun ReaderSettings.toEpubPreferences(isSystemDark: Boolean): EpubPreferences {
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
        // The controller applies this independently of Readium's dark-theme-only filtering.
        imageFilter = null,
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
