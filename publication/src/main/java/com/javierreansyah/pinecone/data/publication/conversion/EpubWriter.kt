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

    private fun flushPending() {
        pendingSection?.let(content::append)
        pendingSection = null
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
