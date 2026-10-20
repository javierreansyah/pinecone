package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.publication.conversion.EpubWriter
import com.javierreansyah.pinecone.data.publication.conversion.ReflowBlock
import com.javierreansyah.pinecone.data.publication.conversion.FigureBounds
import com.javierreansyah.pinecone.data.publication.conversion.ReflowCellPart
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
            originalPageNotice = "Original page",
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
        assertEquals(listOf("EPUB/text/content.xhtml"), entries.keys.filter { it.startsWith("EPUB/text/") })
        val ids = mutableSetOf<String>()
        val elements = page.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val id = elements.item(index).attributes.getNamedItem("id")?.nodeValue ?: continue
            assertTrue("Duplicate ID: $id", ids.add(id))
        }
        assertTrue(ids.containsAll(listOf("page-1-block-0", "page-2-block-0", "source-page-1", "source-page-2")))
        val links = navigation.getElementsByTagNameNS("*", "a")
        assertEquals(4, links.length)
        for (index in 0 until links.length) {
            val href = links.item(index).attributes.getNamedItem("href").nodeValue
            assertEquals("text/content.xhtml", href.substringBefore('#'))
            assertTrue(href.substringAfter('#') in ids)
        }
        assertEquals("Second chapter", links.item(1).textContent)
        assertEquals("text/content.xhtml#source-page-2", links.item(1).attributes.getNamedItem("href").nodeValue)

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
                originalPageNotice = "Halaman asli",
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
        val box = com.javierreansyah.pinecone.data.publication.conversion.FigureBounds(0f, 0f, 100f, 10f)
        val number = com.javierreansyah.pinecone.data.publication.conversion.ReflowSource("page-1-block-0", box, "1",
            listOf(com.javierreansyah.pinecone.data.publication.conversion.ReflowRun("1", 0, 1)))
        val title = com.javierreansyah.pinecone.data.publication.conversion.ReflowSource("page-1-block-1", box, "Introduction",
            listOf(com.javierreansyah.pinecone.data.publication.conversion.ReflowRun("Introduction", 0, 12)))
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
                    val ids = (0 until spans.length).map { spans.item(it).attributes.getNamedItem("id")?.nodeValue }.toSet()
                    assertTrue(ids.containsAll(listOf("page-1-block-0", "page-1-block-1", "page-1-block-1-run-0")))
                }
            }
        }
    }

    @Test fun tableCellsRetainUniqueRunIdsHeadersSpansAndEmptyValues() {
        val box = FigureBounds(0f, 0f, 100f, 10f)
        val source = ReflowSource("page-1-block-0", box, "Scores Base 12.4", listOf(
            ReflowRun("Scores", 0, 6), ReflowRun(" Base", 6, 11), ReflowRun(" 12.4", 11, 16)))
        val block = ReflowBlock(kind = "table", sources = listOf(source), table = ReflowTable(3, listOf(
            listOf(ReflowTableCell(1, listOf(ReflowCellPart(0, listOf(0))), columnSpan = 2)),
            listOf(ReflowTableCell(0, listOf(ReflowCellPart(0, listOf(1)))),
                ReflowTableCell(2, listOf(ReflowCellPart(0, listOf(2)))))), headerRows = 1))
        val doc = writtenDocument { writer -> writer.page(1, listOf(block)) }
        assertEquals(1, doc.getElementsByTagNameNS("*", "table").length)
        assertEquals(1, doc.getElementsByTagNameNS("*", "thead").length)
        val headers = doc.getElementsByTagNameNS("*", "th")
        assertEquals("2", headers.item(1).attributes.getNamedItem("colspan").nodeValue)
        val data = doc.getElementsByTagNameNS("*", "td")
        assertEquals(3, data.length)
        assertEquals("", data.item(1).textContent)
        val elements = doc.getElementsByTagName("*")
        val ids = (0 until elements.length).mapNotNull { elements.item(it).attributes.getNamedItem("id")?.nodeValue }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.containsAll(listOf(source.anchor, "${source.anchor}-run-0", "${source.anchor}-run-1", "${source.anchor}-run-2")))
    }

    @Test fun bibliographyContinuesAcrossSourcePagesInOneParagraphAndPreservesPageLinks() {
        val box = FigureBounds(0f, 0f, 100f, 10f)
        fun source(anchor: String, text: String) = ReflowSource(anchor, box, text, listOf(ReflowRun(text, 0, text.length)))
        val doc = writtenDocument { writer ->
            writer.page(1, listOf(ReflowBlock(kind = "reference", sources = listOf(source("entry", "[1] Author. A paper")))))
            writer.page(2, listOf(ReflowBlock(kind = "reference-continuation", sources = listOf(source("rest", "continued, 2024."))),
                ReflowBlock(kind = "reference", sources = listOf(source("next", "[2] Another paper, 2025.")))))
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
        EpubWriter(output, "test:structure", "Structure", "en", labels).use { pages(it); it.finish() }
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "EPUB/text/content.xhtml") return parse(zip.readBytes())
            }
        }
        error("Missing reading document")
    }

    private fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
