package com.javierreansyah.pinecone.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentedButtonGroupTest {
    @Test
    fun distributesExtraSpaceEvenlyToFillTargetWidth() {
        val natural = intArrayOf(120, 150, 180, 180, 130)
        val spacing = 6
        val targetWidth = 1000

        val result = distributeConnectedWidths(
            naturalWidths = natural,
            spacingPx = spacing,
            targetWidth = targetWidth,
        )

        assertEquals(targetWidth, result.sum() + spacing * (natural.size - 1))
        assertArrayEquals(intArrayOf(164, 193, 223, 223, 173), result)
    }

    @Test
    fun preservesNaturalWidthsWhenOverflowingTargetWidth() {
        val natural = intArrayOf(160, 200, 220, 220, 160)
        val spacing = 6

        val result = distributeConnectedWidths(
            naturalWidths = natural,
            spacingPx = spacing,
            targetWidth = 800,
        )

        assertArrayEquals(natural, result)
    }

    @Test
    fun handlesEmptyInput() {
        assertArrayEquals(
            intArrayOf(),
            distributeConnectedWidths(intArrayOf(), spacingPx = 6, targetWidth = 800),
        )
    }
}
