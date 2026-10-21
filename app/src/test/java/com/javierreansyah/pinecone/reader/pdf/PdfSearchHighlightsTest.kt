package com.javierreansyah.pinecone.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSearchHighlightsTest {
    private fun quad(x: Float, y: Float) = PdfSearchQuad(PdfSearchPoint(x, y), PdfSearchPoint(x + .01f, y),
        PdfSearchPoint(x + .01f, y + .02f), PdfSearchPoint(x, y + .02f))

    @Test fun repeatedWordsKeepSeparateCaseInsensitiveHitsAndSnippets() {
        val text = "word WORD"
        val glyphs = text.mapIndexedNotNull { index, char ->
            if (char == ' ') null else PdfSearchGlyph(index, index + 1, 0, quad(index / 100f, .1f))
        }
        val hits = pdfSearchHits(text, "word", glyphs)
        assertEquals(listOf("word", "WORD"), hits.map { text.substring(it.start, it.end) })
        assertEquals(listOf(1, 1), hits.map { it.quads.size })
        assertEquals(.04f, hits[0].quads.single().topRight.x, .00001f)
        assertEquals(.05f, hits[1].quads.single().topLeft.x, .00001f)
    }

    @Test fun aPhraseAcrossLinesPaintsEachLineSeparately() {
        val text = "find word"
        val glyphs = text.mapIndexedNotNull { index, char ->
            if (char == ' ') null else PdfSearchGlyph(index, index + 1, if (index < 4) 0 else 1,
                quad(index / 100f, if (index < 4) .1f else .2f))
        }
        val hit = pdfSearchHits(text, "  FIND\nword\t", glyphs).single()
        assertEquals(text, text.substring(hit.start, hit.end))
        assertEquals(2, hit.quads.size)
        assertEquals(.1f, hit.quads[0].topLeft.y, .00001f)
        assertEquals(.2f, hit.quads[1].topLeft.y, .00001f)
    }

    @Test fun supplementaryCharactersUseUtf16RangesWithoutDuplicatingGlyphs() {
        val text = "A\uD835\uDC65 A\uD835\uDC65"
        val glyphs = listOf(PdfSearchGlyph(0, 1, 0, quad(0f, .1f)), PdfSearchGlyph(1, 3, 0, quad(.01f, .1f)),
            PdfSearchGlyph(4, 5, 0, quad(.04f, .1f)), PdfSearchGlyph(5, 7, 0, quad(.05f, .1f)))
        val hits = pdfSearchHits(text, "\uD835\uDC65", glyphs)
        assertEquals(listOf(1 to 3, 5 to 7), hits.map { it.start to it.end })
        assertEquals(listOf(1, 1), hits.map { it.quads.size })
    }

    @Test fun rotatedAndRightToLeftRunsKeepTheirFullOrientedBounds() {
        val vertical = listOf(
            PdfSearchQuad(PdfSearchPoint(.5f, .1f), PdfSearchPoint(.5f, .2f), PdfSearchPoint(.4f, .2f), PdfSearchPoint(.4f, .1f)),
            PdfSearchQuad(PdfSearchPoint(.5f, .2f), PdfSearchPoint(.5f, .3f), PdfSearchPoint(.4f, .3f), PdfSearchPoint(.4f, .2f)),
        )
        val hit = pdfSearchHits("ab", "ab", vertical.mapIndexed { i, q -> PdfSearchGlyph(i, i + 1, 0, q) }).single().quads.single()
        assertEquals(.5f, hit.topLeft.x, .00001f)
        assertEquals(.1f, hit.topLeft.y, .00001f)
        assertEquals(.3f, hit.topRight.y, .00001f)
        val rtl = pdfSearchHits("ab", "ab", listOf(PdfSearchGlyph(0, 1, 0, quad(.6f, .1f)),
            PdfSearchGlyph(1, 2, 0, quad(.59f, .1f)))).single().quads.single()
        assertEquals(.59f, rtl.topLeft.x, .00001f)
        assertEquals(.61f, rtl.topRight.x, .00001f)
    }

    @Test fun portraitPageCentersHorizontallyInsideWideViewport() {
        assertEquals(PdfImageViewport(200f, 0f, 600f, 800f), pdfImageViewport(1000f, 800f, 600, 800))
    }

    @Test fun landscapePageCentersVerticallyInsideTallViewport() {
        assertEquals(PdfImageViewport(0f, 200f, 800f, 600f), pdfImageViewport(800f, 1000f, 800, 600))
    }

    @Test fun fittingAndRenderResolutionDoNotChangeNormalizedMatchLocations() {
        val low = pdfImageViewport(300f, 400f, 600, 800)
        val high = pdfImageViewport(300f, 400f, 1200, 1600)
        assertEquals(PdfImageViewport(0f, 0f, 300f, 400f), low)
        assertEquals(low, high)
    }
}
