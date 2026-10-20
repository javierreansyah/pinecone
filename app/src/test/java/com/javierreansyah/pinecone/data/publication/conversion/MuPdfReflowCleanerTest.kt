package com.javierreansyah.pinecone.data.publication.conversion

import org.junit.Assert.*
import org.junit.Test

class MuPdfReflowCleanerTest {
    private val pageBounds = FigureBounds(0f, 0f, 600f, 800f)
    private fun text(value: String, x: Float, y: Float, structure: String = "", math: Boolean = false): MuPdfText {
        var left = x
        val glyphs = value.codePoints().toArray().map { cp ->
            MuPdfGlyph(String(Character.toChars(cp)), FigureBounds(left, y, left + 5, y + 10), y + 8, 10f,
                bold = structure == "H", math = math).also { left += 5 }
        }
        return MuPdfText(FigureBounds(x, y, left, y + 10), glyphs, structure)
    }
    private fun page(vararg texts: MuPdfText, tables: List<MuPdfTable> = emptyList()): MuPdfPageRecord {
        val xhtml = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>" +
            texts.joinToString("") { "<p>${it.text.replace("&", "&amp;").replace("<", "&lt;")}</p>" } + "</body></html>"
        return MuPdfPageRecord(xhtml, pageBounds, texts.toList(), emptyList(), emptyList(), emptyList(), tables)
    }
    private fun clean(record: MuPdfPageRecord, previous: List<SourceBlock> = emptyList()): MuPdfCleanPage {
        var next = 0
        return MuPdfReflowCleaner(MuPdfReflowProfile().apply { observe(record) })
            .clean(record, 1, previous, emptySet(), svg = { bounds, standalone ->
                assertFalse("This fixture contains equations, not figures", standalone)
                MuPdfVisual("visual-${next++}.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                    "xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 ${bounds.width} ${bounds.height}\">" +
                    "<defs><path id=\"glyph\" d=\"M0 0L1 1\"/><clipPath id=\"clip\"><path d=\"M0 0L10 10\"/></clipPath></defs>" +
                    "<g clip-path=\"url(#clip)\" fill=\"#000000\"><use xlink:href=\"#glyph\"/></g></svg>")
            }, asset = { _, _, _ -> fail("Unexpected raster image") })
    }

    @Test fun nativeSvgOutlinesKeepTheirTreeAndHaveUniqueReferenceIds() {
        val result = clean(page(text("a = b", 100f, 100f, math = true), text("c = d", 100f, 200f, math = true)))
        val svg = result.body.descendants("svg")
        assertEquals(2, svg.size)
        val ids = result.body.descendants().mapNotNull { it.getAttribute("id").takeIf(String::isNotEmpty) }
        assertEquals(ids.size, ids.toSet().size)
        svg.forEach { image ->
            val target = image.descendants("use").single().getAttributeNS("http://www.w3.org/1999/xlink", "href").drop(1)
            assertTrue(image.descendants().any { it.getAttribute("id") == target })
            assertEquals("currentColor", image.descendants("g").single().getAttribute("fill"))
            assertEquals(2, image.descendants("path").size)
        }
        result.sources.forEach { source ->
            assertTrue(source.textRuns.single().image)
            assertTrue(source.textRuns.single().anchor in ids)
        }
    }

    @Test fun numberedHeadingsAndBulletContinuationsRetainReadingStructure() {
        val result = clean(page(text("3.2", 50f, 100f), text("Attention", 80f, 100f, "H"),
            text("\u2022 The first list item continues", 50f, 140f),
            text("on another extracted line.", 60f, 153f), text("\u2022 The second item.", 50f, 180f)))
        assertEquals("3.2 Attention", result.body.descendants("h3").single().textContent)
        val items = result.body.descendants("li")
        assertEquals(2, items.size)
        assertTrue(items.first().textContent.endsWith("on another extracted line."))
        assertEquals("\u2022", items.first().children().first().textContent)
    }

