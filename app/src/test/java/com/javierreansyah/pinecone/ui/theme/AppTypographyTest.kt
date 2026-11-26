package com.javierreansyah.pinecone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTypographyTest {

    @Test
    fun allExpressiveStylesUseBundledRobotoAndPreserveMaterial3Tokens() {
        val m3Expressive = Typography(fontFamily = FontFamily.SansSerif)
        val pairs: List<Pair<String, Pair<TextStyle, TextStyle>>> = listOf(
            "displayLarge" to (AppTypography.displayLarge to m3Expressive.displayLarge),
            "displayMedium" to (AppTypography.displayMedium to m3Expressive.displayMedium),
            "displaySmall" to (AppTypography.displaySmall to m3Expressive.displaySmall),
            "headlineLarge" to (AppTypography.headlineLarge to m3Expressive.headlineLarge),
            "headlineMedium" to (AppTypography.headlineMedium to m3Expressive.headlineMedium),
            "headlineSmall" to (AppTypography.headlineSmall to m3Expressive.headlineSmall),
            "titleLarge" to (AppTypography.titleLarge to m3Expressive.titleLarge),
            "titleMedium" to (AppTypography.titleMedium to m3Expressive.titleMedium),
            "titleSmall" to (AppTypography.titleSmall to m3Expressive.titleSmall),
            "bodyLarge" to (AppTypography.bodyLarge to m3Expressive.bodyLarge),
            "bodyMedium" to (AppTypography.bodyMedium to m3Expressive.bodyMedium),
            "bodySmall" to (AppTypography.bodySmall to m3Expressive.bodySmall),
            "labelLarge" to (AppTypography.labelLarge to m3Expressive.labelLarge),
            "labelMedium" to (AppTypography.labelMedium to m3Expressive.labelMedium),
            "labelSmall" to (AppTypography.labelSmall to m3Expressive.labelSmall),
            "displayLargeEmphasized" to (AppTypography.displayLargeEmphasized to m3Expressive.displayLargeEmphasized),
            "displayMediumEmphasized" to (AppTypography.displayMediumEmphasized to m3Expressive.displayMediumEmphasized),
            "displaySmallEmphasized" to (AppTypography.displaySmallEmphasized to m3Expressive.displaySmallEmphasized),
            "headlineLargeEmphasized" to (AppTypography.headlineLargeEmphasized to m3Expressive.headlineLargeEmphasized),
            "headlineMediumEmphasized" to (AppTypography.headlineMediumEmphasized to m3Expressive.headlineMediumEmphasized),
            "headlineSmallEmphasized" to (AppTypography.headlineSmallEmphasized to m3Expressive.headlineSmallEmphasized),
            "titleLargeEmphasized" to (AppTypography.titleLargeEmphasized to m3Expressive.titleLargeEmphasized),
            "titleMediumEmphasized" to (AppTypography.titleMediumEmphasized to m3Expressive.titleMediumEmphasized),
            "titleSmallEmphasized" to (AppTypography.titleSmallEmphasized to m3Expressive.titleSmallEmphasized),
            "bodyLargeEmphasized" to (AppTypography.bodyLargeEmphasized to m3Expressive.bodyLargeEmphasized),
            "bodyMediumEmphasized" to (AppTypography.bodyMediumEmphasized to m3Expressive.bodyMediumEmphasized),
            "bodySmallEmphasized" to (AppTypography.bodySmallEmphasized to m3Expressive.bodySmallEmphasized),
            "labelLargeEmphasized" to (AppTypography.labelLargeEmphasized to m3Expressive.labelLargeEmphasized),
            "labelMediumEmphasized" to (AppTypography.labelMediumEmphasized to m3Expressive.labelMediumEmphasized),
            "labelSmallEmphasized" to (AppTypography.labelSmallEmphasized to m3Expressive.labelSmallEmphasized),
        )

        pairs.forEach { (name, styles) ->
            val (actual, expected) = styles
            assertEquals("$name should use RobotoFontFamily", RobotoFontFamily, actual.fontFamily)
            assertEquals(
                "$name should preserve M3 Expressive token defaults",
                expected.copy(fontFamily = RobotoFontFamily),
                actual,
            )
        }
    }

    @Test
    fun emphasizedStylesAreHeavierThanBaselineStyles() {
        val pairs = listOf(
            "displayLarge" to (AppTypography.displayLarge to AppTypography.displayLargeEmphasized),
            "displayMedium" to (AppTypography.displayMedium to AppTypography.displayMediumEmphasized),
            "displaySmall" to (AppTypography.displaySmall to AppTypography.displaySmallEmphasized),
            "headlineLarge" to (AppTypography.headlineLarge to AppTypography.headlineLargeEmphasized),
            "headlineMedium" to (AppTypography.headlineMedium to AppTypography.headlineMediumEmphasized),
            "headlineSmall" to (AppTypography.headlineSmall to AppTypography.headlineSmallEmphasized),
            "titleLarge" to (AppTypography.titleLarge to AppTypography.titleLargeEmphasized),
            "titleMedium" to (AppTypography.titleMedium to AppTypography.titleMediumEmphasized),
            "titleSmall" to (AppTypography.titleSmall to AppTypography.titleSmallEmphasized),
            "bodyLarge" to (AppTypography.bodyLarge to AppTypography.bodyLargeEmphasized),
            "bodyMedium" to (AppTypography.bodyMedium to AppTypography.bodyMediumEmphasized),
            "bodySmall" to (AppTypography.bodySmall to AppTypography.bodySmallEmphasized),
            "labelLarge" to (AppTypography.labelLarge to AppTypography.labelLargeEmphasized),
            "labelMedium" to (AppTypography.labelMedium to AppTypography.labelMediumEmphasized),
            "labelSmall" to (AppTypography.labelSmall to AppTypography.labelSmallEmphasized),
        )

        pairs.forEach { (name, styles) ->
            val (baseline, emphasized) = styles
            val baselineWeight = requireNotNull(baseline.fontWeight).weight
            val emphasizedWeight = requireNotNull(emphasized.fontWeight).weight
            assertTrue(
                "${name}Emphasized ($emphasizedWeight) should be heavier than $name ($baselineWeight)",
                emphasizedWeight > baselineWeight,
            )
        }
    }

    @Test
    fun robotoFontFamilyCoversAllStandardWeightsInNormalAndItalic() {
        val family = RobotoFontFamily as FontListFontFamily
        val expectedWeights = listOf(
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
        for (weight in expectedWeights) {
            assertTrue(
                "Missing Normal font for $weight",
                family.fonts.any { it.weight == weight && it.style == FontStyle.Normal },
            )
            assertTrue(
                "Missing Italic font for $weight",
                family.fonts.any { it.weight == weight && it.style == FontStyle.Italic },
            )
        }
    }
}
