package com.javierreansyah.pinecone.data.publication.conversion

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.Closeable
import java.io.File
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
    val listMarker: ReflowListMarker? = null,
)

/** Streaming EPUB 3 writer with 25-source-page files and stable navigation anchors. */
class EpubWriter(
    output: OutputStream,
    private val identifier: String,
    private val title: String,
    private val language: String,
    private val strings: GeneratedPublicationStrings,
    contentFile: File = File.createTempFile("pinecone-content-", ".xhtml"),
) : Closeable {
    private val zip = ZipOutputStream(output.buffered())
    private val pages = mutableListOf<Pair<String, String>>()
    private val images = mutableMapOf<String, String>()
    private val outlineAnchors = mutableMapOf<Pair<Int, String>, String>()
    private var finished = false
    private var pendingSection: Element? = null
    private val content = EpubPageChunks(
        contentFile,
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<html xmlns=\"$XHTML\" xmlns:epub=\"$EPUB\" xml:lang=\"${escapeXml(language)}\">" +
                "<head><title>${escapeXml(title)}</title>" +
                "<link rel=\"stylesheet\" href=\"../styles/book.css\" type=\"text/css\"/></head><body>"
    )

    internal fun resourceForAnchor(anchor: String): String = content.resourceForAnchor(anchor)
    internal fun hrefForAnchor(anchor: String): String = content.hrefForAnchor(anchor)

    init {
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
                    "figure>img{width:100%;max-height:85vh;object-fit:contain;background:#fff}" +
                    "p{orphans:2;widows:2;margin:0 0 .85em}h1,h2,h3,h4,h5,h6{font-weight:700;line-height:1.25;break-after:avoid}" +
                    "h1{font-size:1.45em;margin:1.4em 0 .7em}h2{font-size:1.25em;margin:1.3em 0 .6em}" +
                    "h3{font-size:1.1em;margin:1.1em 0 .5em}h4,h5,h6{font-size:1em;margin:1em 0 .4em}" +
                    ".scan-notice{font-size:.85em;font-style:italic}.caption{font-size:.9em;line-height:1.35}" +
                    ".text-bold{font-weight:700}.text-italic{font-style:italic}sup,sub{font-size:.75em;line-height:0}" +
                    ".equation{margin:1em 0;text-align:center;overflow-x:auto;text-indent:0!important}.equation-label{margin-inline-start:1em}" +
                    ".formula{vertical-align:-.15em;max-width:100%}.math-layout{display:inline-block;max-width:none;fill:currentColor;overflow:visible}" +
                    ".reflow-list{list-style:none;padding:0;margin:.3em 0 .85em}" +
                    ".reflow-list .list-item{display:grid;grid-template-columns:var(--list-marker-width,.6em) minmax(0,1fr);column-gap:.45em;padding:0;margin:0 0 .65em;text-indent:0!important}" +
                    ".list-marker{text-align:start;font-variant-numeric:tabular-nums}.list-content{min-width:0;text-indent:0!important}.list-item>.reflow-list{grid-column:2}" +
                    ".reference,.reference-continuation{padding-inline-start:0;text-indent:0!important;overflow-wrap:anywhere}" +
                    ".table-scroll{max-width:100%;max-height:calc(100vh - 2em);overflow:auto;overscroll-behavior:contain;margin:1em 0}table{border-collapse:collapse;font-size:.9em}" +
                    "th,td{padding:.35em .6em;text-align:start;vertical-align:top;border-bottom:1px solid currentColor}" +
                    "th{font-weight:700}thead{display:table-header-group}tr{break-inside:avoid}" +
                    ".math-transcript{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}").toByteArray()
        )
    }

    fun image(name: String, bytes: ByteArray) {
        asset(name, "image/png", bytes)
    }

    internal fun asset(name: String, mediaType: String, bytes: ByteArray) {
        val extension = when (mediaType) {
            "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/svg+xml" -> "svg"
            else -> error("Unsupported generated asset: $mediaType")
        }
        require(name.matches(Regex("[a-zA-Z0-9_-]+\\.$extension")) && name !in images)
        images[name] = mediaType
        write("EPUB/images/$name", bytes)
    }

    /** Import cleaned MuPDF XHTML directly; no intermediate custom paragraph/table model. */
    internal fun nativePage(number: Int, body: Element, scanned: Boolean) {
        require(number == pages.size + 1)
        body.descendants().filter { it.localTag.matches(Regex("h[1-6]")) && it.hasAttribute("id") }
            .forEach {
                outlineAnchors.putIfAbsent(
                    number to outlineKey(it.textContent),
                    it.getAttribute("id")
                )
            }
        val label = strings.pageLabel(number)
        pages += GeneratedEpubLocations.pageHref(number) to label
        val section = element("section", XHTML) { section ->
            section.setAttribute("xmlns:epub", EPUB)
            section.setAttribute("id", "page-$number")
            section.setAttribute("data-source-page", number.toString())
            section.child("span").apply {
                setAttribute("id", "source-page-$number")
                setAttributeNS(EPUB, "epub:type", "pagebreak")
                setAttribute("role", "doc-pagebreak"); setAttribute("aria-label", label)
            }
            if (scanned) section.child("p", strings.scannedPageNotice)
                .setAttribute("class", "scan-notice")
            body.children()
                .forEach { section.appendChild(section.ownerDocument.importNode(it, true)) }
        }
        val continuation =
            section.children().firstOrNull { it.getAttribute("class") == "reference-continuation" }
        val trailing = pendingSection?.lastChild as? Element
        if (continuation != null && section.children().getOrNull(1) === continuation &&
            trailing?.getAttribute("class") in setOf("reference", "reference-continuation")
        ) {
            val target = requireNotNull(trailing)
            target.appendChild(target.ownerDocument.importNode(section.firstChild, true))
            section.removeChild(section.firstChild)
            target.appendChild(target.ownerDocument.createTextNode(" "))
            val holder = target.ownerDocument.createElementNS(XHTML, "span").apply {
                if (continuation.hasAttribute("id")) setAttribute(
                    "id",
                    continuation.getAttribute("id")
                )
            }
            target.appendChild(holder)
            continuation.childNodes.let { nodes -> (0..<nodes.length).map { nodes.item(it) } }
                .forEach { holder.appendChild(target.ownerDocument.importNode(it, true)) }
            section.removeChild(continuation)
        }
        flushPending()
        pendingSection = section
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
                val lists = mutableListOf<Element>()
                val lastItems = mutableListOf<Element>()
                blocks.forEachIndexed { index, block ->
                    if (block.listMarker == null) {
                        lists.clear(); lastItems.clear()
                    }
                    if (block.table != null) {
                        child("div").apply {
                            setAttribute("class", "table-scroll")
                            appendTable(block)
                        }
                    } else if (block.sources.isNotEmpty()) {
                        val tag = block.headingLevel?.let { "h${it.coerceIn(1, 6)}" }
                            ?: when (block.kind) {
                                "figure" -> "figure"; "equation" -> "div"; else -> "p"
                            }
                        val list = block.listMarker
                        val item = if (list == null) null else {
                            val depth = list.depth.coerceAtMost(lists.size)
                            val type = if (list.value != null) "ol" else "ul"
                            while (lists.size > depth + 1) {
                                lists.removeAt(lists.lastIndex); lastItems.removeAt(lastItems.lastIndex)
                            }
                            if (lists.getOrNull(depth)?.localName != type) {
                                if (lists.size > depth) {
                                    lists.removeAt(depth); lastItems.removeAt(depth)
                                }
                                val container =
                                    (if (depth == 0) this else lastItems[depth - 1]).child(type)
                                container.setAttribute("class", "reflow-list")
                                container.setAttribute("role", "list")
                                if (list.value != null) {
                                    val digits =
                                        blocks.drop(index).takeWhile { it.listMarker != null }
                                            .mapNotNull { it.listMarker?.takeIf { marker -> marker.depth == list.depth && marker.value != null } }
                                            .maxOfOrNull { it.text.takeWhile(Char::isDigit).length }
                                            ?: list.value.toString().length
                                    container.setAttribute(
                                        "style",
                                        "--list-marker-width:calc(${digits}ch + .4em)"
                                    )
                                }
                                if (list.value != null) container.setAttribute(
                                    "start",
                                    list.value.toString()
                                )
                                lists += container
                                lastItems += container
                            }
                            lists[depth].child("li").also {
                                if (list.value != null) it.setAttribute(
                                    "value",
                                    list.value.toString()
                                )
                                it.child("span", list.text).setAttribute("class", "list-marker")
                                lastItems[depth] = it
                            }
                        }
                        (item ?: child(tag)).apply {
                            setAttribute(
                                "class",
                                if (block.kind == "list") "list-item" else block.kind
                            )
                            val textContainer = if (item == null) this else child("div").apply {
                                setAttribute(
                                    "class",
                                    "list-content"
                                )
                            }
                            block.sources.forEachIndexed { sourceIndex, source ->
                                val previous = block.sources.getOrNull(sourceIndex - 1)
                                if (previous != null && (block.headingLevel != null || block.kind == "list" || block.kind.startsWith(
                                        "reference"
                                    )) &&
                                    !(previous.text.endsWith('-') && source.text.firstOrNull()
                                        ?.isLowerCase() == true)
                                )
                                    textContainer.appendChild(ownerDocument.createTextNode(" "))
                                textContainer.appendSource(source, block.kind)
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
        val continuation = (0..<section.childNodes.length).map { section.childNodes.item(it) }
            .filterIsInstance<Element>()
            .firstOrNull { it.getAttribute("class") == "reference-continuation" }
        val trailing = previous?.lastChild as? Element
        if (continuation != null && trailing?.getAttribute("class") in setOf(
                "reference",
                "reference-continuation"
            ) &&
            section.getElementsByTagNameNS(XHTML, "p").item(0) == continuation
        ) {
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
        pendingSection?.let(content::append)
        pendingSection = null
    }

    private fun Element.appendSource(
        source: ReflowSource, kind: String, indices: List<Int> = source.runs.indices.toList(),
        anchor: Boolean = true
    ) {
        child("span").apply {
            if (anchor) setAttribute("id", source.anchor)
            if (source.equationLabel) setAttribute("class", "equation-label")
            if (source.hidden) setAttribute("class", "math-transcript")
            if (source.hidden) setAttribute("aria-hidden", "true")
            indices.forEach { runIndex ->
                val run = source.runs[runIndex]
                val runTag = when (run.script) {
                    1 -> "sup"; -1 -> "sub"; else -> "span"
                }
                child(runTag).apply {
                    setAttribute("id", "${source.anchor}-run-$runIndex")
                    setAttribute(
                        "class", listOfNotNull(
                            if (run.bold) "text-bold" else null,
                            if (run.italic) "text-italic" else null,
                            if (run.hidden) "math-transcript" else null
                        ).joinToString(" ")
                    )
                    if (run.hidden) setAttribute("aria-hidden", "true")
                    if (run.math != null && !run.hidden) appendMath(run.math, run.text)
                    else if (run.image != null) child("img").apply {
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

    private fun Element.appendMath(math: ReflowMath, transcript: String) {
        val box = math.bounds
        val svg = ownerDocument.createElementNS(SVG, "svg")
        appendChild(svg)
        svg.apply {
            setAttribute("class", "math-layout")
            setAttribute("viewBox", "${box.left} ${box.top} ${box.width} ${box.height}")
            setAttribute("role", "img")
            setAttribute(
                "aria-label",
                cleanXmlText(math.texts.joinToString("") { it.text }.ifBlank { transcript })
            )
            setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve")
            setAttribute(
                "style",
                "width:${box.width / math.bodySize}em;height:${box.height / math.bodySize}em;" +
                        "vertical-align:${-(box.bottom - math.baseline) / math.bodySize}em"
            )
            math.texts.filterNot { it.radical }.forEach { text ->
                child("text", text.text).apply {
                    setAttribute("x", text.x.toString()); setAttribute(
                    "y",
                    text.baseline.toString()
                )
                    setAttribute("font-size", text.size.toString())
                    setAttribute("font-family", "serif")
                    setAttribute("font-weight", if (text.bold) "700" else "400")
                    setAttribute("font-style", if (text.italic) "italic" else "normal")
                    setAttribute("textLength", text.width.toString()); setAttribute(
                    "lengthAdjust",
                    "spacingAndGlyphs"
                )
                }
            }
            math.rules.forEach { rule ->
                child("line").apply {
                    setAttribute("x1", rule.left.toString()); setAttribute(
                    "x2",
                    rule.right.toString()
                )
                    val y = ((rule.top + rule.bottom) / 2).toString()
                    setAttribute("y1", y); setAttribute("y2", y)
                    setAttribute("stroke", "currentColor"); setAttribute(
                    "stroke-width",
                    rule.height.coerceAtLeast(.4f).toString()
                )
                }
            }
            math.radicals.forEach { root ->
                val bounds = root.bounds
                val width = root.shoulder - bounds.left
                val height = bounds.height
                child("path").apply {
                    setAttribute("class", "math-radical")
                    setAttribute(
                        "d", "M ${bounds.left} ${bounds.top + height * .58f} " +
                                "L ${bounds.left + width * .23f} ${bounds.top + height * .43f} " +
                                "L ${bounds.left + width * .58f} ${bounds.bottom} L ${root.shoulder} ${bounds.top} H ${bounds.right}"
                    )
                    setAttribute("fill", "none"); setAttribute("stroke", "currentColor")
                    setAttribute(
                        "stroke-width",
                        root.strokeWidth.toString()
                    ); setAttribute("stroke-linejoin", "round")
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
                    for ((column1, parts, columnSpan, rowSpan) in cells) {
                        while (column < column1) {
                            if (occupied[column] <= rowIndex) child(if (rowIndex < layout.headerRows) "th" else "td")
                            column++
                        }
                        child(if (rowIndex < layout.headerRows) "th" else "td").apply {
                            if (columnSpan > 1) setAttribute(
                                "colspan",
                                columnSpan.toString()
                            )
                            if (rowSpan > 1) setAttribute("rowspan", rowSpan.toString())
                            if (rowIndex < layout.headerRows) setAttribute(
                                "scope",
                                if (columnSpan > 1) "colgroup" else "col"
                            )
                            parts.forEachIndexed { index, part ->
                                if (index > 0) appendChild(ownerDocument.createTextNode(" "))
                                appendSource(
                                    block.sources[part.source],
                                    "table",
                                    part.runs,
                                    emitted.add(part.source)
                                )
                                runs.getOrPut(part.source) { mutableSetOf() }.addAll(part.runs)
                            }
                        }
                        repeat(columnSpan) {
                            occupied[column1 + it] = rowIndex + rowSpan
                        }
                        column = column1 + columnSpan
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
        content.finish { href, bytes -> write("EPUB/$href", bytes) }
        pages.indices.forEach { index ->
            pages[index] = content.hrefForAnchor("source-page-${index + 1}") to pages[index].second
        }
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
                        entries.ifEmpty { pages.mapIndexed { index, page -> page.second to index + 1 } }
                            .forEach { (name, page) ->
                                val anchor = outlineAnchors[page to outlineKey(name)]
                                child("li").child("a", name).setAttribute(
                                    "href",
                                    anchor?.let(content::hrefForAnchor) ?: pages[page - 1].first
                                )
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
                content.resources.forEachIndexed { index, resource ->
                    item(
                        if (index == 0) "content" else "content-${index + 1}",
                        resource.href,
                        "application/xhtml+xml"
                    ).apply {
                        if (resource.hasSvg) setAttribute("properties", "svg")
                    }
                }
                images.entries.forEachIndexed { index, (name, mediaType) ->
                    item(
                        "image$index",
                        "images/$name",
                        mediaType
                    )
                }
            }
            root.child("spine").apply {
                content.resources.indices.forEach { index ->
                    child("itemref").setAttribute(
                        "idref",
                        if (index == 0) "content" else "content-${index + 1}"
                    )
                }
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
        zip.use { zip ->
            content.close()
        }
    }

    companion object {
        private const val XHTML = "http://www.w3.org/1999/xhtml"
        private const val SVG = "http://www.w3.org/2000/svg"
        private const val EPUB = "http://www.idpf.org/2007/ops"
        private const val OPF = "http://www.idpf.org/2007/opf"
        private const val DC = "http://purl.org/dc/elements/1.1/"
        internal fun outlineKey(text: String) =
            text.trim().lowercase().replace(Regex("^\\d+(?:\\.\\d+)*[.)]?\\s+"), "")
                .replace(Regex("\\s+"), " ")

        internal fun Element.child(name: String, text: String? = null): Element =
            ownerDocument.createElementNS(namespaceURI, name).also {
                if (text != null) it.textContent = cleanXmlText(text)
                appendChild(it)
            }

        internal fun xml(rootName: String, namespace: String, build: (Element) -> Unit): String =
            serialize(element(rootName, namespace, build))

        internal fun element(
            rootName: String,
            namespace: String,
            build: (Element) -> Unit
        ): Element {
            val document: Document =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
            val root = document.createElementNS(namespace, rootName)
            document.appendChild(root); build(root)
            return root
        }

        internal fun serialize(root: Element): String =
            repairSerializedUnicode(StringWriter().also { writer ->
                TransformerFactory.newInstance().newTransformer().apply {
                    setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                    setOutputProperty(OutputKeys.METHOD, "xml")
                }.transform(DOMSource(root), StreamResult(writer))
            }.toString())

        private val surrogateReferences = Regex(
            "&#(5[56]\\d{3}|[xX][dD][89aAbB][\\da-fA-F]{2});&#(5[67]\\d{3}|[xX][dD][c-fC-F][\\da-fA-F]{2});"
        )

        /** Android's XML transformer can escape a valid UTF-16 pair as two illegal XML references. */
        internal fun repairSerializedUnicode(xml: String): String =
            surrogateReferences.replace(xml) { match ->
                fun number(value: String) =
                    if (value.startsWith('x', ignoreCase = true)) value.drop(1)
                        .toInt(16) else value.toInt()

                val high = number(match.groupValues[1]).toChar()
                val low = number(match.groupValues[2]).toChar()
                if (Character.isHighSurrogate(high) && Character.isLowSurrogate(low))
                    "&#${Character.toCodePoint(high, low)};"
                else match.value
            }

        internal fun escapeXml(text: String): String = cleanXmlText(text)
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

        internal fun cleanXmlText(text: String): String = buildString {
            text.codePoints().forEach { cp ->
                if (cp == 9 || cp == 10 || cp == 13 || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF) appendCodePoint(
                    cp
                )
            }
        }
    }
}
