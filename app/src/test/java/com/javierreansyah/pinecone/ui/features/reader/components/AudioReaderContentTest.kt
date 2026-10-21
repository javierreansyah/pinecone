package com.javierreansyah.pinecone.ui.features.reader.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioReaderContentTest {
    @Test
    fun `seeking clamps touches outside the track`() {
        assertEquals(0f, audioSeekFraction(-20f, 200f, false), 0f)
        assertEquals(1f, audioSeekFraction(250f, 200f, false), 0f)
        assertEquals(0.25f, audioSeekFraction(50f, 200f, false), 0f)
    }

    @Test
    fun `RTL seeking follows the mirrored progress indicator`() {
        assertEquals(1f, audioSeekFraction(0f, 200f, true), 0f)
        assertEquals(0f, audioSeekFraction(200f, 200f, true), 0f)
        assertEquals(0.75f, audioSeekFraction(50f, 200f, true), 0f)
    }

    @Test
    fun `seeking before measurement cannot produce an invalid position`() {
        assertEquals(0f, audioSeekFraction(10f, 0f, false), 0f)
        assertEquals(0f, audioSeekFraction(Float.NaN, 200f, false), 0f)
        assertEquals(0f, audioSeekFraction(10f, Float.POSITIVE_INFINITY, false), 0f)
    }

    @Test
    fun `audiobook durations retain hours and never show negative time`() {
        assertEquals("0:00", formatAudioTime(-1))
        assertEquals("2:12", formatAudioTime(132))
        assertEquals("1:00:00", formatAudioTime(3600))
        assertEquals("12:34:56", formatAudioTime(45296))
    }
}
