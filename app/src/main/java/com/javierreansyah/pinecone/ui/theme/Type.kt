package com.javierreansyah.pinecone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.javierreansyah.pinecone.R

private val RobotoWeights = listOf(
    FontWeight.Thin,
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black,
)

/**
 * Bundled Roboto variable typeface (OFL-1.1), Material 3's default font family.
 *
 * Explicitly supplying Roboto prevents Android OEM system font settings from overriding the
 * Compose Material 3 type scale (whose default [FontFamily.SansSerif] resolves to the device font)
 * while remaining offline-safe and independent of Google Play Services.
 */
val RobotoFontFamily: FontFamily = FontFamily(
    RobotoWeights.flatMap { weight ->
        val variation = FontVariation.Settings(FontVariation.weight(weight.weight))
        listOf(
            Font(
                resId = R.font.roboto,
                weight = weight,
                style = FontStyle.Normal,
                variationSettings = variation,
            ),
            Font(
                resId = R.font.roboto_italic,
                weight = weight,
                style = FontStyle.Italic,
                variationSettings = variation,
            ),
        )
    },
)

/** Material 3 Expressive default type scale (15 baseline + 15 emphasized styles) using Roboto. */
val AppTypography: Typography = Typography(fontFamily = RobotoFontFamily)
