package com.example.readerapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils

@Composable
fun ReaderTheme(
    readerBackgroundColor: Color,
    content: @Composable () -> Unit
) {
    val isDarkBackground = ColorUtils.calculateLuminance(readerBackgroundColor.value.toInt()) < 0.5
    
    // We create a color scheme specifically tuned to contrast with the reader background.
    // This is used for overlays, top/bottom bars that sit directly ON TOP of the reader.
    val colorScheme = if (isDarkBackground) {
        darkColorScheme(
            background = Color.Transparent,
            surface = readerBackgroundColor, // Matches reader background
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.7f),
            primary = DarkPrimary,
            onPrimary = DarkOnPrimary
        )
    } else {
        lightColorScheme(
            background = Color.Transparent,
            surface = readerBackgroundColor, // Matches reader background
            onSurface = Color.Black,
            onSurfaceVariant = Color.Black.copy(alpha = 0.7f),
            primary = LightPrimary,
            onPrimary = LightOnPrimary
        )
    }

    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalAppShapes provides AppShapes()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
