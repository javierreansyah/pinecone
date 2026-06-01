package com.example.readerapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

object AppTheme {
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = androidx.compose.material3.MaterialTheme.typography

    val shapes: AppShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalAppShapes.current

    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = androidx.compose.material3.MaterialTheme.colorScheme
}
