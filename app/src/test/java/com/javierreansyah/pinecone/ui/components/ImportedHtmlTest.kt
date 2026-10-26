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
        assertTrue("Meaning" in document.select("b").text())
        assertEquals("Word", document.select("h2.word-title").text())
        assertEquals("cell", document.select("td").text())
        assertEquals("https://example.com", (document.select("a").last() ?: return).attr("href"))
        assertTrue(document.select("script, style, [onclick], [href^=javascript]").isEmpty())
        assertFalse("<html" in html)
    }

}
