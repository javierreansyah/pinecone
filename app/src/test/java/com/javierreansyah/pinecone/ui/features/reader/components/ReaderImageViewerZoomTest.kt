package com.javierreansyah.pinecone.ui.features.reader.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageViewerZoomTest {

    @Test
    fun pinchZoomClampsScaleAndAnchorsCentroid() {
        val viewport = IntSize(1000, 1000)
        val zoomed = imageZoomTransform(
            previous = ImageZoomTransform(offset = Offset.Zero),
            zoom = 2f,
            pan = Offset.Zero,
            centroid = Offset(600f, 500f),
            viewport = viewport,
            imageAspectRatio = 1f,
        )
        assertEquals(2f, zoomed.scale, 0.001f)
        // Centroid is +100px right of center, so scaling 2x shifts image -100px left to anchor it.
        assertEquals(-100f, zoomed.offset.x, 0.001f)
        assertEquals(0f, zoomed.offset.y, 0.001f)

        val clampedMax = imageZoomTransform(
            previous = zoomed,
            zoom = 10f,
            pan = Offset.Zero,
            centroid = Offset(500f, 500f),
            viewport = viewport,
            imageAspectRatio = 1f,
        )
        assertEquals(IMAGE_VIEWER_MAX_SCALE, clampedMax.scale, 0.001f)

        val clampedMin = imageZoomTransform(
            previous = zoomed,
            zoom = 0.1f,
            pan = Offset(50f, 50f),
            centroid = Offset(500f, 500f),
            viewport = viewport,
            imageAspectRatio = 1f,
        )
        assertEquals(IMAGE_VIEWER_MIN_SCALE, clampedMin.scale, 0.001f)
        assertEquals(Offset.Zero, clampedMin.offset)
    }

    @Test
    fun panClampsToFittedPortraitImageBounds() {
        // 1:2 portrait image in a 1000x1000 viewport -> fitted size is 500x1000.
        // At 1.5x zoom: scaled width is 750 (<= 1000, so maxX = 0), scaled height is 1500 (maxY = 250).
        val viewport = IntSize(1000, 1000)
        val panned = imageZoomTransform(
            previous = ImageZoomTransform(scale = 1.5f, offset = Offset.Zero),
            zoom = 1f,
            pan = Offset(300f, 400f),
            centroid = Offset(500f, 500f),
            viewport = viewport,
            imageAspectRatio = 0.5f,
        )
        assertEquals(0f, panned.offset.x, 0.001f)
        assertEquals(250f, panned.offset.y, 0.001f)
    }

    @Test
    fun doubleTapTogglesBetweenBaseAndZoomedScale() {
        val viewport = IntSize(1000, 1000)
        val zoomedIn = imageDoubleTapTransform(
            current = ImageZoomTransform(),
            tapOffset = Offset(700f, 500f),
            viewport = viewport,
            imageAspectRatio = 1f,
        )
        assertEquals(IMAGE_VIEWER_DOUBLE_TAP_SCALE, zoomedIn.scale, 0.001f)
        assertTrue(zoomedIn.offset.x < 0f)

        val reset = imageDoubleTapTransform(
            current = zoomedIn,
            tapOffset = Offset(500f, 500f),
            viewport = viewport,
            imageAspectRatio = 1f,
        )
        assertEquals(IMAGE_VIEWER_MIN_SCALE, reset.scale, 0.001f)
        assertEquals(Offset.Zero, reset.offset)
    }

    @Test
    fun svgDetectionAndSanitizationStripActiveContentAndPreserveViewBox() {
        assertTrue(isSvgLink("images/diagram.svg", null))
        assertTrue(isSvgLink("images/diagram.svg?v=1#top", null))
        assertTrue(isSvgLink("images/blob", "image/svg+xml"))
        assertFalse(isSvgLink("images/photo.png", "image/png"))

        val raw = """
            <svg viewBox="0 0 100 50" onclick="alert(1)">
                <script>alert('xss')</script>
                <rect width="100" height="50" onload="evil()"/>
                <use href="#shape1"/>
                <image href="https://example.com/tracker.png"/>
            </svg>
        """.trimIndent()
        val sanitized = sanitizeSvgMarkup(raw)
        assertNotNull(sanitized)
        requireNotNull(sanitized)
        assertTrue("viewBox=\"0 0 100 50\"" in sanitized)
        assertTrue("href=\"#shape1\"" in sanitized)
        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("onload", ignoreCase = true))
        assertFalse(sanitized.contains("https://example.com", ignoreCase = true))
        assertNull(sanitizeSvgMarkup("<div>not an svg</div>"))
    }

    @Test
    fun readModeInlineMathSvgsAndExternalImagesAreSkipped() {
        assertTrue(isInlineMathSvg("""<svg class="math-layout" viewBox="0 0 10 10"></svg>"""))
        assertTrue(isInlineMathSvg("""<svg class="formula" viewBox="0 0 10 10"></svg>"""))
        assertFalse(isInlineMathSvg("""<svg class="diagram" viewBox="0 0 400 200"></svg>"""))

        assertTrue(isInternalImageHref("images/map.png"))
        assertTrue(isInternalImageHref("/OEBPS/Images/cover.jpg"))
        assertFalse(isInternalImageHref("https://example.com/pic.png"))
        assertFalse(isInternalImageHref("HTTP://example.com/pic.png"))
    }

    @Test
    fun coverImagesAndFirstPageImagesAreIgnoredWhileLaterFiguresOpen() {
        // Explicit cover rel or matching manifest cover href is always ignored.
        assertTrue(
            isCoverOrFirstPageImage(
                elementResourceHref = "chapter1.xhtml",
                embeddedLinkHref = "images/cover.jpg",
                embeddedLinkHasCoverRel = true,
                publicationCoverHref = null,
                firstReadingOrderHref = "cover.xhtml",
                firstReadingOrderPositionCount = 1,
                currentPage = 2,
            ),
        )
        assertTrue(
            isCoverOrFirstPageImage(
                elementResourceHref = "titlepage.xhtml",
                embeddedLinkHref = "/Images/Cover.jpg",
                embeddedLinkHasCoverRel = false,
                publicationCoverHref = "images/cover.jpg",
                firstReadingOrderHref = "titlepage.xhtml",
                firstReadingOrderPositionCount = 1,
                currentPage = 1,
            ),
        )
        // Dedicated first-page cover XHTML without rel="cover" is ignored.
        assertTrue(
            isCoverOrFirstPageImage(
                elementResourceHref = "text/part0000.html",
                embeddedLinkHref = "images/00001.jpeg",
                embeddedLinkHasCoverRel = false,
                publicationCoverHref = null,
                firstReadingOrderHref = "text/part0000.html",
                firstReadingOrderPositionCount = 1,
                currentPage = 1,
            ),
        )
        // Single-file EPUB: page 1 image is ignored, later page diagram opens.
        assertTrue(
            isCoverOrFirstPageImage(
                elementResourceHref = "content.xhtml",
                embeddedLinkHref = "images/front.png",
                embeddedLinkHasCoverRel = false,
                publicationCoverHref = null,
                firstReadingOrderHref = "content.xhtml",
                firstReadingOrderPositionCount = 50,
                currentPage = 1,
            ),
        )
        assertFalse(
            isCoverOrFirstPageImage(
                elementResourceHref = "content.xhtml",
                embeddedLinkHref = "images/diagram.png",
                embeddedLinkHasCoverRel = false,
                publicationCoverHref = null,
                firstReadingOrderHref = "content.xhtml",
                firstReadingOrderPositionCount = 50,
                currentPage = 12,
            ),
        )
        // Normal chapter diagram opens.
        assertFalse(
            isCoverOrFirstPageImage(
                elementResourceHref = "chapter2.xhtml",
                embeddedLinkHref = "images/turbine.png",
                embeddedLinkHasCoverRel = false,
                publicationCoverHref = "images/cover.jpg",
                firstReadingOrderHref = "cover.xhtml",
                firstReadingOrderPositionCount = 1,
                currentPage = 15,
            ),
        )
    }
}
