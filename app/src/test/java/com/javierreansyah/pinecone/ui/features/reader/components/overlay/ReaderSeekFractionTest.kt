package com.javierreansyah.pinecone.ui.features.reader.components.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSeekFractionTest {
    @Test
    fun `seek coordinates follow layout direction`() {
        assertEquals(0.25f, readerSeekFraction(25f, 100f, false), 0.0001f)
        assertEquals(0.75f, readerSeekFraction(25f, 100f, true), 0.0001f)
    }

    @Test
    fun `seeking clamps at both ends in either direction`() {
        assertEquals(0f, readerSeekFraction(-10f, 100f, false), 0.0001f)
        assertEquals(1f, readerSeekFraction(110f, 100f, false), 0.0001f)
        assertEquals(1f, readerSeekFraction(-10f, 100f, true), 0.0001f)
        assertEquals(0f, readerSeekFraction(110f, 100f, true), 0.0001f)
    }

    @Test
    fun `unmeasured track never produces an invalid fraction`() {
        assertEquals(0f, readerSeekFraction(0f, 0f, false), 0.0001f)
        assertEquals(1f, readerSeekFraction(10f, 0f, false), 0.0001f)
    }
}
