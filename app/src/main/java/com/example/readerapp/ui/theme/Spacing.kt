package com.example.readerapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val space4: Dp = 4.dp,
    val space8: Dp = 8.dp,
    val space12: Dp = 12.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space28: Dp = 28.dp,
    val space32: Dp = 32.dp,
    val space36: Dp = 36.dp,
    val space40: Dp = 40.dp,
    val space48: Dp = 48.dp,
    val space56: Dp = 56.dp,
    val space64: Dp = 64.dp,

    // Component specific standard paddings
    val screenPadding: Dp = 16.dp,
    val itemSpacing: Dp = 12.dp,
    val dialogPadding: Dp = 24.dp,
    val bottomSheetPeek: Dp = 56.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
