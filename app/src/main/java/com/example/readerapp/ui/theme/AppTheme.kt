package com.example.readerapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPalette: String = "Dynamic",
    themeContrast: String = "Standard",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val seedColor = remember(colorPalette) {
        if (colorPalette == "Dynamic") {
            Color.White
        } else {
            try {
                Color(colorPalette.toColorInt())
            } catch (_: Exception) {
                Color.White
            }
        }
    }

    val systemColorScheme = remember(darkTheme) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    val contrastLevel = remember(themeContrast) {
        when (themeContrast) {
            "Medium" -> 0.5
            "High" -> 1.0
            else -> 0.0
        }
    }

    val dynamicColorScheme = rememberDynamicColorScheme(
        isDark = darkTheme,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        contrastLevel = contrastLevel,
        seedColor = if (seedColor == Color.White) systemColorScheme.primary else seedColor,
    )

    val resolvedColorScheme =
        if (seedColor == Color.White) systemColorScheme else dynamicColorScheme

    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = resolvedColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
