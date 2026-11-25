package com.javierreansyah.pinecone.ui.features.reader.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioProgressGradientTest {
    @Test
    fun audioProgressGradientColors_sweepsHueAnalogouslyWithBalancedChroma() {
        val primaryGreen = Color(Hct.from(hue = 138.0, chroma = 32.0, tone = 80.0).toInt())
        val tertiaryAqua = Color(Hct.from(hue = 198.0, chroma = 28.0, tone = 80.0).toInt())

        val stops = audioProgressGradientColors(primary = primaryGreen, tertiary = tertiaryAqua)

        assertEquals(3, stops.size)
        val hcts = stops.map { Hct.fromInt(it.toArgb()) }
        assertTrue(
            "Expected hue to sweep from primary (~138) through mid to glow (~189), got ${hcts.map { it.hue }}",
            (hcts[1].hue > hcts[0].hue) && (hcts[2].hue > hcts[1].hue) && (hcts[2].hue < 200.0),
        )
        assertTrue(
            "Expected dark-mode stops to stay luminous (72..88), got ${hcts.map { it.tone }}",
            hcts.all { it.tone in 72.0..88.0 })
        assertTrue(
            "Expected bounded Material chroma (<= 59), got ${hcts.map { it.chroma }}",
            hcts.all { it.chroma <= 59.0 })
        assertTrue(
            "Expected rich start stop to be more saturated than glow stop",
            hcts[0].chroma > hcts[2].chroma
        )
    }

    @Test
    fun audioProgressGradientColors_keepsLightContainerCloseToBaseTone() {
        val pastelTertiaryContainer =
            Color(Hct.from(hue = 100.0, chroma = 30.0, tone = 90.0).toInt())
        val primaryGreen = Color(Hct.from(hue = 138.0, chroma = 32.0, tone = 80.0).toInt())

        val stops = audioProgressGradientColors(
            primary = pastelTertiaryContainer,
            tertiary = primaryGreen,
        )
        val hcts = stops.map { Hct.fromInt(it.toArgb()) }

        assertTrue(
            "Expected container stops to stay close to base tone (81..95), got ${hcts.map { it.tone }}",
            hcts.all { it.tone in 81.0..95.0 })
        assertTrue(
            "Expected bounded chroma without neon overshoot (<= 59), got ${hcts[0].chroma}",
            hcts[0].chroma <= 59.0
        )
    }

    @Test
    fun audioProgressGradientColors_preservesNeutralityForMonochromePrimary() {
        val neutralPrimary = Color(0.78f, 0.78f, 0.78f, 0.9f)
        val accentTertiary = Color(Hct.from(hue = 210.0, chroma = 40.0, tone = 70.0).toInt())

        val stops = audioProgressGradientColors(primary = neutralPrimary, tertiary = accentTertiary)

        assertEquals(3, stops.size)
        assertTrue(stops.all { Hct.fromInt(it.toArgb()).chroma < 4.0 })
        assertTrue(stops.all { abs(it.alpha - neutralPrimary.alpha) < 0.0001f })
    }

    @Test
    fun audioProgressGradientColors_capsHueSweepForComplementaryTertiary() {
        val primary = Color(Hct.from(hue = 40.0, chroma = 36.0, tone = 42.0).toInt())
        val complementary = Color(Hct.from(hue = 220.0, chroma = 36.0, tone = 42.0).toInt())

        val stops = audioProgressGradientColors(primary = primary, tertiary = complementary)
        val firstHue = Hct.fromInt(stops.first().toArgb()).hue
        val lastHue = Hct.fromInt(stops.last().toArgb()).hue
        val diff = abs(((lastHue - firstHue + 540.0) % 360.0) - 180.0)

        assertTrue("Expected capped analogous hue sweep <= 55 deg, got $diff", diff <= 55.0)
    }
}