    @Test fun reparseReusesAnchorsAndAllTextRangesAddressExistingNodes() {
        val record = page(text("A paragraph with a supplementary math letter \uD835\uDC5D.", 50f, 100f),
            text("a = b", 100f, 200f, math = true))
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

    @Test fun detachedNumberMarkersBecomeAListAndPrintedFootersAreRemoved() {
        val result = clean(page(text("13", 50f, 726f), text("1.", 50f, 100f),
            text("Initialize the state.", 70f, 100f), text("2.", 50f, 140f),
            text("Evaluate the result.", 70f, 140f)))
        assertTrue(!result.body.textContent.contains("13"))
        val list = result.body.descendants("ol").single()
        assertEquals(2, list.descendants("li").size)
        assertEquals("1.Initialize the state.", list.descendants("li")[0].textContent)
        assertEquals("2.Evaluate the result.", list.descendants("li")[1].textContent)
    }

    @Test fun nativeCellSpansSurviveAndSynchronizedSuperCellsBecomeRows() {
        val bounds = FigureBounds(40f, 90f, 400f, 180f)
        val table = MuPdfTable(bounds, "<table xmlns=\"http://www.w3.org/1999/xhtml\">" +
            "<tr><td colspan=\"2\"><p>Experiment</p></td><td><p>Value</p></td></tr>" +
            "<tr><td colspan=\"2\"><p>Alpha Beta</p></td><td><p>12 34</p></td></tr></table>",
            listOf(listOf(MuPdfCell(listOf(MuPdfCellLine("Experiment", 100f))), MuPdfCell(listOf(MuPdfCellLine("Value", 100f)))),
                listOf(MuPdfCell(listOf(MuPdfCellLine("Alpha", 130f), MuPdfCellLine("Beta", 150f))),
                    MuPdfCell(listOf(MuPdfCellLine("12", 130f), MuPdfCellLine("34", 150f))))))
        val result = clean(page(text("Experiment", 50f, 100f), text("Value", 300f, 100f),
            text("Alpha Beta", 50f, 130f), text("12 34", 300f, 130f), tables = listOf(table)))
        val output = result.body.descendants("table").single()
        assertEquals(3, output.descendants("tr").size)
        assertEquals("Alpha", output.descendants("tr")[1].children()[0].textContent)
        assertEquals("34", output.descendants("tr")[2].children()[1].textContent)
        assertEquals("2", output.descendants("tr")[2].children()[0].getAttribute("colspan"))
    }

    @Test fun referenceTableFalsePositivesRemainJoinedParagraphEntries() {
        val table = MuPdfTable(FigureBounds(40f, 90f, 400f, 200f),
            "<table xmlns=\"http://www.w3.org/1999/xhtml\"><tr><td>[1]</td><td>First reference</td></tr>" +
                "<tr><td>[2]</td><td>Second reference</td></tr></table>")
        val result = clean(page(text("References", 50f, 80f, "H"), text("[1]", 50f, 110f),
            text("First reference continues", 70f, 110f), text("on another line.", 70f, 125f),
            text("[2] Second reference.", 50f, 160f), tables = listOf(table)))
        assertTrue(result.body.descendants("table").isEmpty())
        val entries = result.body.descendants("p").filter { it.getAttribute("class") == "reference" }
        assertEquals(2, entries.size)
        assertEquals("[1] First reference continues on another line.", entries.first().textContent)
    }

    @Test fun nativeDoctypeNeverLoadsADtdAndInvalidScalarsCannotTruncateTheBook() {
        val document = MuPdfStructure.parse("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
            "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html><p>&#x1d45d; &#x0;</p><p>Final page</p></html>")
        assertTrue(document.textContent.contains("\uD835\uDC5D"))
        assertTrue(document.textContent.contains("Final page"))
        assertThrows(IllegalArgumentException::class.java) {
            MuPdfStructure.parse("<!DOCTYPE html [<!ENTITY sample SYSTEM 'file:///test'>]><html>&sample;</html>")
        }
    }

    @Test fun bibliographyStateEndsAtAFigureCaption() {
        val result = clean(page(text("References", 50f, 80f, "H"),
            text("[1] An example reference.", 50f, 120f), text("Figure 1: A diagram.", 50f, 200f)))
        assertEquals(1, result.body.descendants("p").count { it.getAttribute("class") == "reference" })
        assertEquals("Figure 1: A diagram.", result.body.descendants("p").single { it.getAttribute("class") == "caption" }.textContent)
    }
}
