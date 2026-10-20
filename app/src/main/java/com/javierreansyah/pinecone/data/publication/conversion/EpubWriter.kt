package com.javierreansyah.pinecone.data.publication.conversion

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.Closeable
import java.io.OutputStream
import java.io.StringWriter
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class ReflowBlock(
    val text: String = "",
    val heading: Boolean = false,
    val image: String? = null,
    val headingLevel: Int? = null,
    val kind: String = "paragraph",
    val sources: List<ReflowSource> = emptyList(),
    val table: ReflowTable? = null,
)

/** Streaming EPUB 3 writer. Buffers one section to join reference entries over page breaks. */
class EpubWriter(
    output: OutputStream,
    private val identifier: String,
    private val title: String,
    private val language: String,
    private val strings: GeneratedPublicationStrings,
    private val contentFile: File = File.createTempFile("pinecone-content-", ".xhtml"),
) : Closeable {
    private val zip = ZipOutputStream(output.buffered())
    private val pages = mutableListOf<Pair<String, String>>()
    private val images = mutableListOf<String>()
    private var finished = false
    private var pendingSection: Element? = null
    private val content = contentFile.bufferedWriter(Charsets.UTF_8)

    init {
        content.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        content.write("<html xmlns=\"$XHTML\" xmlns:epub=\"$EPUB\" xml:lang=\"${escapeXml(language)}\">")
        content.write("<head><title>${escapeXml(title)}</title>" +
            "<link rel=\"stylesheet\" href=\"../styles/book.css\" type=\"text/css\"/></head><body>")
        val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        zip.putNextEntry(ZipEntry("mimetype").apply {
            method = ZipEntry.STORED; size = mimetype.size.toLong(); compressedSize = size
            crc = CRC32().apply { update(mimetype) }.value
        })
        zip.write(mimetype); zip.closeEntry()
        write(
            "META-INF/container.xml",
            xml("container", "urn:oasis:names:tc:opendocument:xmlns:container") { root ->
                root.setAttribute("version", "1.0")
                root.child("rootfiles").child("rootfile").apply {
                    setAttribute("full-path", "EPUB/package.opf"); setAttribute(
                    "media-type",
                    "application/oebps-package+xml"
                )
                }
            }.toByteArray()
        )
        write(
            "EPUB/styles/book.css",
            ("body{margin:0;padding:0}img{max-width:100%;height:auto}figure{margin:1em 0}" +
                "p{orphans:2;widows:2;margin:0 0 .85em}h1,h2,h3,h4,h5,h6{font-weight:700;line-height:1.25;break-after:avoid}" +
                "h1{font-size:1.45em;margin:1.4em 0 .7em}h2{font-size:1.25em;margin:1.3em 0 .6em}" +
                "h3{font-size:1.1em;margin:1.1em 0 .5em}h4,h5,h6{font-size:1em;margin:1em 0 .4em}" +
                ".scan-notice{font-size:.85em;font-style:italic}.caption{font-size:.9em;line-height:1.35}" +
                ".text-bold{font-weight:700}.text-italic{font-style:italic}sup,sub{font-size:.75em;line-height:0}" +
                ".equation{margin:1em 0;text-align:center}.equation-label{margin-inline-start:1em}" +
                ".formula{vertical-align:-.15em;max-width:100%}.list-item{padding-inline-start:1.2em;text-indent:-1.2em}" +
                ".reference,.reference-continuation{padding-inline-start:1.6em;text-indent:-1.6em;overflow-wrap:anywhere}" +
                ".table-scroll{max-width:100%;overflow-x:auto;margin:1em 0}table{border-collapse:collapse;font-size:.9em}" +
                "th,td{padding:.35em .6em;text-align:start;vertical-align:top;border-bottom:1px solid currentColor}" +
                "th{font-weight:700}thead{display:table-header-group}tr{break-inside:avoid}" +
                ".math-transcript{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}").toByteArray()
        )
    }

    fun image(name: String, bytes: ByteArray) {
        require(name.matches(Regex("[a-zA-Z0-9_-]+\\.png")) && name !in images)
        images += name
        write("EPUB/images/$name", bytes)
    }

    fun page(
        number: Int,
        blocks: List<ReflowBlock>,
        scanned: Boolean = false,
        label: String = strings.pageLabel(number)
    ) {
        require(number == pages.size + 1)
        pages += GeneratedEpubLocations.pageHref(number) to label
        val section = element("section", XHTML) { section ->
            section.setAttribute("xmlns:epub", EPUB)
            section.setAttribute("id", "page-$number")
            section.setAttribute("data-source-page", number.toString())
            section.apply {
                child("span").apply {
                    setAttribute("id", "source-page-$number"); setAttributeNS(
                    EPUB,
                    "epub:type",
                    "pagebreak"
                )
                    setAttribute("role", "doc-pagebreak"); setAttribute("aria-label", label)
                }
                if (scanned) child("p", strings.scannedPageNotice)
                    .setAttribute("class", "scan-notice")
                blocks.forEachIndexed { index, block ->
                    if (block.table != null) {
                        child("div").apply {
                            setAttribute("class", "table-scroll")
                            appendTable(block)
                        }
                    } else if (block.sources.isNotEmpty()) {
                        val tag = block.headingLevel?.let { "h${it.coerceIn(1, 6)}" }
                            ?: when (block.kind) { "figure" -> "figure"; "equation" -> "div"; else -> "p" }
                        child(tag).apply {
                            setAttribute("class", if (block.kind == "list") "list-item" else block.kind)
                            block.sources.forEachIndexed { sourceIndex, source ->
                                val previous = block.sources.getOrNull(sourceIndex - 1)
                                if (previous != null && (block.headingLevel != null || block.kind.startsWith("reference")) &&
                                    !(previous.text.endsWith('-') && source.text.firstOrNull()?.isLowerCase() == true))
                                    appendChild(ownerDocument.createTextNode(" "))
                                appendSource(source, block.kind)
                            }
                        }
                    } else if (block.image != null) {
                        child("figure").apply {
                            setAttribute("id", GeneratedEpubLocations.blockAnchor(number, index))
                            child("img").apply {
                                setAttribute(
                                    "src",
                                    "../images/${block.image}"
                                ); setAttribute("alt", "")
                            }
                        }
                    } else if (block.text.isNotBlank()) {
                        child(if (block.heading) "h2" else "p", block.text).setAttribute(
                            "id",
                            GeneratedEpubLocations.blockAnchor(number, index)
                        )
                    }
                }
            }
        }
        val previous = pendingSection
        val continuation = (0 until section.childNodes.length).map { section.childNodes.item(it) }
            .filterIsInstance<Element>().firstOrNull { it.getAttribute("class") == "reference-continuation" }
        val trailing = previous?.lastChild as? Element
        if (continuation != null && trailing?.getAttribute("class") in setOf("reference", "reference-continuation") &&
            section.getElementsByTagNameNS(XHTML, "p").item(0) == continuation) {
            val target = requireNotNull(trailing)
            val pageBreak = section.firstChild
            target.appendChild(target.ownerDocument.importNode(pageBreak, true))
            section.removeChild(pageBreak)
            target.appendChild(target.ownerDocument.createTextNode(" "))
            while (continuation.hasChildNodes()) {
                val node = continuation.firstChild
                target.appendChild(target.ownerDocument.importNode(node, true))
                continuation.removeChild(node)
            }
            section.removeChild(continuation)
        }
        flushPending()
        pendingSection = section
    }

    private fun flushPending() {
        pendingSection?.let { content.write(serialize(it).substringAfter("?>")) }
        pendingSection = null
    }

    private fun Element.appendSource(source: ReflowSource, kind: String, indices: List<Int> = source.runs.indices.toList(),
        anchor: Boolean = true) {
        child("span").apply {
            if (anchor) setAttribute("id", source.anchor)
            if (source.equationLabel) setAttribute("class", "equation-label")
            if (source.hidden) setAttribute("class", "math-transcript")
            indices.forEach { runIndex ->
                val run = source.runs[runIndex]
                val runTag = when (run.script) { 1 -> "sup"; -1 -> "sub"; else -> "span" }
                child(runTag).apply {
                    setAttribute("id", "${source.anchor}-run-$runIndex")
                    setAttribute("class", listOfNotNull(if (run.bold) "text-bold" else null,
                        if (run.italic) "text-italic" else null,
                        if (run.hidden) "math-transcript" else null).joinToString(" "))
                    if (run.image != null) child("img").apply {
                        setAttribute("src", "../images/${run.image}")
                        setAttribute("alt", run.text)
                        if (kind != "figure") setAttribute("class", "formula")
                        if (kind != "figure" && kind != "equation" && run.heightEm != null)
                            setAttribute("style", "height:${run.heightEm}em;width:auto")
                    } else appendChild(ownerDocument.createTextNode(cleanXmlText(run.text)))
                }
            }
        }
    }

    private fun Element.appendTable(block: ReflowBlock) {
        val layout = requireNotNull(block.table)
        val emitted = mutableSetOf<Int>()
        val runs = mutableMapOf<Int, MutableSet<Int>>()
        child("table").apply {
            var group: Element? = null
            val occupied = IntArray(layout.columns)
            layout.rows.forEachIndexed { rowIndex, cells ->
                if (rowIndex == 0 || rowIndex == layout.headerRows)
                    group = child(if (rowIndex < layout.headerRows) "thead" else "tbody")
                requireNotNull(group).child("tr").apply {
                    var column = 0
                    for (cell in cells) {
                        while (column < cell.column) {
                            if (occupied[column] <= rowIndex) child(if (rowIndex < layout.headerRows) "th" else "td")
                            column++
                        }
                        child(if (rowIndex < layout.headerRows) "th" else "td").apply {
                            if (cell.columnSpan > 1) setAttribute("colspan", cell.columnSpan.toString())
                            if (cell.rowSpan > 1) setAttribute("rowspan", cell.rowSpan.toString())
                            if (rowIndex < layout.headerRows) setAttribute("scope", if (cell.columnSpan > 1) "colgroup" else "col")
                            cell.parts.forEachIndexed { index, part ->
                                if (index > 0) appendChild(ownerDocument.createTextNode(" "))
                                appendSource(block.sources[part.source], "table", part.runs, emitted.add(part.source))
                                runs.getOrPut(part.source) { mutableSetOf() }.addAll(part.runs)
                            }
                        }
                        repeat(cell.columnSpan) { occupied[cell.column + it] = rowIndex + cell.rowSpan }
                        column = cell.column + cell.columnSpan
                    }
                    while (column < layout.columns) {
                        if (occupied[column] <= rowIndex) child(if (rowIndex < layout.headerRows) "th" else "td")
                        column++
                    }
                }
            }
        }
        block.sources.forEachIndexed { index, source ->
            val unused = source.runs.indices.filter { it !in runs[index].orEmpty() }
            if (index !in emitted || unused.isNotEmpty())
                appendSource(source.copy(hidden = true), "table", unused, index !in emitted)
        }
    }

    fun finish(outline: List<Pair<String, Int>> = emptyList()) {
        check(!finished && pages.isNotEmpty())
        flushPending()
        content.write("</body></html>")
        content.close()
        zip.putNextEntry(ZipEntry("EPUB/${GeneratedEpubLocations.CONTENT_HREF}"))
        contentFile.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        write("EPUB/nav.xhtml", xml("html", XHTML) { html ->
            html.setAttribute("xmlns:epub", EPUB)
            html.setAttribute("xml:lang", language)
            html.child("head").child("title", title)
            html.child("body").apply {
                child("nav").apply {
                    setAttributeNS(EPUB, "epub:type", "toc"); setAttribute("id", "toc")
                    child("h1", strings.contents)
                    child("ol").apply {
                        val entries = outline.filter { it.second in 1..pages.size }
                        (entries.ifEmpty { pages.mapIndexed { index, page -> page.second to index + 1 } }).forEach { (name, page) ->
                            child("li").child("a", name).setAttribute("href", pages[page - 1].first)
                        }
                    }
                }
                child("nav").apply {
                    setAttributeNS(EPUB, "epub:type", "page-list"); setAttribute("id", "pages")
                    child("ol").apply {
                        pages.forEach { page ->
                            child("li").child("a", page.second)
                                .setAttribute("href", page.first)
                        }
                    }
                }
            }
        }.toByteArray())
        write("EPUB/package.opf", xml("package", OPF) { root ->
            root.setAttribute("version", "3.0"); root.setAttribute(
            "unique-identifier",
            "publication-id"
        )
            root.child("metadata").apply {
                setAttribute("xmlns:dc", DC)
                ownerDocument.createElementNS(DC, "dc:identifier").also {
                    it.setAttribute("id", "publication-id"); it.textContent =
                    identifier; appendChild(it)
                }
                ownerDocument.createElementNS(DC, "dc:title")
                    .also { it.textContent = title; appendChild(it) }
                ownerDocument.createElementNS(DC, "dc:language")
                    .also { it.textContent = language; appendChild(it) }
                child(
                    "meta",
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
                ).setAttribute("property", "dcterms:modified")
            }
            root.child("manifest").apply {
                fun item(id: String, href: String, media: String): Element = child("item").apply {
                    setAttribute("id", id); setAttribute("href", href); setAttribute(
                    "media-type",
                    media
                )
                }
                item("nav", "nav.xhtml", "application/xhtml+xml").setAttribute("properties", "nav")
                item("css", "styles/book.css", "text/css")
                item("content", GeneratedEpubLocations.CONTENT_HREF, "application/xhtml+xml")
                images.forEachIndexed { index, name ->
                    item(
                        "image$index",
                        "images/$name",
                        "image/png"
                    )
                }
            }
            root.child("spine").apply {
                child("itemref").setAttribute("idref", "content")
            }
        }.toByteArray())
        zip.finish()
        finished = true
    }

    private fun write(path: String, bytes: ByteArray) {
        check(!finished)
        zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
    }

    override fun close() {
        try {
            content.close()
        } finally {
            try { zip.close() } finally { contentFile.delete() }
        }
    }

    companion object {
        private const val XHTML = "http://www.w3.org/1999/xhtml"
        private const val EPUB = "http://www.idpf.org/2007/ops"
        private const val OPF = "http://www.idpf.org/2007/opf"
        private const val DC = "http://purl.org/dc/elements/1.1/"

        internal fun Element.child(name: String, text: String? = null): Element =
            ownerDocument.createElementNS(namespaceURI, name).also {
                if (text != null) it.textContent = cleanXmlText(text)
                appendChild(it)
            }

        internal fun xml(rootName: String, namespace: String, build: (Element) -> Unit): String =
            serialize(element(rootName, namespace, build))

        private fun element(rootName: String, namespace: String, build: (Element) -> Unit): Element {
            val document: Document =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
            val root = document.createElementNS(namespace, rootName)
            document.appendChild(root); build(root)
            return root
        }

        private fun serialize(root: Element): String = StringWriter().also { writer ->
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.METHOD, "xml")
            }.transform(DOMSource(root), StreamResult(writer))
        }.toString()

        private fun escapeXml(text: String): String = cleanXmlText(text)
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

        private fun cleanXmlText(text: String): String = buildString {
            text.codePoints().forEach { cp ->
                if (cp == 9 || cp == 10 || cp == 13 || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF) appendCodePoint(
                    cp
                )
            }
        }
    }
}
