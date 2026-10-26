package com.javierreansyah.pinecone.ui.components

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

    fun containsMarkup(content: String): Boolean =
        Jsoup.parse(content).body().children().isNotEmpty()

    private fun document(html: String): Document = cleaner.clean(Jsoup.parse(html)).apply {
        outputSettings().prettyPrint(false)
    }
}
