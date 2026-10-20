package com.javierreansyah.pinecone.data.publication.conversion

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.Serializable
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

@Serializable
internal data class MuPdfPageRecord(
    val xhtml: String, val bounds: FigureBounds, val blocks: List<MuPdfText>,
    val images: List<FigureBounds>, val vectors: List<FigureBounds>,
    val groups: List<MuPdfRegion>, val tables: List<MuPdfTable> = emptyList(),
)

@Serializable
internal data class MuPdfRegion(val kind: String, val bounds: FigureBounds)

@Serializable
internal data class MuPdfTable(val bounds: FigureBounds, val xhtml: String, val rows: List<List<MuPdfCell>> = emptyList())

@Serializable
internal data class MuPdfCell(val lines: List<MuPdfCellLine>)

@Serializable
internal data class MuPdfCellLine(val text: String, val top: Float)

@Serializable
internal data class MuPdfGlyph(
    val text: String, val bounds: FigureBounds, val baseline: Float, val size: Float,
    val bold: Boolean = false, val italic: Boolean = false, val math: Boolean = false,
)

@Serializable
internal data class MuPdfText(val bounds: FigureBounds, val glyphs: List<MuPdfGlyph>, val structure: String = "") {
    @kotlinx.serialization.Transient
    val text: String = glyphs.joinToString("") { it.text }
    @kotlinx.serialization.Transient
    val size: Float = glyphs.groupBy { (it.size * 4).toInt() }
        .maxByOrNull { it.value.size }?.key?.div(4f) ?: 12f
    @kotlinx.serialization.Transient
    val bold: Boolean = glyphs.count { it.bold } > glyphs.size / 2
}

/** MuPDF owns segmentation, tagged structure, glyph extraction and table geometry. */
internal object MuPdfStructure {
    fun read(xhtml: String, xml: String, tableXhtml: String, tableXml: String): MuPdfPageRecord {
        val root = parse(xml)
        val page = root.descendants("page").single()
        val pageBounds = FigureBounds(0f, 0f, page.getAttribute("width").toFloat(), page.getAttribute("height").toFloat())
        val blocks = mutableListOf<MuPdfText>()
        val images = mutableListOf<FigureBounds>()
        val vectors = mutableListOf<FigureBounds>()
        val groups = mutableListOf<MuPdfRegion>()
        fun visit(element: Element, structure: String = "") {
            when (element.localTag) {
                "struct" -> {
                    val kind = element.getAttribute("std")
                    element.box()?.let { groups += MuPdfRegion(kind, it) }
                    element.children().forEach { visit(it, if (kind == "Div") structure else kind) }
                }
                "block" -> {
                    val glyphs = mutableListOf<MuPdfGlyph>()
                    element.children().filter { it.localTag == "line" }.forEachIndexed { lineIndex, line ->
                        line.children().filter { it.localTag == "font" }.forEach { font ->
                            val name = font.getAttribute("name")
                            val size = font.getAttribute("size").toFloatOrNull() ?: 12f
                            val bold = Regex("bold|black|heavy", RegexOption.IGNORE_CASE).containsMatchIn(name)
                            val italic = Regex("italic|oblique", RegexOption.IGNORE_CASE).containsMatchIn(name)
                            val math = Regex("math|symbol|cmsy|cmex|cmmi|msam|msbm|stix", RegexOption.IGNORE_CASE).containsMatchIn(name)
                            font.children().filter { it.localTag == "char" }.forEach { char ->
                                val quad = char.getAttribute("quad").numbers()
                                if (quad.size == 8) {
                                    val xs = quad.filterIndexed { index, _ -> index % 2 == 0 }
                                    val ys = quad.filterIndexed { index, _ -> index % 2 == 1 }
                                    val box = FigureBounds(xs.min(), ys.min(), xs.max(), ys.max())
                                    val text = char.getAttribute("c")
                                    val flags = char.getAttribute("flags").toIntOrNull() ?: 0
                                    glyphs += MuPdfGlyph(text, box, char.getAttribute("y").toFloatOrNull() ?: box.bottom,
                                        size, bold || flags and 8 != 0, italic, math)
                                }
                            }
                        }
                        if (lineIndex < element.children().count { it.localTag == "line" } - 1 && glyphs.isNotEmpty())
                            glyphs += glyphs.last().copy(text = " ")
                    }
                    element.box()?.let { blocks += MuPdfText(it, glyphs, structure) }
                }
                "image" -> element.box()?.let { images += it }
                "vector" -> element.box()?.let { vectors += it }
                else -> element.children().forEach { visit(it, structure) }
            }
        }
        visit(page)
        val tableGeometry = parse(tableXml).descendants("struct").filter { it.getAttribute("std") == "Table" }
        val tableElements = parse(tableXhtml).descendants("table")
        val tables = tableGeometry.zip(tableElements).mapNotNull { (geometry, element) ->
            val rows = geometry.children().filter { it.getAttribute("std") == "TR" }.map { row ->
                row.children().filter { it.getAttribute("std") == "TD" }.map { cell ->
                    MuPdfCell(cell.descendants("line").map { line ->
                        MuPdfCellLine(line.getAttribute("text"), line.box()?.top ?: 0f)
                    })
                }
            }
            geometry.box()?.let { MuPdfTable(it, EpubWriter.serialize(element), rows) }
        }
        return MuPdfPageRecord(xhtml, pageBounds, blocks, images, vectors, groups, tables)
    }

    /** Native exports have a known XHTML doctype; never fetch a DTD or resolve external entities. */
    fun parse(xml: String): Element {
        require(!xml.contains("<!ENTITY", true))
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        // Android's Harmony parser does not implement the Xerces external-entity feature
        // switches. Remove MuPDF's fixed doctype and reject all entity declarations instead.
        val input = xml.replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("&#(?:x([0-9a-fA-F]+)|([0-9]+));")) { match ->
                val cp = if (match.groupValues[1].isNotEmpty()) match.groupValues[1].toLongOrNull(16)
                    else match.groupValues[2].toLongOrNull()
                if (cp != null && (cp == 9L || cp == 10L || cp == 13L || cp in 0x20L..0xD7FFL ||
                        cp in 0xE000L..0xFFFDL || cp in 0x10000L..0x10FFFFL)) match.value else "\uFFFD"
            }
        require(!input.contains("<!DOCTYPE", true) && !input.contains("<!ENTITY", true))
        return factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
            .parse(InputSource(StringReader(input))).documentElement
    }

    private fun String.numbers() = trim().split(Regex("\\s+")).mapNotNull(String::toFloatOrNull)
    private fun Element.box(): FigureBounds? = getAttribute("bbox").numbers().takeIf { it.size == 4 }
        ?.let { FigureBounds(it[0], it[1], it[2], it[3]) }
}

internal val Element.localTag: String get() = localName ?: tagName
internal fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
internal fun Element.descendants(tag: String? = null): List<Element> = buildList {
    fun visit(element: Element) {
        if (tag == null || element.localTag == tag) add(element)
        element.children().forEach(::visit)
    }
    visit(this@descendants)
}
internal fun Node.textNodes(): List<Node> = buildList {
    fun visit(node: Node) {
        if (node is Element && node.namespaceURI == "http://www.w3.org/2000/svg") return
        if (node.nodeType == Node.TEXT_NODE) add(node)
        else (0 until node.childNodes.length).forEach { visit(node.childNodes.item(it)) }
    }
    visit(this@textNodes)
}
