package com.javierreansyah.pinecone.data.publication.conversion

import org.w3c.dom.Document
import org.w3c.dom.Element
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
    val image: String? = null
)

/** Streaming EPUB 3 writer. Only a single source page and its images need be in memory. */
class EpubWriter(
    output: OutputStream,
    private val identifier: String,
    private val title: String,
    private val language: String,
    private val strings: GeneratedPublicationStrings,
) : Closeable {
    private val zip = ZipOutputStream(output.buffered())
    private val pages = mutableListOf<Pair<String, String>>()
    private val images = mutableListOf<String>()
    private var finished = false

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
            "body{margin:0;padding:0}img{max-width:100%;height:auto}figure{margin:1em 0}p{orphans:2;widows:2}.scan-notice{font-size:.85em;font-style:italic}".toByteArray()
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
        val href = "text/page-$number.xhtml"
        pages += href to label
        write("EPUB/$href", xml("html", XHTML) { html ->
            html.setAttribute("xmlns:epub", EPUB)
            html.setAttribute("xml:lang", language)
            html.child("head").apply {
                child("title", label)
                child("link").apply {
                    setAttribute("rel", "stylesheet"); setAttribute(
                    "href",
                    "../styles/book.css"
                ); setAttribute("type", "text/css")
                }
            }
            html.child("body").apply {
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
                    if (block.image != null) {
                        child("figure").apply {
                            setAttribute("id", "block-$index")
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
                            "block-$index"
                        )
                    }
                }
            }
        }.toByteArray())
    }

    fun finish(outline: List<Pair<String, Int>> = emptyList()) {
        check(!finished && pages.isNotEmpty())
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
                        pages.forEachIndexed { index, page ->
                            child("li").child("a", page.second)
                                .setAttribute("href", "${page.first}#source-page-${index + 1}")
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
                pages.forEachIndexed { index, page ->
                    item(
                        "page$index",
                        page.first,
                        "application/xhtml+xml"
                    )
                }
                images.forEachIndexed { index, name ->
                    item(
                        "image$index",
                        "images/$name",
                        "image/png"
                    )
                }
            }
            root.child("spine").apply {
                pages.indices.forEach {
                    child("itemref").setAttribute(
                        "idref",
                        "page$it"
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
        zip.close()
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

        internal fun xml(rootName: String, namespace: String, build: (Element) -> Unit): String {
            val document: Document =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
            val root = document.createElementNS(namespace, rootName)
            document.appendChild(root); build(root)
            return StringWriter().also { writer ->
                TransformerFactory.newInstance().newTransformer().apply {
                    setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                    setOutputProperty(OutputKeys.METHOD, "xml")
                }.transform(DOMSource(document), StreamResult(writer))
            }.toString()
        }

        private fun cleanXmlText(text: String): String = buildString {
            text.codePoints().forEach { cp ->
                if (cp == 9 || cp == 10 || cp == 13 || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF) appendCodePoint(
                    cp
                )
            }
        }
    }
}
