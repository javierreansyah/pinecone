package com.javierreansyah.pinecone.data.publication.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfFigureRegionsTest {
    private val page = FigureBounds(0f, 0f, 600f, 800f)

    @Test
    fun pageBackgroundAndHeaderRuleDoNotBecomePageImages() {
        assertTrue(pdfFigureRegions(page, listOf(
            page, FigureBounds(30f, 50f, 570f, 51f)
        )).isEmpty())
    }

    @Test
    fun preservesSeparateFiguresWithoutIncludingTheWholePage() {
        val result = pdfFigureRegions(page, listOf(
            page,
            FigureBounds(50f, 100f, 180f, 180f),
            FigureBounds(190f, 500f, 400f, 650f),
        ))
        assertEquals(listOf(
            FigureBounds(47f, 97f, 183f, 183f),
            FigureBounds(187f, 497f, 403f, 653f),
        ), result)
    }

    @Test
    fun mergesConnectedDiagramPartsEvenWhenTheConnectorIsLast() {
        val result = pdfFigureRegions(page, listOf(
            FigureBounds(50f, 100f, 100f, 150f),
            FigureBounds(140f, 100f, 190f, 150f),
            FigureBounds(100f, 120f, 140f, 121f),
        ))
        assertEquals(listOf(FigureBounds(47f, 97f, 193f, 153f)), result)
    }

    @Test
    fun loneUnderlinesDoNotBecomeFiguresButTableRulesAreGrouped() {
        assertTrue(pdfFigureRegions(page, listOf(FigureBounds(50f, 100f, 120f, 101f))).isEmpty())
        val table = pdfFigureRegions(page, listOf(
            FigureBounds(50f, 100f, 200f, 101f),
            FigureBounds(50f, 200f, 200f, 201f),
            FigureBounds(50f, 100f, 51f, 201f),
            FigureBounds(199f, 100f, 200f, 201f),
        ))
        assertEquals(listOf(FigureBounds(47f, 97f, 203f, 204f)), table)
    }

    @Test
    fun clipsPaddingAndDiscardsOffPageOrInvalidPaths() {
        assertEquals(listOf(FigureBounds(0f, 0f, 23f, 23f)), pdfFigureRegions(page, listOf(
            FigureBounds(-5f, -5f, 20f, 20f),
            FigureBounds(900f, 900f, 950f, 950f),
            FigureBounds(Float.NaN, 50f, 100f, 100f),
        )))
    }

    @Test
    fun mergedPageSizedArtDoesNotReintroduceTheFullPageFallback() {
        assertTrue(pdfFigureRegions(page, listOf(
            FigureBounds(0f, 0f, 300f, 800f),
            FigureBounds(300f, 0f, 600f, 800f),
        )).isEmpty())
    }
}
