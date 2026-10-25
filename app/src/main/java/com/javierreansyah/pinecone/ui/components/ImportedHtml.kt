package com.javierreansyah.pinecone.ui.components

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/** Imported content is cleaned before it enters a WebView with app-owned styles and scripts. */
internal object ImportedHtml {
    private val cleaner = Cleaner(
        Safelist.relaxed()
            .addTags("div", "span", "h1", "h2", "h3", "h4", "h5", "h6", "hr")
            .addAttributes(":all", "class", "lang", "dir")
            .addAttributes("ol", "start", "reversed", "type")
            .addAttributes("li", "value")
            .addProtocols("a", "href", "#")
            .preserveRelativeLinks(true),
    )

    fun clean(html: String): String = document(html).body().html()

    fun containsMarkup(content: String): Boolean = Jsoup.parse(content).body().children().isNotEmpty()

    fun description(html: String): String {
        val document = document(html)
        val body = document.body()
        // Descriptions often use double breaks as paragraphs and single breaks as spaces.
        body.select("br").toList().forEach { br ->
            if (br.parent() == null) return@forEach
            var next = br.nextSibling()
            while (next is TextNode && next.isBlank) next = next.nextSibling()
            if (next is Element && next.normalName() == "br") {
                next.remove()
                br.before(TextNode("\n\n"))
            } else {
                br.before(TextNode(" "))
            }
            br.remove()
        }
        if (body.select("p, div, h1, h2, h3, h4, h5, h6, ul, ol, blockquote, table").isEmpty()) {
            val fragments = body.html().split("\n\n").filter(String::isNotBlank)
            body.empty()
            fragments.forEach { body.appendElement("p").html(it.trim()) }
        }
        body.select("p").filter { it.text().isBlank() && it.children().isEmpty() }.forEach(Element::remove)
        return body.html()
    }

    private fun document(html: String): Document = cleaner.clean(Jsoup.parse(html)).apply {
        outputSettings().prettyPrint(false)
    }
}
