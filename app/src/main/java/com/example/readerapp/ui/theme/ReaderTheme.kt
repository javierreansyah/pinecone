package com.example.readerapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun ReaderTheme(
    readerBackgroundColor: Color,
    content: @Composable () -> Unit
) {
    val isDarkBackground = readerBackgroundColor.luminance() < 0.5f

    // Check if the parent theme is dark or light based on background luminance
    val parentIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Resolve primary/onPrimary to respect light/dark mismatch between reader page and app theme
    val primaryColor = when {
        isDarkBackground && parentIsDark -> MaterialTheme.colorScheme.primary
        isDarkBackground && !parentIsDark -> MaterialTheme.colorScheme.inversePrimary
        !isDarkBackground && parentIsDark -> MaterialTheme.colorScheme.inversePrimary
        else -> MaterialTheme.colorScheme.primary
    }

    val onPrimaryColor = if (primaryColor.luminance() < 0.5f) Color.White else Color.Black

    val colorScheme = if (isDarkBackground) {
        darkColorScheme(
            background = Color.Transparent,
            surface = readerBackgroundColor, // Matches reader background
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.7f),
            primary = primaryColor,
            onPrimary = onPrimaryColor
        )
    } else {
        lightColorScheme(
            background = Color.Transparent,
            surface = readerBackgroundColor, // Matches reader background
            onSurface = Color.Black,
            onSurfaceVariant = Color.Black.copy(alpha = 0.7f),
            primary = primaryColor,
            onPrimary = onPrimaryColor
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
