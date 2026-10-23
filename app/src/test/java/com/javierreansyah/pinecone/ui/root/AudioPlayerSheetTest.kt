package com.javierreansyah.pinecone.ui.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayerSheetTest {
    @Test
    fun `predictive back moves the player down toward its mini-player anchor`() {
        assertEquals(0f, audioPredictiveBackOffset(0f, 800f, 0f), 0f)
        assertEquals(400f, audioPredictiveBackOffset(0f, 800f, 0.5f), 0f)
        assertEquals(800f, audioPredictiveBackOffset(0f, 800f, 1f), 0f)
    }

    @Test
    fun `predictive back starts at the current sheet position and can reverse`() {
        assertEquals(200f, audioPredictiveBackOffset(200f, 800f, 0f), 0f)
        assertEquals(500f, audioPredictiveBackOffset(200f, 800f, 0.5f), 0f)
        assertEquals(350f, audioPredictiveBackOffset(200f, 800f, 0.25f), 0f)
        assertEquals(200f, audioPredictiveBackOffset(200f, 800f, 0f), 0f)
    }

    @Test
    fun `predictive back progress stays within its gesture range`() {
        assertEquals(0f, audioPredictiveBackOffset(0f, 800f, -1f), 0f)
        assertEquals(800f, audioPredictiveBackOffset(0f, 800f, 2f), 0f)
        assertEquals(0f, audioPredictiveBackOffset(0f, 800f, Float.NaN), 0f)
    }

    @Test
    fun `drag progress runs from the mini-player to the expanded sheet`() {
        assertEquals(0f, audioSheetExpansion(800f, 800f), 0f)
        assertEquals(0.5f, audioSheetExpansion(400f, 800f), 0f)
        assertEquals(1f, audioSheetExpansion(0f, 800f), 0f)
    }

    @Test
    fun `progress is bounded and safe before layout`() {
        assertEquals(0f, audioSheetExpansion(Float.NaN, 0f), 0f)
        assertEquals(0f, audioSheetExpansion(0f, Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, audioSheetExpansion(900f, 800f), 0f)
        assertEquals(1f, audioSheetExpansion(-20f, 800f), 0f)
    }

    @Test
    fun `slow drags settle to the nearer end`() {
        assertEquals(AudioSheetValue.Collapsed, audioSheetTarget(600f, 800f, 0f, 125f))
        assertEquals(AudioSheetValue.Expanded, audioSheetTarget(200f, 800f, 0f, 125f))
    }

    @Test
    fun `upward and downward flings override the distance threshold`() {
        assertEquals(AudioSheetValue.Expanded, audioSheetTarget(750f, 800f, -500f, 125f))
        assertEquals(AudioSheetValue.Collapsed, audioSheetTarget(50f, 800f, 500f, 125f))
    }

    @Test
    fun `mini-player disappears within the first ten percent of expansion`() {
        assertEquals(1f, audioSheetOpacity(0f).miniPlayer, 0f)
        assertEquals(0.5f, audioSheetOpacity(0.05f).miniPlayer, 0.0001f)
        assertEquals(0f, audioSheetOpacity(0.10f).miniPlayer, 0f)
        assertEquals(0f, audioSheetOpacity(1f).miniPlayer, 0f)
    }

    @Test
    fun `direct audiobook opening does not flash the mini-player`() {
        listOf(0f, 0.025f, 0.05f, 0.5f, 1f).forEach { expansion ->
            assertEquals(0f, audioSheetOpacity(expansion, suppressMiniPlayer = true).miniPlayer, 0f)
            assertEquals(
                audioSheetOpacity(expansion).playerContent,
                audioSheetOpacity(expansion, suppressMiniPlayer = true).playerContent,
                0f,
            )
        }
        assertEquals(1f, audioSheetOpacity(0f).miniPlayer, 0f)
    }

    @Test
    fun `player content is fully visible by one quarter of expansion`() {
        assertEquals(0f, audioSheetOpacity(0f).playerContent, 0f)
        assertEquals(0.5f, audioSheetOpacity(0.125f).playerContent, 0f)
        assertEquals(1f, audioSheetOpacity(0.25f).playerContent, 0f)
        assertEquals(1f, audioSheetOpacity(0.75f).playerContent, 0f)
    }

    @Test
    fun `background eases out quickly and softens toward fully expanded`() {
        assertEquals(0f, audioSheetOpacity(0f).background, 0f)
        assertEquals(0.578125f, audioSheetOpacity(0.25f).background, 0f)
        assertEquals(0.875f, audioSheetOpacity(0.5f).background, 0f)
        assertEquals(0.984375f, audioSheetOpacity(0.75f).background, 0f)
        assertTrue(audioSheetOpacity(0.99f).background < 1f)
        assertEquals(1f, audioSheetOpacity(1f).background, 0f)
        assertEquals(1f, audioSheetOpacity(0.25f).playerContent, 0f)
    }

    @Test
    fun `artwork fades in after the mini-player and is fully visible at half expansion`() {
        assertEquals(0f, audioSheetOpacity(0.10f).artwork, 0f)
        assertEquals(0.5f, audioSheetOpacity(0.30f).artwork, 0.0001f)
        assertEquals(1f, audioSheetOpacity(0.50f).artwork, 0f)
        assertEquals(1f, audioSheetOpacity(1f).artwork, 0f)
    }

    @Test
    fun `top bar appears later than the artwork`() {
        assertEquals(0f, audioSheetOpacity(0.50f).topBar, 0f)
        assertEquals(0.5f, audioSheetOpacity(0.675f).topBar, 0.0001f)
        assertEquals(1f, audioSheetOpacity(0.85f).topBar, 0f)
        assertEquals(1f, audioSheetOpacity(1f).topBar, 0f)
    }

    @Test
    fun `opacity remains bounded before layout and outside the drag range`() {
        assertEquals(AudioSheetOpacity(1f, 0f, 0f, 0f, 0f), audioSheetOpacity(Float.NaN))
        assertEquals(AudioSheetOpacity(1f, 0f, 0f, 0f, 0f), audioSheetOpacity(-1f))
        assertEquals(AudioSheetOpacity(0f, 1f, 1f, 1f, 1f), audioSheetOpacity(2f))
    }

    @Test
    fun `left and right dismissal fade equally and clamp at the screen width`() {
        assertEquals(0f, audioSwipeDismissProgress(0f, 400f), 0f)
        assertEquals(0.5f, audioSwipeDismissProgress(-200f, 400f), 0f)
        assertEquals(0.5f, audioSwipeDismissProgress(200f, 400f), 0f)
        assertEquals(1f, audioSwipeDismissProgress(-600f, 400f), 0f)
        assertEquals(1f, audioSwipeDismissProgress(600f, 400f), 0f)
        assertEquals(0f, audioSwipeDismissProgress(Float.NaN, 0f), 0f)
    }
}
