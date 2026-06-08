package com.example.readerapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.example.readerapp.ui.theme.custom.PineTheme
import com.example.readerapp.ui.theme.custom.NeutralTheme

object ThemeRegistry {
    val themes = listOf(
        PineTheme,
        NeutralTheme
    )

    fun getTheme(name: String): AppThemeColors {
        return themes.find { it.name.equals(name, ignoreCase = true) } ?: themes.first()
    }

    fun getThemeNames(): List<String> {
        return themes.map { it.name }
    }
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPalette: String = "Dynamic",
    themeContrast: String = "Standard",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Check if dynamic color is supported (Android 12+)
    val isDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val resolvedPalette = if (colorPalette == "Dynamic" && !isDynamicSupported) {
        "Pine"
    } else {
        colorPalette
    }

    val colorScheme = when {
        resolvedPalette == "Dynamic" -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val theme = ThemeRegistry.getTheme(resolvedPalette)
            if (darkTheme) {
                when (themeContrast) {
                    "Medium" -> theme.mediumContrastDarkColorScheme
                    "High" -> theme.highContrastDarkColorScheme
                    else -> theme.darkColorScheme
                }
            } else {
                when (themeContrast) {
                    "Medium" -> theme.mediumContrastLightColorScheme
                    "High" -> theme.highContrastLightColorScheme
                    else -> theme.lightColorScheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
