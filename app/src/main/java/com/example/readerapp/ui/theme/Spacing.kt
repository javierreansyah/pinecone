package com.example.readerapp.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val massive: Dp = 48.dp,
    
    // Specific component spacings
    val screenPadding: Dp = 16.dp,
    val itemSpacing: Dp = 12.dp,
    val dialogPadding: Dp = 24.dp,
    val bottomSheetPeek: Dp = 56.dp
)

val LocalAppSpacing = androidx.compose.runtime.staticCompositionLocalOf {
    AppSpacing()
}
