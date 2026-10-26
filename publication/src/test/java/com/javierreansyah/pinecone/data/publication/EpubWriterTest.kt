package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.publication.conversion.EpubWriter
import com.javierreansyah.pinecone.data.publication.conversion.GeneratedPublicationStrings
import com.javierreansyah.pinecone.data.publication.conversion.MuPdfStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** The native XHTML path used in production, replacing tests of the retired Reflow models. */
class EpubWriterTest {
    private val labels =
        GeneratedPublicationStrings("Contents", "OCR is not enabled") { "Page $it" }

    @Test
    fun writesEpub3WithFirstStoredMimetypeAndValidResourceGraph() {
        val entries = book { writer ->
            writer.asset("figure-1.png", "image/png", byteArrayOf(1, 2, 3))
            writer.nativePage(
                1,
                body("<h2 id=\"heading\">A &lt;heading&gt; &amp; title</h2><p id=\"page-1-block-0\">Text with 😀 Unicode</p>"),
                false
            )
            writer.nativePage(
                2,
                body("<img id=\"page-2-block-0\" src=\"../images/figure-1.png\"/>"),
                true
            )
        }
        val opf = parse(entries.getValue("EPUB/package.opf"))
        assertEquals("3.0", opf.documentElement.getAttribute("version"))
        assertEquals("A & B <C>", opf.getElementsByTagNameNS("*", "title").item(0).textContent)
        val manifest = opf.getElementsByTagNameNS("*", "item")
        val identifiers = mutableSetOf<String>()
        for (index in 0..<manifest.length) {
            val item = manifest.item(index) as Element
            identifiers += item.getAttribute("id")
            assertTrue(entries.containsKey("EPUB/" + item.getAttribute("href")))
        }
        val spine = opf.getElementsByTagNameNS("*", "itemref")
        assertEquals(1, spine.length)
        assertTrue((spine.item(0) as Element).getAttribute("idref") in identifiers)
        val page = parse(entries.getValue("EPUB/text/content.xhtml"))
        assertEquals(
            "A <heading> & title",
            page.getElementsByTagNameNS("*", "h2").item(0).textContent
        )
        assertTrue("😀" in page.documentElement.textContent)
        val ids = ids(page)
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
        val links = parse(entries.getValue("EPUB/nav.xhtml")).getElementsByTagNameNS("*", "a")
        for (index in 0..<links.length) {
            val href = (links.item(index) as Element).getAttribute("href")
            assertEquals("text/content.xhtml", href.substringBefore('#'))
            assertTrue(href.substringAfter('#') in ids)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeResourceNames() {
        book { it.asset("../escape.png", "image/png", byteArrayOf()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateResourceNames() {
        book {
            it.asset("figure.png", "image/png", byteArrayOf(1))
            it.asset("figure.png", "image/png", byteArrayOf(2))
        }
    }

    @Test
    fun generatedNavigationAndScanNoticesUseSuppliedTranslations() {
        val translated =
            GeneratedPublicationStrings("Daftar Isi", "OCR tidak diaktifkan.") { "Halaman $it" }
        val output = ByteArrayOutputStream()
        EpubWriter(output, "id", "Judul", "id", translated).use {
            it.nativePage(1, body(""), true)
            it.finish()
        }
        val entries = entries(output.toByteArray())
        assertTrue("OCR tidak diaktifkan." in parse(entries.getValue("EPUB/text/content.xhtml")).documentElement.textContent)
        val navigation = parse(entries.getValue("EPUB/nav.xhtml")).documentElement.textContent
        assertTrue("Daftar Isi" in navigation)
        assertTrue("Halaman 1" in navigation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresStableSequentialSourcePages() {
        book { it.nativePage(2, body(""), false) }
    }

    @Test
    fun richHeadingsAndInlineStylesRetainSourceAndRunIds() {
        val entries = book {
            it.nativePage(
                1,
                body("<h2 id=\"heading\"><span id=\"source\"><span id=\"run\"><strong>Bold</strong> <em>italic</em> x<sup>2</sup></span></span></h2>"),
                false
            )
        }
        val page = parse(entries.getValue("EPUB/text/content.xhtml"))
        assertTrue(ids(page).containsAll(listOf("heading", "source", "run")))
        assertEquals("Bold", page.getElementsByTagNameNS("*", "strong").item(0).textContent)
        assertEquals("italic", page.getElementsByTagNameNS("*", "em").item(0).textContent)
        assertEquals("2", page.getElementsByTagNameNS("*", "sup").item(0).textContent)
    }

    @Test
    fun tableSpansHeadersEmptyCellsAndRunAnchorsSurviveSerialization() {
        val entries = book {
            it.nativePage(
                1,
                body("<div class=\"table-scroll\"><table><thead><tr><th colspan=\"2\" id=\"header\">Heading</th></tr></thead><tbody><tr><td rowspan=\"2\"><span id=\"cell-run\">Value</span></td><td/></tr><tr><td>Other</td></tr></tbody></table></div>"),
                false
            )
        }
        val page = parse(entries.getValue("EPUB/text/content.xhtml"))
        assertEquals(
            "2",
            (page.getElementsByTagNameNS("*", "th").item(0) as Element).getAttribute("colspan")
        )
        val cells = page.getElementsByTagNameNS("*", "td")
        assertEquals("2", (cells.item(0) as Element).getAttribute("rowspan"))
        assertEquals("", cells.item(1).textContent)
        assertTrue("cell-run" in ids(page))
    }

    @Test
    fun bibliographyContinuesAcrossSourcePagesWithoutLosingPageLinks() {
        val entries = book {
            it.nativePage(1, body("<p class=\"reference\" id=\"ref-1\">First reference</p>"), false)
            it.nativePage(
                2,
                body("<p class=\"reference-continuation\" id=\"ref-2\">continued here</p><p>Next entry</p>"),
                false
            )
        }
        val page = parse(entries.getValue("EPUB/text/content.xhtml"))
        assertTrue(
            "First reference continued here" in page.getElementsByTagNameNS("*", "p")
                .item(0).textContent
        )
        assertTrue(
            ids(page).containsAll(
                listOf(
                    "ref-1",
                    "ref-2",
                    "source-page-1",
                    "source-page-2"
                )
            )
        )
    }

    @Test
    fun nativeSvgAndSupplementaryMathRetainAccessibilityAndManifestCapability() {
        val symbol = "\uD835\uDC5D"
        val entries = book {
            it.nativePage(
                1,
                body("<svg xmlns=\"http://www.w3.org/2000/svg\" aria-label=\"$symbol\"><path id=\"glyph\" d=\"M0 0L10 10\"/></svg><span>$symbol</span>"),
                false
            )
            it.nativePage(2, body("<p>The final page is still readable.</p>"), false)
        }
        val page = parse(entries.getValue("EPUB/text/content.xhtml"))
        val svg =
            page.getElementsByTagNameNS("http://www.w3.org/2000/svg", "svg").item(0) as Element
        assertEquals(symbol, svg.getAttribute("aria-label"))
        assertTrue(symbol in page.documentElement.textContent)
        assertTrue("The final page is still readable." in page.documentElement.textContent)
        val items = parse(entries.getValue("EPUB/package.opf")).getElementsByTagNameNS("*", "item")
        assertTrue((0..<items.length).map { items.item(it) as Element }
            .any { it.getAttribute("properties") == "svg" })
    }

    @Test
    fun listItemsKeepTheirMarkersAndNestedStructure() {
        val entries = book {
            it.nativePage(
                1,
                body("<ol class=\"reflow-list\"><li value=\"10\"><span class=\"list-marker\">10.</span><span class=\"list-content\" id=\"item-run\">Item</span><ol><li>Nested</li></ol></li></ol>"),
                false
            )
        }
        val page = parse(entries.getValue("EPUB/text/content.xhtml"))
        assertEquals(
            "10",
            (page.getElementsByTagNameNS("*", "li").item(0) as Element).getAttribute("value")
        )
        assertEquals(2, page.getElementsByTagNameNS("*", "ol").length)
        assertTrue("item-run" in ids(page))
    }

    @Test
    fun repairsEscapedSurrogatePairsWithoutChangingOtherCharacterReferences() {
        val broken = "<p>&#65;&#55349;&#56413; &#xD83D;&#xDE00; &#945; &amp;#55349;&amp;#56413;</p>"
        val fixed = EpubWriter.repairSerializedUnicode(broken)
        assertEquals(
            "A\uD835\uDC5D \uD83D\uDE00 \u03b1 &#55349;&#56413;",
            parse(fixed.toByteArray()).documentElement.textContent
        )
        assertEquals(fixed, EpubWriter.repairSerializedUnicode(fixed))
        assertFalse('\u0001' in EpubWriter.cleanXmlText("Text\u0001"))
    }

    private fun body(markup: String): Element =
        MuPdfStructure.parse("<body xmlns=\"http://www.w3.org/1999/xhtml\">$markup</body>")

    private fun book(pages: (EpubWriter) -> Unit): Map<String, ByteArray> {
        val output = ByteArrayOutputStream()
        EpubWriter(output, "urn:test:1", "A & B <C>", "en", labels).use {
            pages(it)
            it.finish(listOf("First chapter" to 1, "Second chapter" to 2))
        }
        return entries(output.toByteArray())
    }

    private fun entries(bytes: ByteArray): Map<String, ByteArray> =
        linkedMapOf<String, ByteArray>().also { entries ->
            ZipInputStream(bytes.inputStream()).use { zip ->
                val first = requireNotNull(zip.nextEntry)
                assertEquals("mimetype", first.name)
                assertEquals(ZipEntry.STORED, first.method)
                assertEquals("application/epub+zip", zip.readBytes().toString(Charsets.US_ASCII))
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
        }

    private fun ids(document: Document): Set<String> {
        val ids = mutableSetOf<String>()
        val elements = document.getElementsByTagName("*")
        for (index in 0..<elements.length) {
            val id = (elements.item(index) as Element).getAttribute("id")
            if (id.isNotEmpty()) assertTrue("Duplicate ID: $id", ids.add(id))
        }
        return ids
    }

    private fun parse(bytes: ByteArray): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(bytes.inputStream())
}
