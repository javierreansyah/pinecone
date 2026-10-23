package com.javierreansyah.pinecone.data.publication.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class EpubPageChunksTest {
    private fun convert(
        count: Int,
        blocks: (Int) -> String = { "<p id='page-$it-block-0'>Paragraph $it.</p>" }
    ): Map<String, String> {
        val output = ByteArrayOutputStream()
        EpubWriter(
            output, "test:chunks", "Test", "en",
            GeneratedPublicationStrings(
                "Contents",
                "Scanned"
            ) { "Page $it" }).use { writer ->
            (1..count).forEach { page ->
                writer.nativePage(
                    page,
                    MuPdfStructure.parse("<body xmlns='http://www.w3.org/1999/xhtml'>${blocks(page)}</body>"),
                    false
                )
            }
            writer.finish()
        }
        return buildMap {
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                }
            }
        }
    }

    private fun files(entries: Map<String, String>) =
        entries.filterKeys { it.startsWith("EPUB/text/") }

    private fun pages(xml: String) = MuPdfStructure.parse(xml).descendants("section")
        .map { it.getAttribute("data-source-page").toInt() }

    @Test
    fun twentyFivePagesStayTogetherAndLongDocumentsUseFixedIntervals() {
        assertEquals(1, files(convert(25)).size)
        val output = convert(51)
        val chunks = files(output).values.toList()
        assertEquals(3, chunks.size)
        assertEquals((1..25).toList(), pages(chunks[0]))
        assertEquals((26..50).toList(), pages(chunks[1]))
        assertEquals(listOf(51), pages(chunks[2]))
        assertEquals(files(output), files(convert(51)))
        val nav = MuPdfStructure.parse(output.getValue("EPUB/nav.xhtml"))
        assertEquals(51, nav.descendants("nav").first().descendants("a").size)
        assertEquals(
            "text/content-0002.xhtml#source-page-26",
            nav.descendants("a")[25].getAttribute("href")
        )
        assertEquals(
            3,
            MuPdfStructure.parse(output.getValue("EPUB/package.opf")).descendants("itemref").size
        )
    }

    @Test
    fun headingFigureCaptionAndContinuedListDelayTheCutWithoutAddingChapters() {
        listOf(
            "<h2>Heading</h2>" to "<p>Following paragraph.</p>",
            "<figure><img src='../images/chart.svg'/></figure>" to "<p class='caption'>Figure caption.</p>",
            "<p class='caption'>Table caption.</p>" to "<div class='table-scroll'><table><tr><td>Cell</td></tr></table></div>",
            "<ul><li>First part</li></ul>" to "<ul><li>Continuation</li></ul>",
        ).forEach { (before, after) ->
            val chunks = files(convert(51) { page ->
                when (page) {
                    25 -> before
                    26 -> after
                    else -> "<p>Paragraph $page.</p>"
                }
            }).values.toList()
            assertEquals((1..26).toList(), pages(chunks[0]))
            assertEquals((27..50).toList(), pages(chunks[1]))
            assertEquals(listOf(51), pages(chunks[2]))
        }
    }

    @Test
    fun bibliographyContinuationAndItsPageAnchorStayWithTheOriginalEntry() {
        val output = convert(26) { page ->
            when (page) {
                25 -> "<p class='reference' id='entry'>[1] Author.</p>"
                26 -> "<p class='reference-continuation' id='continued'><span id='run'>Remaining entry.</span></p><p id='next'>Next entry.</p>"
                else -> "<p>Paragraph $page.</p>"
            }
        }
        val first = MuPdfStructure.parse(output.getValue("EPUB/text/content.xhtml"))
        val second = MuPdfStructure.parse(output.getValue("EPUB/text/content-0002.xhtml"))
        val entry = first.descendants("p").last()
        assertTrue(entry.textContent.contains("Remaining entry."))
        assertTrue(entry.descendants().any { it.getAttribute("id") == "source-page-26" })
        assertTrue(entry.descendants().any { it.getAttribute("id") == "continued" })
        assertEquals("Next entry.", second.descendants("p").single().textContent)
        val nav = MuPdfStructure.parse(output.getValue("EPUB/nav.xhtml"))
        assertEquals(
            "text/content.xhtml#source-page-26",
            nav.descendants("a")[25].getAttribute("href")
        )
    }

    @Test
    fun crossFileLinksResolveWhileTablesListsEquationsAndChartsStayWhole() {
        val output = convert(26) { page ->
            when (page) {
                1 -> "<p><a href='#target'>Forward</a><a href='https://example.com/text/content.xhtml#target'>External</a></p>"
                25 -> "<div class='equation'><svg xmlns='http://www.w3.org/2000/svg' id='formula'><path d='M0 0L1 1'/></svg></div>"
                26 -> "<p id='target'><a href='content.xhtml#source-page-1'>Back</a></p><table><tr><td>Cell</td></tr></table><ul><li>Item</li></ul>"
                else -> "<p>Text.</p>"
            }
        }
        val first = MuPdfStructure.parse(output.getValue("EPUB/text/content.xhtml"))
        val second = MuPdfStructure.parse(output.getValue("EPUB/text/content-0002.xhtml"))
        assertEquals("content-0002.xhtml#target", first.descendants("a")[0].getAttribute("href"))
        assertEquals(
            "https://example.com/text/content.xhtml#target",
            first.descendants("a")[1].getAttribute("href")
        )
        assertEquals(
            "content.xhtml#source-page-1",
            second.descendants("a").single().getAttribute("href")
        )
        assertEquals(1, first.descendants("svg").size)
        assertEquals(1, second.descendants("table").size)
        assertEquals(1, second.descendants("ul").size)
        val manifest = MuPdfStructure.parse(output.getValue("EPUB/package.opf")).descendants("item")
        assertEquals(
            "svg",
            manifest.first { it.getAttribute("href") == "text/content.xhtml" }
                .getAttribute("properties")
        )
        assertFalse(manifest.first { it.getAttribute("href") == "text/content-0002.xhtml" }
            .hasAttribute("properties"))
    }

    @Test
    fun aFinalBibliographyContinuationDoesNotCreateAnEmptyReadingResource() {
        val chunks = files(convert(26) { page ->
            when (page) {
                25 -> "<p class='reference'>[1] Author.</p>"
                26 -> "<p class='reference-continuation'>Remaining entry.</p>"
                else -> "<p>Text.</p>"
            }
        })
        assertEquals(1, chunks.size)
        assertEquals((1..26).toList(), pages(chunks.values.single()))
    }
}
