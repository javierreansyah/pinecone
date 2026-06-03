package com.example.readerapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun ReaderTheme(
    readerBackgroundColor: Color,
    content: @Composable () -> Unit
) {
    val isDarkBackground = readerBackgroundColor.luminance() < 0.5f
    
    // We create a color scheme specifically tuned to contrast with the reader background.
    // This is used for overlays, top/bottom bars that sit directly ON TOP of the reader.
    val colorScheme = if (isDarkBackground) {
        darkColorScheme(
            background = Color.Transparent,
            surface = readerBackgroundColor, // Matches reader background
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.7f),
            primary = primaryDark,
            onPrimary = onPrimaryDark
        )
    } else {
        lightColorScheme(
            background = Color.Transparent,
            surface = readerBackgroundColor, // Matches reader background
            onSurface = Color.Black,
            onSurfaceVariant = Color.Black.copy(alpha = 0.7f),
            primary = primaryLight,
            onPrimary = onPrimaryLight
        )
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
