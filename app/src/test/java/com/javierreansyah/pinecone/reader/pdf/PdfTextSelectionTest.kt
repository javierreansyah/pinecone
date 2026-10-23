package com.javierreansyah.pinecone.reader.pdf

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextSelectionTest {
    @Test
    fun containerRelativePdfUrlsMatchSavedAnnotationUrls() {
        assertTrue(samePdfResource("/document.pdf", "document.pdf#page=2"))
        assertFalse(samePdfResource("first.pdf", "second.pdf"))
    }

    private fun quad(x: Float, y: Float) = PdfSearchQuad(
        PdfSearchPoint(x, y), PdfSearchPoint(x + .02f, y),
        PdfSearchPoint(x + .02f, y + .04f), PdfSearchPoint(x, y + .04f),
    )

    private fun page(text: String = "hello world") =
        PdfTextPage(text, text.mapIndexedNotNull { index, char ->
            if (char == ' ') null else PdfSearchGlyph(
                index,
                index + 1,
                0,
                quad(.1f + index * .025f, .2f)
            )
        })

    @Test
    fun longPressSelectsAWholeWordAndDoesNotSelectPageWhitespace() {
        val page = page()
        assertEquals(PdfTextRange(0, 5), pdfWordAt(page, PdfSearchPoint(.16f, .22f)))
        assertEquals(PdfTextRange(6, 11), pdfWordAt(page, PdfSearchPoint(.28f, .22f)))
        assertNull(pdfWordAt(page, PdfSearchPoint(.9f, .8f)))
        assertNull(pdfWordAt(PdfTextPage("", emptyList()), PdfSearchPoint(.1f, .1f)))
    }

    @Test
    fun draggingCanFindTheClosestGlyphOutsideItsExactBounds() {
        assertNull(pdfGlyphAt(page(), PdfSearchPoint(.9f, .8f)))
        assertNotNull(pdfGlyphAt(page(), PdfSearchPoint(.9f, .8f), nearest = true))
        assertNull(pdfGlyphAt(page(), PdfSearchPoint(Float.NaN, .2f), nearest = true))
    }

    @Test
    fun selectionsKeepTextOffsetsAndSeparateLineGeometry() {
        val page = PdfTextPage("hello world", page().glyphs.map {
            it.copy(
                line = if (it.start < 5) 0 else 1,
                quad = quad(it.start * .025f, if (it.start < 5) .2f else .4f)
            )
        })
        val anchor = requireNotNull(pdfSelectionAnchor(2, page, PdfTextRange(0, 11)))
        assertEquals(2, anchor.quads.size)
        assertEquals("hello world", page.text.substring(anchor.start, anchor.end))
        assertEquals(2, anchor.pageIndex)
        assertEquals(anchor, decodePdfSelectionAnchor(Json.encodeToString(anchor)))
        assertEquals(anchor, Json.decodeFromString<PdfSelectionAnchor>(Json.encodeToString(anchor)))
    }

    @Test
    fun emptyAndInvalidRangesAreRejected() {
        assertNull(pdfSelectionAnchor(0, page(), PdfTextRange(2, 2)))
        assertNull(pdfSelectionAnchor(0, page(), PdfTextRange(-1, 5)))
        assertNull(pdfSelectionAnchor(0, page(), PdfTextRange(0, 100)))
        assertNull(pdfSelectionAnchor(-1, page(), PdfTextRange(0, 5)))
        assertNull(pdfSelectionAnchor(0, page(), PdfTextRange(5, 6)))
    }

    @Test
    fun malformedSavedGeometryIsIgnoredRatherThanCrashingTheReader() {
        assertNull(decodePdfSelectionAnchor("not json"))
        val invalid = PdfSelectionAnchor(-1, 0, 1, listOf(quad(.1f, .2f)))
        assertNull(decodePdfSelectionAnchor(Json.encodeToString(invalid)))
    }

    @Test
    fun rotatedTextUsesItsActualQuadAndRejectsDegenerateQuads() {
        val rotated = PdfSearchQuad(
            PdfSearchPoint(.5f, .1f), PdfSearchPoint(.6f, .2f),
            PdfSearchPoint(.5f, .3f), PdfSearchPoint(.4f, .2f)
        )
        assertTrue(rotated.contains(PdfSearchPoint(.5f, .2f)))
        assertFalse(rotated.contains(PdfSearchPoint(.41f, .11f)))
        val point = PdfSearchPoint(.1f, .1f)
        assertFalse(PdfSearchQuad(point, point, point, point).contains(point))
    }

    @Test
    fun selectionPreservesSupplementaryUnicodeCharacters() {
        val text = "A\uD835\uDC65"
        val page = PdfTextPage(
            text, listOf(
                PdfSearchGlyph(0, 1, 0, quad(.1f, .2f)),
                PdfSearchGlyph(1, 3, 0, quad(.125f, .2f))
            )
        )
        val range = requireNotNull(pdfWordAt(page, PdfSearchPoint(.13f, .22f)))
        val anchor = requireNotNull(pdfSelectionAnchor(0, page, range))
        assertEquals(text, page.text.substring(anchor.start, anchor.end))
    }
}
