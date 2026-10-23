package com.javierreansyah.pinecone.data.publication.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLayoutTest {
    private val page = FigureBounds(0f, 0f, 600f, 800f)
    private val body = "This is a sufficiently long paragraph of ordinary body text. ".repeat(3)
    private fun block(
        id: String, text: String, x: Float, y: Float, size: Float = 10f,
        bold: Boolean = false, width: Float = text.length * size * .5f
    ) = PdfLayoutBlock(
        id, FigureBounds(x, y, x + width, y + size),
        listOf(
            PdfTextLine(
                listOf(
                    PdfTextRun(
                        text,
                        FigureBounds(x, y, x + width, y + size),
                        y + size * .8f,
                        size,
                        bold
                    ),
                )
            )
        ),
    )

    private fun reconstruct(
        vararg blocks: PdfLayoutBlock,
        rules: List<FigureBounds> = emptyList()
    ): List<ReflowBlock> {
        val layout = PdfPageLayout(page, blocks.toList(), rules)
        return PdfLayoutReconstructor(PdfDocumentProfile().apply { observe(layout) }).reconstruct(
            layout
        )
    }

    @Test
    fun joinsSeparateNumberAndHeadingAndRetainsBookmarkAnchors() {
        val output = reconstruct(
            block("number", "1", 50f, 30f), block("title", "Introduction", 70f, 30f),
            block("body", body, 50f, 100f, width = 480f)
        )
        assertEquals(2, output.first().headingLevel)
        assertEquals(listOf("1", "Introduction"), output.first().sources.map { it.text })
        assertEquals(listOf("number", "title"), output.first().sources.map { it.anchor })
    }

    @Test
    fun subsectionNumberDeterminesHeadingHierarchy() {
        val output = reconstruct(
            block("number", "3.2", 50f, 90f, bold = true),
            block("title", "Attention", 72f, 90f, bold = true),
            block("body", body, 50f, 140f, width = 480f)
        )
        assertEquals(3, output.first().headingLevel)
    }

    @Test
    fun equivalentSectionsHaveTheSameLevelRegardlessOfLocalFontDistribution() {
        val a = PdfPageLayout(
            page,
            listOf(block("one", "1 Introduction", 50f, 90f), block("body", body, 50f, 130f))
        )
        val b = PdfPageLayout(
            page, listOf(
                block("four", "4 Why Self-Attention", 50f, 90f, 13f, true),
                block("tiny", "Small table cell ".repeat(20), 50f, 130f, 7f)
            )
        )
        val profile = PdfDocumentProfile().apply { observe(a); observe(b) }
        val model = PdfLayoutReconstructor(profile)
        assertEquals(2, model.reconstruct(a).first().headingLevel)
        assertEquals(
            model.reconstruct(a).first().headingLevel,
            model.reconstruct(b).first().headingLevel
        )
    }

    @Test
    fun doesNotJoinAcrossColumnsOrPromoteNumberedLists() {
        val output = reconstruct(
            block("n", "1", 286f, 90f, width = 5f),
            block("t", "Background", 310f, 90f, bold = true),
            block("left", body, 50f, 130f, width = 240f),
            block("right", body, 310f, 130f, width = 240f),
            block("list", "1. Install the package", 50f, 300f)
        )
        assertFalse(output.any { it.sources.map { s -> s.anchor }.containsAll(listOf("n", "t")) })
        val list = output.first { it.sources.singleOrNull()?.anchor == "list" }
        assertNull(list.headingLevel)
        assertEquals("list", list.kind)
    }

    @Test
    fun inlineScriptsAndEmphasisKeepCanonicalOffsets() {
        val runs = listOf(
            PdfTextRun("x", FigureBounds(50f, 100f, 57f, 112f), 110f, 12f, italic = true),
            PdfTextRun("2", FigureBounds(57f, 96f, 62f, 104f), 103f, 8f),
            PdfTextRun(" + y", FigureBounds(62f, 100f, 90f, 112f), 110f, 12f),
            PdfTextRun("i", FigureBounds(90f, 107f, 94f, 115f), 114f, 8f),
        )
        val math =
            PdfLayoutBlock("math", FigureBounds(50f, 96f, 94f, 115f), listOf(PdfTextLine(runs)))
        val source = reconstruct(math, block("body", body, 50f, 200f, 12f)).first().sources.single()
        assertEquals("x2 + yi", source.text)
        assertEquals(1, source.runs.first { it.text == "2" }.script)
        assertEquals(-1, source.runs.first { it.text == "i" }.script)
        assertTrue(source.runs.first().italic)
        assertEquals(1, source.runs.first { it.text == "2" }.start)
        assertEquals(source.text, source.runs.joinToString("") { it.text })
    }

    @Test
    fun stackedFractionHasOneCropAndRetainsEquationNumber() {
        val a = PdfTextRun("a+b", FigureBounds(100f, 100f, 125f, 110f), 108f, 10f, mathFont = true)
        val b = PdfTextRun("c", FigureBounds(110f, 116f, 117f, 126f), 124f, 10f, mathFont = true)
        val fraction = PdfLayoutBlock(
            "fraction",
            FigureBounds(100f, 100f, 125f, 126f),
            listOf(PdfTextLine(listOf(a)), PdfTextLine(listOf(b)))
        )
        val output = reconstruct(
            fraction, block("label", "(1)", 180f, 109f), block("body", body, 50f, 200f),
            rules = listOf(FigureBounds(98f, 113f, 128f, 114f))
        )
        val equation = output.first { it.kind == "equation" }
        assertNull(equation.headingLevel)
        assertEquals(1, equation.sources.flatMap { it.runs }.count { it.math != null })
        assertTrue(equation.sources.flatMap { it.runs }.none { it.crop != null })
        assertEquals("(1)", equation.sources.single { it.equationLabel }.text)
        assertEquals(1, output.flatMap { it.sources }.count { it.anchor == "label" })
    }

    @Test
    fun inlineFractionRemainsInsideItsParagraph() {
        val runs = listOf(
            PdfTextRun("We compute the value ", FigureBounds(50f, 100f, 155f, 112f), 110f, 12f),
            PdfTextRun("a", FigureBounds(156f, 96f, 163f, 106f), 104f, 10f, mathFont = true),
            PdfTextRun("b", FigureBounds(156f, 112f, 163f, 122f), 120f, 10f, mathFont = true),
            PdfTextRun(" for each input.", FigureBounds(166f, 100f, 250f, 112f), 110f, 12f),
        )
        val paragraph = PdfLayoutBlock(
            "paragraph",
            FigureBounds(50f, 96f, 250f, 122f),
            listOf(PdfTextLine(runs))
        )
        val output = reconstruct(
            paragraph,
            block("body", body, 50f, 200f, 12f),
            rules = listOf(FigureBounds(155f, 109f, 164f, 110f))
        )
        assertEquals("paragraph", output.first().kind)
        val source = output.first().sources.single()
        assertTrue(source.runs.first().text.startsWith("We compute"))
        assertTrue(source.runs.last().text.endsWith("input."))
        assertEquals(1, source.runs.count { it.math != null && !it.hidden })
        assertTrue(source.runs.none { it.crop != null })
    }

    @Test
    fun canonicalWhitespaceAndDehyphenationMatchPreviousConversion() {
        assertEquals(
            "A reconstructed paragraph",
            PdfReadModeConverter.joinLines("A recon-\nstructed\nparagraph")
        )
        val block = PdfLayoutBlock(
            "p", FigureBounds(0f, 0f, 300f, 30f), listOf(
                PdfTextLine(
                    listOf(
                        PdfTextRun(
                            "A recon-",
                            FigureBounds(0f, 0f, 100f, 10f),
                            8f,
                            10f
                        )
                    )
                ),
                PdfTextLine(
                    listOf(
                        PdfTextRun(
                            "structed paragraph",
                            FigureBounds(0f, 12f, 120f, 22f),
                            20f,
                            10f
                        )
                    )
                ),
            )
        )
        assertEquals(block.text, reconstruct(block).single().sources.single().text)
    }

    @Test
    fun diagramAndTableLabelsRemainWithTheirFigureInsteadOfBecomingHeadings() {
        val picture = PdfLayoutBlock("image", FigureBounds(170f, 100f, 240f, 240f), image = true)
        val output = reconstruct(
            block("title", "Scaled Dot-Product Attention", 145f, 82f, bold = true, width = 125f),
            picture,
            block("cell", "Softmax", 180f, 130f, bold = true),
            block("body", body, 50f, 300f, width = 480f)
        )
        val figure = output.first { it.kind == "figure" }
        assertTrue(figure.sources.single { it.anchor == "title" }.hidden)
        assertTrue(figure.sources.single { it.anchor == "cell" }.hidden)
        assertFalse(output.any { it.headingLevel != null })
        assertTrue((figure.sources.first().runs.single().crop ?: return).left <= 145f)
    }

    @Test
    fun rotatedMarginWatermarksAndLongLicenseParagraphsAreNotHeadings() {
        val watermark = block("mark", "arXiv metadata", 10f, 200f, 20f, true, 25f)
            .copy(bounds = FigureBounds(10f, 200f, 35f, 600f))
        val license = block(
            "license",
            "Permission is granted with attribution for reproduction of these figures for academic and other purposes",
            60f,
            60f,
            12f,
            true,
            450f
        )
        val output = reconstruct(watermark, license, block("body", body, 50f, 300f, width = 480f))
        assertFalse(output.flatMap { it.sources }.any { it.anchor == "mark" })
        assertNull(output.single { it.sources.any { source -> source.anchor == "license" } }.headingLevel)
    }

    @Test
    fun equationCropIncludesFunctionPrefixAndClosingFragmentsWithoutClippingText() {
        val prefix = PdfTextRun("f(x) = ", FigureBounds(50f, 105f, 120f, 117f), 115f, 10f)
        val a = PdfTextRun("a+b", FigureBounds(120f, 100f, 160f, 110f), 108f, 10f, mathFont = true)
        val b = PdfTextRun("c", FigureBounds(130f, 116f, 137f, 126f), 124f, 10f, mathFont = true)
        val expression = PdfLayoutBlock(
            "expr", FigureBounds(50f, 100f, 160f, 126f),
            listOf(PdfTextLine(listOf(prefix, a)), PdfTextLine(listOf(b)))
        )
        val closing = block("closing", ") V (1)", 162f, 108f, width = 100f)
        val output = reconstruct(
            expression, closing, block("body", body, 50f, 200f),
            rules = listOf(FigureBounds(119f, 113f, 161f, 114f))
        )
        val equation = output.first { it.kind == "equation" }
        val crop = (equation.sources.flatMap { it.runs }.single { it.math != null }.math ?: return).bounds
        assertTrue(crop.left <= expression.bounds.left)
        assertTrue(crop.right >= closing.bounds.right)
        assertEquals(1, output.flatMap { it.sources }.count { it.anchor == "closing" })
    }
}
