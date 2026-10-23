package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.publication.conversion.EpubWriter
import com.javierreansyah.pinecone.data.publication.conversion.FigureBounds
import com.javierreansyah.pinecone.data.publication.conversion.ReflowBlock
import com.javierreansyah.pinecone.data.publication.conversion.ReflowCellPart
import com.javierreansyah.pinecone.data.publication.conversion.ReflowListMarker
import com.javierreansyah.pinecone.data.publication.conversion.ReflowMath
import com.javierreansyah.pinecone.data.publication.conversion.ReflowMathText
import com.javierreansyah.pinecone.data.publication.conversion.ReflowRun
import com.javierreansyah.pinecone.data.publication.conversion.ReflowSource
import com.javierreansyah.pinecone.data.publication.conversion.ReflowTable
import com.javierreansyah.pinecone.data.publication.conversion.ReflowTableCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class EpubWriterTest {
    private val labels =
        com.javierreansyah.pinecone.data.publication.conversion.GeneratedPublicationStrings(
            contents = "Contents",
            scannedPageNotice = "OCR is not enabled",
            pageLabel = { "Page $it" },
        )

    @Test
    fun writesEpub3WithFirstStoredMimetypeAndValidResourceGraph() {
        val output = ByteArrayOutputStream()
        EpubWriter(output, "urn:test:1", "A & B <C>", "en", labels).use { writer ->
            writer.image("figure-1.png", byteArrayOf(1, 2, 3))
            writer.page(
                1,
                listOf(
                    ReflowBlock("A <heading> & title", heading = true),
                    ReflowBlock("Text \u0001 with 😀 Unicode"),
                    ReflowBlock(image = "figure-1.png")
                )
            )
            writer.page(2, listOf(ReflowBlock(image = "figure-1.png")), scanned = true)
            writer.finish(listOf("First chapter" to 1, "Second chapter" to 2))
        }
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            val first = zip.nextEntry ?: return@use
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            assertEquals("application/epub+zip", zip.readBytes().toString(Charsets.US_ASCII))
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val container = parse(entries.getValue("META-INF/container.xml"))
        assertEquals(
            "EPUB/package.opf",
            container.getElementsByTagNameNS("*", "rootfile")
                .item(0).attributes.getNamedItem("full-path").nodeValue
        )
        val opf = parse(entries.getValue("EPUB/package.opf"))
        assertEquals("3.0", opf.documentElement.getAttribute("version"))
        assertEquals("A & B <C>", opf.getElementsByTagNameNS("*", "title").item(0).textContent)
        val items = opf.getElementsByTagNameNS("*", "item")
        val identifiers = mutableSetOf<String>()
        for (index in 0 until items.length) {
            val item = items.item(index).attributes
            identifiers += item.getNamedItem("id").nodeValue
            assertTrue(entries.containsKey("EPUB/" + item.getNamedItem("href").nodeValue))
        }
        val spine = opf.getElementsByTagNameNS("*", "itemref")
        assertEquals(1, spine.length)
        for (index in 0 until spine.length) assertTrue(spine.item(index).attributes.getNamedItem("idref").nodeValue in identifiers)
        val page = parse(entries.getValue("EPUB/text/content.xhtml"))
        assertEquals(
            "A <heading> & title",
            page.getElementsByTagNameNS("*", "h2").item(0).textContent
        )
        assertFalse(page.documentElement.textContent.contains('\u0001'))
        assertTrue(page.documentElement.textContent.contains("😀"))
        assertEquals(
            "source-page-1",
            page.getElementsByTagNameNS("*", "span").item(0).attributes.getNamedItem("id").nodeValue
        )
        val navigation = parse(entries.getValue("EPUB/nav.xhtml"))
        assertEquals(2, navigation.getElementsByTagNameNS("*", "nav").length)
        assertEquals(
            listOf("EPUB/text/content.xhtml"),
            entries.keys.filter { it.startsWith("EPUB/text/") })
        val ids = mutableSetOf<String>()
        val elements = page.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val id = elements.item(index).attributes.getNamedItem("id")?.nodeValue ?: continue
            assertTrue("Duplicate ID: $id", ids.add(id))
        }
        assertTrue(
            ids.containsAll(
                listOf(
                    "page-1-block-0",
                    "page-2-block-0",
                    "source-page-1",
                    "source-page-2"
                )
            )
        )
        val links = navigation.getElementsByTagNameNS("*", "a")
        assertEquals(4, links.length)
        for (index in 0 until links.length) {
            val href = links.item(index).attributes.getNamedItem("href").nodeValue
            assertEquals("text/content.xhtml", href.substringBefore('#'))
            assertTrue(href.substringAfter('#') in ids)
        }
        assertEquals("Second chapter", links.item(1).textContent)
        assertEquals(
            "text/content.xhtml#source-page-2",
            links.item(1).attributes.getNamedItem("href").nodeValue
        )

    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeResourceNames() {
        EpubWriter(
            ByteArrayOutputStream(),
            "id",
            "Title",
            "en",
            labels
        ).use { it.image("../escape.png", byteArrayOf()) }
    }

    @Test
    fun generatedNavigationAndScanNoticesUseSuppliedTranslations() {
        val translated =
            com.javierreansyah.pinecone.data.publication.conversion.GeneratedPublicationStrings(
                contents = "Daftar Isi",
                scannedPageNotice = "OCR tidak diaktifkan.",
                pageLabel = { "Halaman $it" },
            )
        val output = ByteArrayOutputStream()
        EpubWriter(output, "urn:test:translated", "Judul", "id", translated).use {
            it.page(1, emptyList(), scanned = true)
            it.finish()
        }
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val page = parse(entries.getValue("EPUB/text/content.xhtml"))
        assertEquals("Judul", page.getElementsByTagNameNS("*", "title").item(0).textContent)
        assertTrue(page.documentElement.textContent.contains("OCR tidak diaktifkan."))
        val navigation = parse(entries.getValue("EPUB/nav.xhtml"))
        assertEquals("Daftar Isi", navigation.getElementsByTagNameNS("*", "h1").item(0).textContent)
        assertTrue(navigation.documentElement.textContent.contains("Halaman 1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresStableSequentialSourcePages() {
        EpubWriter(ByteArrayOutputStream(), "id", "Title", "en", labels).use {
            it.page(
                2,
                emptyList()
            )
        }
    }


    @Test
    fun richHeadingsAndInlineStylesRetainSourceAndRunIds() {
        val output = ByteArrayOutputStream()
        val box =
            FigureBounds(0f, 0f, 100f, 10f)
        val number = ReflowSource(
            "page-1-block-0", box, "1",
            listOf(ReflowRun("1", 0, 1))
        )
        val title = ReflowSource(
            "page-1-block-1", box, "Introduction",
            listOf(
                ReflowRun(
                    "Introduction",
                    0,
                    12
                )
            )
        )
        EpubWriter(output, "test:rich", "Rich", "en", labels).use {
            it.page(1, listOf(ReflowBlock(headingLevel = 2, sources = listOf(number, title))))
            it.finish()
        }
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "EPUB/text/content.xhtml") {
                    val doc = parse(zip.readBytes())
                    val h = doc.getElementsByTagNameNS("*", "h2").item(0)
                    assertEquals("1 Introduction", h.textContent)
                    val spans = doc.getElementsByTagNameNS("*", "span")
                    val ids =
                        (0 until spans.length).map { spans.item(it).attributes.getNamedItem("id")?.nodeValue }
                            .toSet()
                    assertTrue(
                        ids.containsAll(
                            listOf(
                                "page-1-block-0",
                                "page-1-block-1",
                                "page-1-block-1-run-0"
                            )
                        )
                    )
                }
            }
        }
    }

    @Test
    fun tableCellsRetainUniqueRunIdsHeadersSpansAndEmptyValues() {
        val box = FigureBounds(0f, 0f, 100f, 10f)
        val source = ReflowSource(
            "page-1-block-0", box, "Scores Base 12.4", listOf(
                ReflowRun("Scores", 0, 6), ReflowRun(" Base", 6, 11), ReflowRun(" 12.4", 11, 16)
            )
        )
        val block = ReflowBlock(
            kind = "table", sources = listOf(source), table = ReflowTable(
                3, listOf(
                    listOf(
                        ReflowTableCell(
                            1,
                            listOf(ReflowCellPart(0, listOf(0))),
                            columnSpan = 2
                        )
                    ),
                    listOf(
                        ReflowTableCell(0, listOf(ReflowCellPart(0, listOf(1)))),
                        ReflowTableCell(2, listOf(ReflowCellPart(0, listOf(2))))
                    )
                ), headerRows = 1
            )
        )
        val doc = writtenDocument { writer -> writer.page(1, listOf(block)) }
        assertEquals(1, doc.getElementsByTagNameNS("*", "table").length)
        assertEquals(1, doc.getElementsByTagNameNS("*", "thead").length)
        val headers = doc.getElementsByTagNameNS("*", "th")
        assertEquals("2", headers.item(1).attributes.getNamedItem("colspan").nodeValue)
        val data = doc.getElementsByTagNameNS("*", "td")
        assertEquals(3, data.length)
        assertEquals("", data.item(1).textContent)
        val elements = doc.getElementsByTagName("*")
        val ids =
            (0 until elements.length).mapNotNull { elements.item(it).attributes.getNamedItem("id")?.nodeValue }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(
            ids.containsAll(
                listOf(
                    source.anchor,
                    "${source.anchor}-run-0",
                    "${source.anchor}-run-1",
                    "${source.anchor}-run-2"
                )
            )
        )
    }

    @Test
    fun bibliographyContinuesAcrossSourcePagesInOneParagraphAndPreservesPageLinks() {
        val box = FigureBounds(0f, 0f, 100f, 10f)
        fun source(anchor: String, text: String) =
            ReflowSource(anchor, box, text, listOf(ReflowRun(text, 0, text.length)))

        val doc = writtenDocument { writer ->
            writer.page(
                1,
                listOf(
                    ReflowBlock(
                        kind = "reference",
                        sources = listOf(source("entry", "[1] Author. A paper"))
                    )
                )
            )
            writer.page(
                2, listOf(
                    ReflowBlock(
                        kind = "reference-continuation",
                        sources = listOf(source("rest", "continued, 2024."))
                    ),
                    ReflowBlock(
                        kind = "reference",
                        sources = listOf(source("next", "[2] Another paper, 2025."))
                    )
                )
            )
        }
        val paragraphs = doc.getElementsByTagNameNS("*", "p")
        assertEquals(2, paragraphs.length)
        assertEquals("[1] Author. A paper continued, 2024.", paragraphs.item(0).textContent)
        val spans = doc.getElementsByTagNameNS("*", "span")
        val pageBreak = (0 until spans.length).map { spans.item(it) }
            .single { it.attributes.getNamedItem("id")?.nodeValue == "source-page-2" }
        assertEquals(paragraphs.item(0), pageBreak.parentNode)
    }

    private fun writtenDocument(pages: (EpubWriter) -> Unit): Document {
        val output = ByteArrayOutputStream()
        EpubWriter(
            output,
            "test:structure",
            "Structure",
            "en",
            labels
        ).use { pages(it); it.finish() }
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "EPUB/text/content.xhtml") return parse(zip.readBytes())
            }
        }
        error("Missing reading document")
    }

    @Test
    fun formulaSvgPreservesPositionsScalesWithTextAndDeclaresEpubCapability() {
        val math = ReflowMath(
            FigureBounds(100f, 90f, 150f, 120f), listOf(
                ReflowMathText("a", 110f, 100f, 8f, 10f), ReflowMathText("b", 110f, 118f, 8f, 10f)
            ),
            listOf(FigureBounds(108f, 106f, 120f, 106.5f)), 10f, 110f
        )
        val source = ReflowSource(
            "fraction",
            math.bounds,
            "a b",
            listOf(ReflowRun("a b", 0, 3, math = math))
        )
        val output = ByteArrayOutputStream()
        EpubWriter(output, "test:math", "Math", "en", labels).use {
            it.page(1, listOf(ReflowBlock(sources = listOf(source)))); it.finish()
        }
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes()
            }
        }
        val doc = parse(entries.getValue("EPUB/text/content.xhtml"))
        val svg = doc.getElementsByTagNameNS("http://www.w3.org/2000/svg", "svg")
            .item(0) as org.w3c.dom.Element
        assertEquals("100.0 90.0 50.0 30.0", svg.getAttribute("viewBox"))
        assertTrue(svg.getAttribute("style").contains("height:3.0em"))
        assertTrue(svg.getAttribute("style").contains("vertical-align:-1.0em"))
        assertEquals(1, svg.getElementsByTagNameNS("*", "line").length)
        assertEquals(0, doc.getElementsByTagNameNS("*", "img").length)
        assertTrue(entries.keys.none { it.startsWith("EPUB/images/") })
        val manifest =
            parse(entries.getValue("EPUB/package.opf")).getElementsByTagNameNS("*", "item")
        assertTrue((0 until manifest.length).map { manifest.item(it) as org.w3c.dom.Element }
            .any { it.getAttribute("id") == "content" && it.getAttribute("properties") == "svg" })
    }

    @Test
    fun repairsEscapedSurrogatePairsWithoutChangingOtherCharacterReferences() {
        val broken = "<p>&#65;&#55349;&#56413; &#xD83D;&#xDE00; &#945; &amp;#55349;&amp;#56413;</p>"
        val fixed = EpubWriter.repairSerializedUnicode(broken)
        val doc = parse(fixed.toByteArray(Charsets.UTF_8))
        assertEquals(
            "A\uD835\uDC5D \uD83D\uDE00 \u03b1 &#55349;&#56413;",
            doc.documentElement.textContent
        )
        assertEquals(fixed, EpubWriter.repairSerializedUnicode(fixed))
    }

    @Test
    fun supplementaryMathCharactersAndFollowingPagesRoundTripAsValidXhtml() {
        val first = "Probability \uD835\uDC5D and temperature \uD835\uDC47 with \uD83D\uDE00."
        val doc = writtenDocument { writer ->
            writer.page(1, listOf(ReflowBlock(text = first)))
            writer.page(
                2,
                listOf(ReflowBlock(text = "The rest of the publication remains readable."))
            )
        }
        assertTrue(doc.documentElement.textContent.contains(first))
        assertTrue(doc.documentElement.textContent.contains("The rest of the publication remains readable."))
        assertEquals(2, doc.getElementsByTagNameNS("*", "section").length)
    }

    @Test
    fun listItemsKeepSemanticsAndDoubleDigitMarkersInSeparateColumns() {
        val box = FigureBounds(0f, 0f, 100f, 10f)
        fun item(marker: String, value: Int?, depth: Int = 0) = ReflowBlock(
            kind = "list",
            listMarker = ReflowListMarker(marker, value, depth), sources = listOf(
                ReflowSource(
                    "item-$marker", box,
                    "An item", listOf(ReflowRun("An item", 0, 7))
                )
            )
        )

        val doc = writtenDocument { writer ->
            writer.page(
                1,
                listOf(item("9.", 9), item("10.", 10), item("bullet", null, 1))
            )
        }
        val ordered = doc.getElementsByTagNameNS("*", "ol").item(0) as org.w3c.dom.Element
        assertEquals("9", ordered.getAttribute("start"))
        val items = ordered.getElementsByTagNameNS("*", "li")
        assertEquals("9", (items.item(0) as org.w3c.dom.Element).getAttribute("value"))
        assertEquals("10", (items.item(1) as org.w3c.dom.Element).getAttribute("value"))
        val first = items.item(0) as org.w3c.dom.Element
        assertEquals("9.", first.firstChild.textContent)
        assertEquals("list-marker", (first.firstChild as org.w3c.dom.Element).getAttribute("class"))
        assertEquals("An item", first.childNodes.item(1).textContent)
        assertEquals(
            "list-content",
            (first.childNodes.item(1) as org.w3c.dom.Element).getAttribute("class")
        )
        assertEquals(items.item(1), doc.getElementsByTagNameNS("*", "ul").item(0).parentNode)
    }

    private fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
