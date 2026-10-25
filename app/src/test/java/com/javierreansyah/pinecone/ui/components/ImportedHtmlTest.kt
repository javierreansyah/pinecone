package com.javierreansyah.pinecone.ui.components

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedHtmlTest {
    @Test
    fun malformedDefinitionsRetainFormattingButCannotInjectActiveContent() {
        val html = ImportedHtml.clean(
            """<!doctype html><html><head><style>p{display:none}</style></head><body>
                <h2 class="word-title">Word</h2><p onclick="evil()"><b>Meaning
                <script>evil()</script><a href="javascript:evil()">bad</a>
                <a href="https://example.com">reference</a><table><tr><td>cell
            """.trimIndent(),
        )
        val document = Jsoup.parse(html)
        assertTrue(document.select("b").text().contains("Meaning"))
        assertEquals("Word", document.select("h2.word-title").text())
        assertEquals("cell", document.select("td").text())
        assertEquals("https://example.com", document.select("a").last()!!.attr("href"))
        assertTrue(document.select("script, style, [onclick], [href^=javascript]").isEmpty())
        assertFalse(html.contains("<html"))
    }

    @Test
    fun descriptionsUseParagraphsForDoubleBreaksAndSpacesForSingleBreaks() {
        val html = ImportedHtml.description("First<br>line<BR /> <br>Second &amp; last")
        val paragraphs = Jsoup.parse(html).select("p")
        assertEquals(listOf("First line", "Second & last"), paragraphs.map { it.text() })
    }

    @Test
    fun existingParagraphsAndRelativeLinksSurviveCleaning() {
        val html = ImportedHtml.description("<p><i>First</i></p><p><a href='#note'>Second</a></p>")
        assertEquals(2, Jsoup.parse(html).select("p").size)
        assertEquals("#note", Jsoup.parse(html).select("a").attr("href"))
    }
}
