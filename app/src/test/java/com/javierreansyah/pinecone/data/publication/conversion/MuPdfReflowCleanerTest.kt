package com.javierreansyah.pinecone.data.publication.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MuPdfReflowCleanerTest {
    private val pageBounds = FigureBounds(0f, 0f, 600f, 800f)
    private fun text(
        value: String,
        x: Float,
        y: Float,
        structure: String = "",
        math: Boolean = false
    ): MuPdfText {
        var left = x
        val glyphs = value.codePoints().toArray().map { cp ->
            MuPdfGlyph(
                String(Character.toChars(cp)), FigureBounds(left, y, left + 5, y + 10), y + 8, 10f,
                bold = structure == "H", math = math
            ).also { left += 5 }
        }
        return MuPdfText(FigureBounds(x, y, left, y + 10), glyphs, structure)
    }

    private fun page(
        vararg texts: MuPdfText,
        tables: List<MuPdfTable> = emptyList()
    ): MuPdfPageRecord {
        val xhtml = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>" +
                texts.joinToString("") {
                    "<p>${
                        it.text.replace("&", "&amp;").replace("<", "&lt;")
                    }</p>"
                } + "</body></html>"
        return MuPdfPageRecord(
            xhtml,
            pageBounds,
            texts.toList(),
            emptyList(),
            emptyList(),
            emptyList(),
            tables
        )
    }

    private fun clean(
        record: MuPdfPageRecord,
        previous: List<SourceBlock> = emptyList()
    ): MuPdfCleanPage {
        var next = 0
        return MuPdfReflowCleaner(MuPdfReflowProfile().apply { observe(record) })
            .clean(record, 1, previous, emptySet(), svg = { bounds, standalone ->
                assertFalse("This fixture contains equations, not figures", standalone)
                MuPdfVisual(
                    "visual-${next++}.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                            "xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 ${bounds.width} ${bounds.height}\">" +
                            "<defs><path id=\"glyph\" d=\"M0 0L1 1\"/><clipPath id=\"clip\"><path d=\"M0 0L10 10\"/></clipPath></defs>" +
                            "<g clip-path=\"url(#clip)\" fill=\"#000000\"><use xlink:href=\"#glyph\"/></g></svg>"
                )
            }, asset = { _, _, _ -> fail("Unexpected raster image") })
    }

    @Test
    fun nativeSvgOutlinesKeepTheirTreeAndHaveUniqueReferenceIds() {
        val result = clean(
            page(
                text("a = b", 100f, 100f, math = true),
                text("c = d", 100f, 200f, math = true)
            )
        )
        val svg = result.body.descendants("svg")
        assertEquals(2, svg.size)
        val ids = result.body.descendants()
            .mapNotNull { it.getAttribute("id").takeIf(String::isNotEmpty) }
        assertEquals(ids.size, ids.toSet().size)
        svg.forEach { image ->
            val target = image.descendants("use").single()
                .getAttributeNS("http://www.w3.org/1999/xlink", "href").drop(1)
            assertTrue(image.descendants().any { it.getAttribute("id") == target })
            assertEquals("currentColor", image.descendants("g").single().getAttribute("fill"))
            assertEquals(2, image.descendants("path").size)
        }
        result.sources.forEach { source ->
            assertTrue(source.textRuns.single().image)
            assertTrue(source.textRuns.single().anchor in ids)
        }
    }

    @Test
    fun numberedHeadingsAndBulletContinuationsRetainReadingStructure() {
        val result = clean(
            page(
                text("3.2", 50f, 100f),
                text("Attention", 80f, 100f, "H"),
                text("\u2022 The first list item continues", 50f, 140f),
                text("on another extracted line.", 60f, 153f),
                text("\u2022 The second item.", 50f, 180f)
            )
        )
        assertEquals("3.2 Attention", result.body.descendants("h3").single().textContent)
        val items = result.body.descendants("li")
        assertEquals(2, items.size)
        assertTrue(items.first().textContent.endsWith("on another extracted line."))
        assertEquals("\u2022", items.first().children().first().textContent)
    }

    @Test
    fun reparseReusesAnchorsAndAllTextRangesAddressExistingNodes() {
        val record = page(
            text("A paragraph with a supplementary math letter \uD835\uDC5D.", 50f, 100f),
            text("a = b", 100f, 200f, math = true)
        )
        val first = clean(record)
        val next = clean(record, first.sources)
        assertEquals(first.sources, next.sources)
        val ids = next.body.descendants().map { it.getAttribute("id") }.toSet()
        next.sources.forEach { source ->
            assertTrue(source.anchor in ids)
            source.textRuns.forEach { assertTrue(it.anchor in ids); assertTrue(it.end >= it.start) }
        }
        assertTrue(next.body.textContent.contains("\uD835\uDC5D"))
    }

    @Test
    fun detachedNumberMarkersBecomeAListAndPrintedFootersAreRemoved() {
        val result = clean(
            page(
                text("13", 50f, 726f), text("1.", 50f, 100f),
                text("Initialize the state.", 70f, 100f), text("2.", 50f, 140f),
                text("Evaluate the result.", 70f, 140f)
            )
        )
        assertTrue(!result.body.textContent.contains("13"))
        val list = result.body.descendants("ol").single()
        assertEquals(2, list.descendants("li").size)
        assertEquals("1.Initialize the state.", list.descendants("li")[0].textContent)
        assertEquals("2.Evaluate the result.", list.descendants("li")[1].textContent)
    }

    @Test
    fun nativeCellSpansSurviveAndSynchronizedSuperCellsBecomeRows() {
        val bounds = FigureBounds(40f, 90f, 400f, 180f)
        val table = MuPdfTable(
            bounds, "<table xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<tr><td colspan=\"2\"><p>Experiment</p></td><td><p>Value</p></td></tr>" +
                    "<tr><td colspan=\"2\"><p>Alpha Beta</p></td><td><p>12 34</p></td></tr></table>",
            listOf(
                listOf(
                    MuPdfCell(listOf(MuPdfCellLine("Experiment", 100f))),
                    MuPdfCell(listOf(MuPdfCellLine("Value", 100f)))
                ),
                listOf(
                    MuPdfCell(listOf(MuPdfCellLine("Alpha", 130f), MuPdfCellLine("Beta", 150f))),
                    MuPdfCell(listOf(MuPdfCellLine("12", 130f), MuPdfCellLine("34", 150f)))
                )
            )
        )
        val result = clean(
            page(
                text("Experiment", 50f, 100f), text("Value", 300f, 100f),
                text("Alpha Beta", 50f, 130f), text("12 34", 300f, 130f), tables = listOf(table)
            )
        )
        val output = result.body.descendants("table").single()
        assertEquals(3, output.descendants("tr").size)
        assertEquals("Alpha", output.descendants("tr")[1].children()[0].textContent)
        assertEquals("34", output.descendants("tr")[2].children()[1].textContent)
        assertEquals("2", output.descendants("tr")[2].children()[0].getAttribute("colspan"))
    }

    @Test
    fun referenceTableFalsePositivesRemainJoinedParagraphEntries() {
        val table = MuPdfTable(
            FigureBounds(40f, 90f, 400f, 200f),
            "<table xmlns=\"http://www.w3.org/1999/xhtml\"><tr><td>[1]</td><td>First reference</td></tr>" +
                    "<tr><td>[2]</td><td>Second reference</td></tr></table>"
        )
        val result = clean(
            page(
                text("References", 50f, 80f, "H"), text("[1]", 50f, 110f),
                text("First reference continues", 70f, 110f), text("on another line.", 70f, 125f),
                text("[2] Second reference.", 50f, 160f), tables = listOf(table)
            )
        )
        assertTrue(result.body.descendants("table").isEmpty())
        val entries =
            result.body.descendants("p").filter { it.getAttribute("class") == "reference" }
        assertEquals(2, entries.size)
        assertEquals("[1] First reference continues on another line.", entries.first().textContent)
    }

    @Test
    fun nativeDoctypeNeverLoadsADtdAndInvalidScalarsCannotTruncateTheBook() {
        val document = MuPdfStructure.parse(
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
                    "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html><p>&#x1d45d; &#x0;</p><p>Final page</p></html>"
        )
        assertTrue(document.textContent.contains("\uD835\uDC5D"))
        assertTrue(document.textContent.contains("Final page"))
        assertThrows(IllegalArgumentException::class.java) {
            MuPdfStructure.parse("<!DOCTYPE html [<!ENTITY sample SYSTEM 'file:///test'>]><html>&sample;</html>")
        }
    }

    @Test
    fun bibliographyStateEndsAtAFigureCaption() {
        val result = clean(
            page(
                text("References", 50f, 80f, "H"),
                text("[1] An example reference.", 50f, 120f),
                text("Figure 1: A diagram.", 50f, 200f, "Caption")
            )
        )
        assertEquals(
            1,
            result.body.descendants("p").count { it.getAttribute("class") == "reference" })
        assertEquals(
            "Figure 1: A diagram.",
            result.body.descendants("p")
                .single { it.getAttribute("class") == "caption" }.textContent
        )
    }

    @Test
    fun inlineFigureReferencesAndBodySizedNativeHeadingsStayInTheirParagraph() {
        val result = clean(
            page(
                text("The illustration is shown in", 50f, 100f),
                text("Gambar 5.1 and the explanation continues.", 50f, 118f, "H"),
                text("Table 2 summarizes these findings in the next section.", 50f, 170f, "H")
            )
        )
        assertTrue(
            result.body.descendants()
                .none { it.getAttribute("class") == "caption" || it.localTag.matches(Regex("h[1-6]")) })
        assertEquals(
            "The illustration is shown in Gambar 5.1 and the explanation continues.",
            result.body.children()[0].textContent
        )
        assertEquals(2, result.body.children().size)
    }

    @Test
    fun ruledTextTablesSurviveWithoutBaseParagraphsAndHaveValidHeaderMarkup() {
        val description = "A long description containing prose instead of numeric data. ".repeat(5)
        val table = MuPdfTable(
            FigureBounds(40f, 100f, 500f, 250f),
            "<table xmlns=\"http://www.w3.org/1999/xhtml\"><tr><td><th><p><div><p>Attribute</p></div></p></th></td><td><p>Description</p></td></tr>" +
                    "<tr><td><p>Name</p></td><td><p>$description</p></td></tr></table>",
            ruled = true
        )
        val result = clean(
            page(
                text("Before the table.", 50f, 70f),
                text("After the table.", 50f, 300f),
                tables = listOf(table)
            )
        )
        assertEquals(listOf("p", "div", "p"), result.body.children().map { it.localTag })
        val output = result.body.descendants("table").single()
        assertEquals("th", output.descendants("tr")[0].children()[0].localTag)
        assertEquals(description, output.descendants("tr")[1].children()[1].textContent)
        assertTrue(
            output.descendants("p")
                .all { it.children().none { it.localTag in setOf("p", "div", "th") } })
    }

    @Test
    fun taggedTableShellsCannotShiftTheGeometryOfLaterTables() {
        val xml = "<document><page width=\"600\" height=\"800\">" +
                "<struct std=\"Table\" bbox=\"40 100 400 200\">" +
                "<struct std=\"TR\"><struct std=\"TH\"><line bbox=\"50 110 80 120\" text=\"First\"/></struct></struct></struct>" +
                "<struct std=\"Table\" bbox=\"40 100 400 200\">" +
                listOf(100, 200).joinToString("") { y ->
                    listOf(
                        40,
                        160,
                        280
                    ).joinToString("") { x -> "<vector bbox=\"$x $y ${x + 120} ${y + 1}\"/>" }
                } +
                "<vector bbox=\"40 100 41 200\"/><vector bbox=\"399 100 400 200\"/>" +
                "<struct std=\"TBody\"><struct std=\"TR\"/></struct></struct>" +
                "<struct std=\"Table\" bbox=\"40 300 400 400\"><struct std=\"TR\"><struct std=\"TD\"><line bbox=\"50 310 80 320\" text=\"Second\"/></struct></struct></struct>" +
                "</page></document>"
        val html =
            "<html><body><table><tr><th>First</th></tr></table><table><tr><td>Second</td></tr></table></body></html>"
        val record = MuPdfStructure.read("<html><body/></html>", xml, html, xml)
        assertEquals(2, record.tables.size)
        assertEquals(300f, record.tables[1].bounds.top)
        assertEquals("First", record.tables[0].rows.single().single().lines.single().text)
        assertTrue("Cell-sized ruling segments form a ruled table", record.tables[0].ruled)
    }

    @Test
    fun fontChangesInsideALineDoNotDetachFigureAndTableCitations() {
        val result = clean(
            page(
                text("The result in", 50f, 100f), text("Gambar 5.1", 120f, 100f),
                text("and", 175f, 100f), text("Table 2.3", 195f, 100f),
                text("confirms the conclusion.", 245f, 100f)
            )
        )
        assertEquals(1, result.body.children().size)
        assertEquals(
            "The result in Gambar 5.1 and Table 2.3 confirms the conclusion.",
            result.body.textContent
        )
    }

    @Test
    fun aReferenceNearATableIsNotACaptionWhenItContinuesTheSameLine() {
        val table = MuPdfTable(
            FigureBounds(40f, 100f, 450f, 180f),
            "<table xmlns=\"http://www.w3.org/1999/xhtml\"><tr><td>Name</td><td>Description</td></tr>" +
                    "<tr><td>Alpha</td><td>Example</td></tr></table>", ruled = true
        )
        val result = clean(
            page(
                text("The result in", 50f, 200f), text("Table 2.3", 120f, 200f),
                text("confirms it.", 170f, 200f), tables = listOf(table)
            )
        )
        val paragraph = result.body.children().last()
        assertEquals("The result in Table 2.3 confirms it.", paragraph.textContent)
        assertEquals("", paragraph.getAttribute("class"))
    }

    @Test
    fun wrappedCellBandsRejoinOnlyWhereThePdfHasNoRowSeparator() {
        fun cell(text: String, border: String) =
            "<td style=\"border-style:$border;\"><p>$text</p></td>"

        val table = MuPdfTable(
            FigureBounds(40f, 100f, 450f, 230f),
            "<table xmlns=\"http://www.w3.org/1999/xhtml\"><tr>" + cell(
                "Name",
                "solid solid solid solid"
            ) + cell("Description", "solid solid solid solid") +
                    "</tr><tr>" + cell("Alpha", "solid solid none solid") + cell(
                "The first part",
                "solid solid none solid"
            ) +
                    "</tr><tr>" + cell("", "none solid solid solid") + cell(
                "continues here.",
                "none solid solid solid"
            ) +
                    "</tr><tr>" + cell("Beta", "solid solid solid solid") + cell(
                "A different row.",
                "solid solid solid solid"
            ) + "</tr></table>", ruled = true
        )
        val rows = clean(
            page(
                text("Before the table.", 50f, 70f),
                tables = listOf(table)
            )
        ).body.descendants("tr")
        assertEquals(3, rows.size)
        assertEquals("The first part continues here.", rows[1].children()[1].textContent)
        assertEquals("A different row.", rows[2].children()[1].textContent)
    }
}
