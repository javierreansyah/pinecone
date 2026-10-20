package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.publication.conversion.EpubWriter
import com.javierreansyah.pinecone.data.publication.conversion.ReflowBlock
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
            writer.finish(listOf("First chapter" to 1))
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
        assertEquals(2, spine.length)
        for (index in 0 until spine.length) assertTrue(spine.item(index).attributes.getNamedItem("idref").nodeValue in identifiers)
        val page = parse(entries.getValue("EPUB/text/page-1.xhtml"))
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
        val page = parse(entries.getValue("EPUB/text/page-1.xhtml"))
        assertEquals("Halaman 1", page.getElementsByTagNameNS("*", "title").item(0).textContent)
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

    private fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
