package com.javierreansyah.pinecone.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun tilePlanIsSkippedWhenBaseBitmapMatchesZoomedResolution() {
        val pageBounds = Rect(0f, 0f, 600f, 800f)
        val plan = calculatePdfTilePlan(
            pageBoundsInContent = pageBounds,
            viewportSize = viewport,
            transform = PdfZoomTransform(offset = Offset.Zero),
            baseBitmapWidth = 600,
        )
        assertNull(plan)
    }

    @Test
    fun tilePlanRendersVisibleSliceWithOverscanAtZoomedResolution() {
        val pageBounds = Rect(0f, 0f, 1000f, 1400f)
        val screen = IntSize(1000, 1400)
        // 3x zoom centered around (500, 700) -> visible unzoomed region is [333..667] x [467..933]
        val plan = calculatePdfTilePlan(
            pageBoundsInContent = pageBounds,
            viewportSize = screen,
            transform = PdfZoomTransform(scale = 3f, offset = Offset(-1000f, -1400f)),
            baseBitmapWidth = 1000,
            overscanFraction = 0.10f,
        )
        assertNotNull(plan)
        val nonNull = requireNotNull(plan)
        assertTrue(nonNull.request.region.contains(nonNull.visibleRegion))
        assertEquals(1200, nonNull.request.widthPx)
        assertEquals(1680, nonNull.request.heightPx)
        assertEquals(3000f, nonNull.request.fullPageWidthPx, 1f)
    }

    @Test
    fun tilePlanReturnsNullWhenPageIsOffScreenWhileZoomed() {
        val pageBounds = Rect(0f, 1500f, 1000f, 2900f)
        val plan = calculatePdfTilePlan(
            pageBoundsInContent = pageBounds,
            viewportSize = IntSize(1000, 1400),
            transform = PdfZoomTransform(scale = 3f, offset = Offset.Zero),
            baseBitmapWidth = 1000,
        )
        assertNull(plan)
    }

    @Test
    fun tilePlanCapsDimensionsAndPixelAreaForLargeViewports() {
        val pageBounds = Rect(0f, 0f, 2000f, 3000f)
        val plan = calculatePdfTilePlan(
            pageBoundsInContent = pageBounds,
            viewportSize = IntSize(2000, 3000),
            transform = PdfZoomTransform(scale = 4f, offset = Offset.Zero),
            baseBitmapWidth = 1800,
            overscanFraction = 0.15f,
        )
        val nonNull = requireNotNull(plan)
        assertTrue(nonNull.request.widthPx <= 3200)
        assertTrue(nonNull.request.heightPx <= 3200)
        assertTrue(nonNull.request.widthPx.toLong() * nonNull.request.heightPx.toLong() <= 4_500_000L)
    }

    @Test
    fun existingTileIsReusedWhilePanStaysInsideOverscanAndScaleIsClose() {
        val pageBounds = Rect(0f, 0f, 1000f, 1400f)
        val screen = IntSize(1000, 1400)
        val initial = requireNotNull(
            calculatePdfTilePlan(
                pageBoundsInContent = pageBounds,
                viewportSize = screen,
                transform = PdfZoomTransform(scale = 3f, offset = Offset(-1000f, -1400f)),
                baseBitmapWidth = 1000,
            ),
        )
        val smallPan = requireNotNull(
            calculatePdfTilePlan(
                pageBoundsInContent = pageBounds,
                viewportSize = screen,
                transform = PdfZoomTransform(scale = 3f, offset = Offset(-1050f, -1450f)),
                baseBitmapWidth = 1000,
            ),
        )
        assertTrue(
            shouldReusePdfTile(
                initial.request.region,
                initial.request.fullPageWidthPx,
                smallPan,
            ),
        )

        val furtherZoom = requireNotNull(
            calculatePdfTilePlan(
                pageBoundsInContent = pageBounds,
                viewportSize = screen,
                transform = PdfZoomTransform(scale = 4f, offset = Offset(-1500f, -2100f)),
                baseBitmapWidth = 1000,
            ),
        )
        assertFalse(
            shouldReusePdfTile(
                initial.request.region,
                initial.request.fullPageWidthPx,
                furtherZoom,
            ),
        )
    }
}
