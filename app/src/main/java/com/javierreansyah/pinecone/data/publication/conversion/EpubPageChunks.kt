package com.javierreansyah.pinecone.data.publication.conversion

import org.w3c.dom.Element
import java.io.Closeable
import java.io.File
import java.util.Locale

/** Source-page intervals only. File boundaries never infer chapters or divide DOM blocks. */
internal class EpubPageChunks(private val firstFile: File, private val header: String) : Closeable {
    data class Resource(val href: String, val file: File, var hasSvg: Boolean = false)

    val resources = mutableListOf<Resource>()
    private val anchors = mutableMapOf<String, String>()
    private var output = firstFile.bufferedWriter(Charsets.UTF_8)
    private var boundary = PAGES_PER_FILE
    private var previous: Element? = null
    private var captionNeedsFollowing = false

    init {
        resources += Resource(GeneratedEpubLocations.CONTENT_HREF, firstFile)
        output.write(header)
    }

    fun append(section: Element) {
        val page = section.getAttribute("data-source-page").toInt()
        val blocks = section.children().filterNot { it.localTag == "span" }
        val first = blocks.firstOrNull()
        if (page > boundary && first != null && safeBoundary(
                previous,
                first,
                captionNeedsFollowing
            )
        ) {
            output.write("</body></html>")
            output.close()
            val file = File.createTempFile("pinecone-content-", ".xhtml", firstFile.parentFile)
            val href =
                "text/content-${String.format(Locale.ROOT, "%04d", resources.size + 1)}.xhtml"
            resources += Resource(href, file)
            output = file.bufferedWriter(Charsets.UTF_8)
            output.write(header)
            boundary = ((page - 1) / PAGES_PER_FILE + 1) * PAGES_PER_FILE
        }
        val resource = resources.last()
        section.descendants().forEach { element ->
            element.getAttribute("id").takeIf(String::isNotBlank)?.let { id ->
                check(anchors.put(id, resource.href) == null) { "Duplicate generated anchor: $id" }
            }
        }
        resource.hasSvg = resource.hasSvg || section.getElementsByTagNameNS(
            "http://www.w3.org/2000/svg",
            "svg"
        ).length > 0
        output.write(EpubWriter.serialize(section).substringAfter("?>"))
        blocks.forEach { block ->
            captionNeedsFollowing = block.hasClass("caption") && !isVisual(previous)
            previous = block
        }
    }

    fun resourceForAnchor(anchor: String): String = anchors.getValue(anchor)
    fun hrefForAnchor(anchor: String): String = "${resourceForAnchor(anchor)}#$anchor"

    fun finish(write: (String, ByteArray) -> Unit) {
        output.write("</body></html>")
        output.close()
        // Resolve forward links after all anchor destinations are known. Only one
        // 25-page resource is parsed at a time; source-page DOMs stream to disk.
        resources.forEach { resource ->
            val root = MuPdfStructure.parse(resource.file.readText(Charsets.UTF_8))
            root.descendants("a").forEach { link ->
                val href = link.getAttribute("href")
                val path = href.substringBefore('#')
                val anchor = href.substringAfter('#', "")
                if (anchor in anchors && !path.contains(':') && !path.startsWith("//") &&
                    (path.isEmpty() || GeneratedEpubLocations.isContent(path) ||
                            path.matches(Regex("content(?:-\\d+)?\\.xhtml")))
                ) {
                    val target = resourceForAnchor(anchor)
                    link.setAttribute(
                        "href", if (target == resource.href) "#$anchor"
                        else "${target.substringAfterLast('/')}#$anchor"
                    )
                }
            }
            write(resource.href, EpubWriter.serialize(root).toByteArray(Charsets.UTF_8))
        }
    }

    override fun close() {
        try {
            output.close()
        } finally {
            resources.forEach { it.file.delete() }
        }
    }

    companion object {
        const val PAGES_PER_FILE = 25

        internal fun Element.hasClass(name: String) =
            getAttribute("class").split(Regex("\\s+")).contains(name)

        internal fun isVisual(element: Element?) = element != null &&
                (element.localTag in setOf("figure", "table") || element.hasClass("table-scroll"))

        internal fun safeBoundary(
            previous: Element?,
            first: Element?,
            captionNeedsFollowing: Boolean
        ): Boolean {
            if (previous == null || first == null) return true
            if (previous.localTag.matches(Regex("h[1-6]")) || captionNeedsFollowing) return false
            if (first.hasClass("caption") && isVisual(previous)) return false
            if (previous.localTag in setOf(
                    "ul",
                    "ol"
                ) && first.localTag == previous.localTag
            ) return false
            if (first.hasClass("reference-continuation") && previous.hasClass("reference")) return false
            return true
        }
    }
}
