package com.javierreansyah.pinecone.data.publication.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfStructuredContentTest {
    private val page = FigureBounds(0f, 0f, 600f, 800f)
    private fun text(
        id: String, value: String, x: Float, y: Float, width: Float = value.length * 5f,
        bold: Boolean = false
    ) = PdfLayoutBlock(
        id, FigureBounds(x, y, x + width, y + 10),
        listOf(
            PdfTextLine(
                listOf(
                    PdfTextRun(
                        value,
                        FigureBounds(x, y, x + width, y + 10),
                        y + 8,
                        10f,
                        bold
                    )
                )
            )
        )
    )

    private fun reconstruct(
        blocks: List<PdfLayoutBlock>,
        rules: List<FigureBounds> = emptyList()
    ): List<ReflowBlock> {
        val layout = PdfPageLayout(page, blocks, rules)
        return PdfLayoutReconstructor(PdfDocumentProfile().apply { observe(layout) }).reconstruct(
            layout
        )
    }

    private fun cellText(block: ReflowBlock, cell: ReflowTableCell) =
        cell.parts.joinToString(" ") { part ->
            part.runs.joinToString("") { block.sources[part.source].runs[it].text }.trim()
        }

    @Test
    fun reconstructsRowsFromColumnOrderedBlocksAndPreservesAnchors() {
        val blocks = listOf(
            text("caption", "Table 1: Results", 50f, 70f),
            text("a", "Model", 50f, 100f, bold = true),
            text("b", "Base", 50f, 120f),
            text("c", "Large", 50f, 140f),
            text("d", "Score", 200f, 100f, bold = true),
            text("e", "12.4", 200f, 120f),
            text("f", "16.7", 200f, 140f),
            PdfLayoutBlock("grid", FigureBounds(45f, 95f, 250f, 155f), image = true)
        )
        val output = reconstruct(blocks, listOf(FigureBounds(45f, 114f, 250f, 115f)))
        val table = output.single { it.table != null }
        val layout = requireNotNull(table.table)
        assertEquals(2, layout.columns)
        assertEquals(1, layout.headerRows)
        assertEquals(
            listOf(listOf("Model", "Score"), listOf("Base", "12.4"), listOf("Large", "16.7")),
            layout.rows.map { row -> row.map { cellText(table, it) } })
        assertTrue(table.sources.map { it.anchor }
            .containsAll(listOf("a", "b", "c", "d", "e", "f", "grid")))
        assertFalse(output.any { it.kind == "figure" })
    }

    @Test
    fun reconstructsSpanningHeadersAndKeepsEmptyCellsEmpty() {
        val blocks = listOf(
            text("caption", "Table 2: Results", 50f, 70f),
            text("h", "Scores", 185f, 100f, 140f),
            text("m1", "Small", 50f, 120f),
            text("x1", "1.0", 200f, 120f),
            text("y1", "2.0", 300f, 120f),
            text("m2", "Large", 50f, 140f),
            text("x2", "3.0", 200f, 140f),
            text("y2", "4.0", 300f, 140f),
            text("m3", "Other", 50f, 160f),
            text("y3", "5.0", 300f, 160f)
        )
        val table = reconstruct(blocks).single { it.table != null }
        val rows = requireNotNull(table.table).rows
        assertEquals(2, rows.first().single().columnSpan)
        assertEquals(listOf(0, 2), rows.last().map { it.column })
    }

    @Test
    fun doesNotTurnTwoProseColumnsOrFigureLabelsIntoTables() {
        val output = reconstruct(
            listOf(
                text("a", "Left column prose ".repeat(8), 50f, 100f, 220f),
                text("b", "Right column prose ".repeat(8), 310f, 100f, 220f),
                text("c", "Input", 60f, 330f), text("d", "Output", 180f, 330f),
                PdfLayoutBlock("figure", FigureBounds(50f, 300f, 250f, 450f), image = true)
            )
        )
        assertFalse(output.any { it.table != null })
        assertEquals(1, output.count { it.kind == "figure" })
    }

    @Test
    fun joinsBibliographyContinuationsAndKeepsEntriesSeparate() {
        val output = reconstruct(
            listOf(
                text("heading", "References", 50f, 100f, bold = true),
                text("first", "[1] Author. A useful paper about parsing", 50f, 130f, 400f),
                text("continued", "tables. Journal of Examples, 2024.", 70f, 142f),
                text("second", "[2] Another author. A second paper.", 50f, 170f),
                text("continued2", "Proceedings of Examples, 2025.", 70f, 182f)
            )
        )
        val entries = output.filter { it.kind == "reference" }
        assertEquals(2, entries.size)
        assertEquals(listOf("first", "continued"), entries.first().sources.map { it.anchor })
        assertEquals(listOf("second", "continued2"), entries.last().sources.map { it.anchor })
    }

    @Test
    fun dehyphenatesAcrossReferenceBlocksWithoutChangingCanonicalOffsets() {
        val output = reconstruct(
            listOf(
                text("heading", "References", 50f, 100f, bold = true),
                text("first", "[1] Author. Convolu-", 50f, 130f),
                text("rest", "tional networks, 2024.", 70f, 142f)
            )
        )
        val source = output.last().sources.first()
        assertEquals("[1] Author. Convolu-", source.text)
        assertEquals(
            "[1] Author. Convolu",
            source.runs.filterNot { it.hidden }.joinToString("") { it.text })
        assertEquals(source.text.length, source.runs.last().end)
    }

    @Test
    fun separateCitationNumberAndAuthorOnTheSameLineFormOneEntry() {
        val output = reconstruct(
            listOf(
                text("heading", "References", 50f, 100f, bold = true),
                text("marker", "[1]", 50f, 130f),
                text("author", "Author. A paper about parsing", 70f, 130f),
                text("rest", "with a continuation, 2024.", 70f, 142f)
            )
        )
        val entries = output.filter { it.kind == "reference" }
        assertEquals(1, entries.size)
        assertEquals(listOf("marker", "author", "rest"), entries.single().sources.map { it.anchor })
    }

    @Test
    fun recognizesPageContinuationAndStopsAtNextSection() {
        val first = PdfPageLayout(
            page, listOf(
                text("heading", "Bibliography", 50f, 100f, bold = true),
                text("start", "[1] Author. This entry continues", 50f, 700f, 400f)
            )
        )
        val next = PdfPageLayout(
            page, listOf(
                text("rest", "on the next page, 2024.", 70f, 70f),
                text("second", "[2] Another entry.", 50f, 100f),
                text("appendix", "Appendix", 50f, 150f, bold = true),
                text("prose", "Some ordinary prose.", 50f, 190f)
            )
        )
        val profile = PdfDocumentProfile().apply { observe(first); observe(next) }
        val model = PdfLayoutReconstructor(profile)
        model.reconstruct(first)
        val result = model.reconstruct(next, setOf("Appendix"))
        assertEquals("reference-continuation", result.first().kind)
        assertEquals("reference", result[1].kind)
        assertEquals("paragraph", result.last().kind)
    }
}
