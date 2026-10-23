package com.javierreansyah.pinecone.ui.root

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMiniPlayerGeometryTest {
    @Test
    fun `mini-player height includes the extra inset for its progress track`() {
        assertEquals(4.dp, MiniPlayerProgressHeight)
        assertEquals(88.dp, MiniPlayerHeight)
        assertEquals(
            MiniPlayerArtworkSize + MiniPlayerArtworkInset * 2 +
                    MiniPlayerProgressHeight + MiniPlayerOuterVerticalPadding * 2,
            MiniPlayerHeight,
        )
    }

    @Test
    fun `outer rounding follows artwork rounding plus the artwork inset`() {
        assertEquals(MiniPlayerArtworkRadius + MiniPlayerArtworkInset, MiniPlayerContainerRadius)
        assertEquals(20.dp, MiniPlayerContainerRadius)
    }

    @Test
    fun `root mini-player anchors to the window rather than a navigation scene`() {
        val handle = AudioPlayerHandle(height = 284, bottomInset = 0, navigationBarsPadding = true)
        assertEquals(
            Rect(0f, 2116f, 1080f, 2400f),
            audioMiniPlayerBounds(IntSize(1080, 2400), handle)
        )
    }

    @Test
    fun `library mini-player remains above bottom navigation at full window width`() {
        val handle =
            AudioPlayerHandle(height = 221, bottomInset = 231, navigationBarsPadding = false)
        assertEquals(
            Rect(0f, 1948f, 1080f, 2169f),
            audioMiniPlayerBounds(IntSize(1080, 2400), handle)
        )
    }

    @Test
    fun `bounds adapt to resizing and remain valid before layout`() {
        val handle = AudioPlayerHandle(height = 84, bottomInset = 24, navigationBarsPadding = false)
        assertEquals(Rect(0f, 292f, 600f, 376f), audioMiniPlayerBounds(IntSize(600, 400), handle))
        assertEquals(Rect.Zero, audioMiniPlayerBounds(IntSize.Zero, handle))
    }
}
