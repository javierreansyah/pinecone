package com.javierreansyah.pinecone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.core.graphics.toColorInt
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPalette: String = "Dynamic",
    themeContrast: String = "Standard",
    content: @Composable () -> Unit,
) {
    // Use only the device accent as input; MaterialKolor defines every scheme role.
    // A fixed accent tone also keeps the seed consistent between light and dark mode.
    val systemSeedColor = colorResource(android.R.color.system_accent1_500)

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
        seedColor = if (seedColor == Color.White) systemSeedColor else seedColor,
    )

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
