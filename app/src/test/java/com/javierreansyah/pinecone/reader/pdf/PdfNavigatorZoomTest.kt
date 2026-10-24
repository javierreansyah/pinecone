package com.javierreansyah.pinecone.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfNavigatorZoomTest {
    private val viewport = IntSize(600, 800)

    @Test
    fun pinchKeepsTheDocumentPointUnderTheFingers() {
        val result = pdfZoomTransform(
            PdfZoomTransform(), 2f, Offset.Zero, Offset(150f, 200f), viewport,
        )
        assertEquals(2f, result.scale)
        assertEquals(Offset(-150f, -200f), result.offset)
    }

    @Test
    fun panIsClampedToTheWholeNavigatorBounds() {
        val result = pdfZoomTransform(
            PdfZoomTransform(2f), 1f, Offset(-2000f, 2000f), Offset.Zero, viewport,
        )
        assertEquals(Offset(-600f, 0f), result.offset)
    }

    @Test
    fun zoomingOutResetsPanAndCannotShrinkBelowFit() {
        val result = pdfZoomTransform(
            PdfZoomTransform(2f, Offset(-200f, -300f)),
            .1f, Offset.Zero, Offset(300f, 400f), viewport,
        )
        assertEquals(1f, result.scale)
        assertEquals(Offset.Zero, result.offset)
    }

    @Test
    fun zoomHasAnUpperLimit() {
        val result = pdfZoomTransform(
            PdfZoomTransform(3f), 2f, Offset.Zero, Offset.Zero, viewport,
        )
        assertEquals(4f, result.scale)
    }
}
