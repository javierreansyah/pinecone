package com.javierreansyah.pinecone.data.publication.conversion

import java.util.Base64
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Small document-wide style/margin statistics; native page records stay on disk. */
internal class MuPdfReflowProfile {
    private val sizes = mutableMapOf<Int, Int>()
    private val allSizes = mutableMapOf<Int, Int>()
    private val margins = mutableMapOf<String, Int>()
    private var pages = 0
    val bodySize: Float get() = (sizes.ifEmpty { allSizes }.maxByOrNull { it.value }?.key ?: 48) / 4f
    fun observe(page: MuPdfPageRecord) {
        pages++
        page.blocks.forEach { block ->
            block.glyphs.filter { it.text.isNotBlank() }.forEach {
                val key = (it.size.coerceIn(1f, 200f) * 4).toInt()
                allSizes[key] = (allSizes[key] ?: 0) + 1
                if (!it.math) sizes[key] = (sizes[key] ?: 0) + 1
            }
        }
        page.blocks.filter { margin(it.bounds, page.bounds) }.map { it.text.trim() }.distinct().forEach {
            if (margins.size < 2048 || it in margins) margins[it] = (margins[it] ?: 0) + 1
        }
    }
    fun repeated(text: String): Boolean = (margins[text.trim()] ?: 0) >= max(3, pages / 3)
    fun margin(box: FigureBounds, page: FigureBounds): Boolean =
        box.bottom < page.top + page.height * .08f || box.top > page.bottom - page.height * .10f
}

internal data class MuPdfCleanPage(val body: Element, val sources: List<SourceBlock>, val scanned: Boolean)
internal data class MuPdfVisual(val name: String, val markup: String)

/** Native XHTML is the content model. This adapter only normalizes it for a reflowable EPUB. */
internal class MuPdfReflowCleaner(private val profile: MuPdfReflowProfile) {
    private data class UnitBlock(
        var element: Element, var texts: List<MuPdfText>, var bounds: FigureBounds,
        var kind: String = "paragraph", var marker: String? = null,
    ) {
        val text: String get() = element.textContent.trim()
    }
    private data class PositionedChar(val start: Int, val end: Int, val glyph: MuPdfGlyph)
    private var bibliography = false
    private var assets = 0
    private val bodySize = profile.bodySize

    fun clean(page: MuPdfPageRecord, number: Int, previous: List<SourceBlock>, outline: Set<String>,
        svg: (FigureBounds, Boolean) -> MuPdfVisual, asset: (String, String, ByteArray) -> Unit): MuPdfCleanPage {
        val root = MuPdfStructure.parse(page.xhtml)
        val document = root.ownerDocument
        val body = document.createElementNS(XHTML, "div")
        val used = mutableSetOf<MuPdfText>()
        fun match(element: Element, scope: FigureBounds = page.bounds): MuPdfText? {
            val text = folded(element.textContent)
            if (text.isEmpty()) return null
            val candidates = page.blocks.filter { scope.containsCenter(it.bounds) }
            val exact = candidates.filter { folded(it.text) == text }
            val choices = exact.ifEmpty { candidates.filter {
                val native = folded(it.text)
                native.contains(text) || (native.length > 3 && text.contains(native))
            } }
            return (choices.firstOrNull { it !in used } ?: choices.firstOrNull())?.also { used += it }
        }
        var imageIndex = 0
        val units = mutableListOf<UnitBlock>()
        fun flatten(element: Element) {
            val tag = element.localTag.lowercase()
            val children = element.children()
            if (tag == "img") {
                val box = page.images.getOrNull(imageIndex++) ?: page.bounds
                units += UnitBlock(element, emptyList(), box, "figure")
            } else if (tag in TEXT_TAGS && children.none { it.descendants().any { child -> child.localTag.lowercase() in TEXT_TAGS } }) {
                if (element.descendants("img").isNotEmpty()) children.forEach(::flatten)
                else if (element.textContent.isNotBlank()) {
                    val metadata = match(element)
                    units += UnitBlock(element, listOfNotNull(metadata), metadata?.bounds ?: page.bounds)
                }
            } else children.forEach(::flatten)
        }
        root.descendants("body").single().children().forEach(::flatten)

        val scanned = page.blocks.sumOf { it.text.count { char -> !char.isWhitespace() } } < 5
        if (scanned) {
            val figure = visual(document.createElementNS(XHTML, "figure"), page.bounds, "", svg, false, true)
            units.clear(); units += UnitBlock(figure, emptyList(), page.bounds, "figure")
        } else {
            // Use MuPDF's table cells/spans as-is; reject prose, reference and figure false positives.
            val tables = page.tables.filter { validTable(it, page) }
            val charts = chartRegions(page, tables)
            charts.forEach { region -> replaceVisual(units, region, "figure", svg) }
            tables.filter { table -> charts.none { it.intersects(table.bounds) } }.forEach { table ->
                val selected = units.filter { table.bounds.containsCenter(it.bounds) }
                if (selected.isNotEmpty()) {
                    val element = document.importNode(MuPdfStructure.parse(table.xhtml), true) as Element
                    expandNativeTableRows(element, table)
                    val wrapper = document.createElementNS(XHTML, "div").apply {
                        setAttribute("class", "table-scroll"); appendChild(element)
                    }
                    val index = units.indexOf(selected.first())
                    units.removeAll(selected.toSet())
                    units.add(index, UnitBlock(wrapper, selected.flatMap { it.texts }, table.bounds, "table"))
                }
            }
            displayEquations(page, charts, tables).forEach { replaceVisual(units, it, "equation", svg) }
            // A missing Unicode mapping can affect a glyph whose outline is perfectly valid.
            // Preserve the complete affected paragraph, avoiding rectangular inline crops that
            // would duplicate neighboring prose around tall operators.
            page.blocks.filter { block -> block.text.contains('\uFFFD') && charts.none { it.containsCenter(block.bounds) } }
                .forEach { block ->
                    val region = tables.firstOrNull { it.bounds.containsCenter(block.bounds) }?.bounds ?: block.bounds
                    replaceVisual(units, region.padded(1f).clippedTo(page.bounds), "equation", svg)
                }
            normalizeScripts(units)
            replaceInline(units, inlineMath(page, charts, tables), svg)
        }
        units.removeAll { it.text.isBlank() && it.element.descendants("img").isEmpty() }

        // The native export includes source font sizes and occasional heading wrappers. Reader
        // typography controls the final sizes; preserve bold/italic/superscript and semantic tables.
        units.forEach { unit ->
            unit.element.descendants().filter { it.localTag in setOf("sup", "sub") && it.descendants("svg").isNotEmpty() }
                .forEach { rename(it, "span") }
            unit.element.descendants().forEach { element ->
                if (element.namespaceURI == SVG) return@forEach
                element.removeAttribute("id")
                if (element.localTag != "img") element.removeAttribute("style")
            }
            if (unit.kind == "paragraph") unit.element = rename(unit.element, "p")
        }
        units.removeAll { unit -> unit.kind == "paragraph" && profile.margin(unit.bounds, page.bounds) &&
            (profile.repeated(unit.text) || Regex("^(?:[0-9]+|[ivxlcdm]+|(?:Page|Halaman)\\s+\\d+(?:\\s+(?:of|/)\\s+\\d+)?)$",
                RegexOption.IGNORE_CASE).matches(unit.text)) }
        units.removeAll { it.kind == "paragraph" && it.text.length > 10 && it.bounds.height > it.bounds.width * 3 }
        normalizeHeadings(units, outline)
        joinDetachedListMarkers(units)
        joinParagraphFragments(units)
        normalizeReferencesAndLists(units)

        // Extract data URIs into EPUB resources, avoiding giant inline attributes in Readium.
        units.forEach { unit -> unit.element.descendants("img").forEach { image ->
            val source = image.getAttribute("src")
            if (source.startsWith("data:")) {
                val media = source.substringAfter("data:").substringBefore(';')
                val extension = when (media) { "image/png" -> "png"; "image/jpeg" -> "jpg"; else -> error("Unsupported MuPDF image: $media") }
                val name = "native-${assets++}.$extension"
                asset(name, media, Base64.getMimeDecoder().decode(source.substringAfter(',')))
                image.setAttribute("src", "../images/$name")
            }
            image.removeAttribute("width"); image.removeAttribute("height")
            if (!image.hasAttribute("alt")) image.setAttribute("alt", "")
        } }

        val sourceBlocks = mutableListOf<SourceBlock>()
        val assigned = mutableSetOf<String>()
        fun annotate(element: Element, bounds: FigureBounds, image: Boolean = false) {
            val old = previous.filter { it.anchor !in assigned }.minByOrNull {
                abs(it.x - bounds.left) + abs(it.y - bounds.top) + abs(it.width - bounds.width) + abs(it.height - bounds.height)
            }?.takeIf { abs(it.x - bounds.left) + abs(it.y - bounds.top) + abs(it.width - bounds.width) + abs(it.height - bounds.height) < 3f }
            var anchor = old?.anchor ?: if (previous.isEmpty()) GeneratedEpubLocations.blockAnchor(number, sourceBlocks.size)
                else "page-$number-native-${sourceBlocks.size}"
            while (anchor in assigned) anchor += "-new"
            assigned += anchor
            element.setAttribute("id", anchor)
            var offset = 0
            val runs = mutableListOf<SourceTextRun>()
            if (image) {
                val id = "$anchor-run-0"
                val visual = element.descendants().firstOrNull { it.localTag in setOf("img", "svg") }
                visual?.setAttribute("id", id)
                val transcript = visual?.let { if (it.localTag == "svg") it.getAttribute("aria-label") else it.getAttribute("alt") }.orEmpty()
                runs += SourceTextRun(id, 0, transcript.length, true)
            } else {
                val nodes = mutableListOf<Node>()
                fun visit(node: Node) {
                    if (node is Element && node.localTag in setOf("img", "svg")) nodes += node
                    else if (node.nodeType == Node.TEXT_NODE && node.nodeValue.isNotEmpty()) nodes += node
                    else (0 until node.childNodes.length).forEach { visit(node.childNodes.item(it)) }
                }
                (0 until element.childNodes.length).forEach { visit(element.childNodes.item(it)) }
                nodes.forEach { node ->
                    val id = "$anchor-run-${runs.size}"
                    if (node is Element) {
                        node.setAttribute("id", id)
                        val length = (if (node.localTag == "svg") node.getAttribute("aria-label") else node.getAttribute("alt")).length
                        runs += SourceTextRun(id, offset, offset + length, true); offset += length
                    } else {
                        val span = document.createElementNS(XHTML, "span").apply { setAttribute("id", id) }
                        node.parentNode.replaceChild(span, node); span.appendChild(node)
                        runs += SourceTextRun(id, offset, offset + node.nodeValue.length)
                        offset += node.nodeValue.length
                    }
                }
            }
            sourceBlocks += SourceBlock(anchor, bounds.left, bounds.top, bounds.width, bounds.height, runs)
        }
        var list: Element? = null
        var listType: String? = null
        units.filter { it.text.isNotBlank() || it.element.descendants("img").isNotEmpty() }.forEach { unit ->
            if (unit.kind == "table") {
                unit.element.descendants().filter { it.localTag in TEXT_TAGS && it.children().none { child -> child.localTag in TEXT_TAGS } }
                    .forEach { element ->
                        val metadata = match(element, unit.bounds)
                        val glyphs = metadata?.let { positioned(UnitBlock(element, listOf(it), it.bounds)) }.orEmpty()
                        val bounds = glyphs.map { it.glyph.bounds }.reduceOrNull(FigureBounds::union) ?: metadata?.bounds ?: unit.bounds
                        annotate(element, bounds)
                    }
            } else annotate(unit.element, unit.bounds, unit.kind in setOf("equation", "figure"))
            val marker = unit.marker
            if (marker != null) {
                val type = if (marker.any(Char::isDigit)) "ol" else "ul"
                if (list == null || listType != type) {
                    listType = type
                    list = document.createElementNS(XHTML, type).apply {
                        setAttribute("class", "reflow-list"); setAttribute("role", "list")
                        setAttribute("style", "--list-marker-width:${max(1, marker.length)}ch")
                        if (type == "ol") marker.filter(Char::isDigit).toIntOrNull()?.let { setAttribute("start", it.toString()) }
                        body.appendChild(this)
                    }
                }
                val item = document.createElementNS(XHTML, "li").apply { setAttribute("class", "list-item") }
                list.appendChild(item)
                item.appendChild(document.createElementNS(XHTML, "span").apply {
                    setAttribute("class", "list-marker"); textContent = marker
                })
                item.appendChild(rename(unit.element, "div").apply { setAttribute("class", "list-content") })
            } else {
                list = null; listType = null; body.appendChild(unit.element)
            }
        }
        return MuPdfCleanPage(body, sourceBlocks, scanned)
    }

    private fun validTable(table: MuPdfTable, page: MuPdfPageRecord): Boolean {
        val element = MuPdfStructure.parse(table.xhtml)
        val rows = element.descendants("tr")
        val cells = element.descendants().filter { it.localTag in setOf("td", "th") }
        val columnCounts = rows.map { row -> row.children().count { it.localTag in setOf("td", "th") } }
        if (rows.size < 2 || columnCounts.maxOrNull()?.let { it < 2 } != false) return false
        if (columnCounts.count { it >= 2 } < 2 && (columnCounts.maxOrNull() ?: 0) < 3) return false
        if (cells.any { Regex("^\\s*\\[\\d+]\\s*$").matches(it.textContent) } ||
            (cells.any { it.textContent.length > 200 } && (columnCounts.maxOrNull() ?: 0) < 3) ||
            cells.count { it.textContent.isBlank() } > cells.size * .55f) return false
        if (page.images.any { table.bounds.containsCenter(it) && it.height > bodySize * 3 }) return false
        val texts = page.blocks.filter { table.bounds.containsCenter(it.bounds) }
        if (texts.any { number -> Regex("^\\d+(?:\\.\\d+)*$").matches(number.text.trim()) && texts.any { title ->
                title.structure.startsWith("H") && abs(title.bounds.top - number.bounds.top) < bodySize * .5f &&
                    title.bounds.left > number.bounds.right && title.bounds.left - number.bounds.right < bodySize * 2
            } }) return false
        return rows.size >= 3 || cells.sumOf { Regex("\\d+(?:\\.\\d+)?").findAll(it.textContent).count() } >= 2
    }

    /** MuPDF sometimes exports an entire unruled column as a super-cell. Split only
     * synchronized native lines inside those cells; columns and spans remain MuPDF's. */
    private fun expandNativeTableRows(element: Element, table: MuPdfTable) {
        element.descendants("tr").zip(table.rows).forEach { (row, nativeCells) ->
            val cells = row.children().filter { it.localTag in setOf("td", "th") }
            if (cells.size != nativeCells.size || cells.size < 2 || cells.any { (it.getAttribute("rowspan").toIntOrNull() ?: 1) > 1 } ||
                nativeCells.count { it.lines.size > 1 } < 2) return@forEach
            val positions = nativeCells.flatMapIndexed { column, cell -> cell.lines.map { column to it.top } }.sortedBy { it.second }
            val bands = mutableListOf<MutableList<Pair<Int, Float>>>()
            positions.forEach { position ->
                val band = bands.lastOrNull()
                if (band != null && position.second - band.first().second < bodySize * .45f) band += position
                else bands += mutableListOf(position)
            }
            val baselines = bands.filter { band -> band.map { it.first }.distinct().size >= 2 }
                .map { band -> band.map { it.second }.average().toFloat() }
            if (baselines.size < 2) return@forEach
            val fragments = cells.zip(nativeCells).map { (cell, native) ->
                val raw = cell.textContent
                val offsets = raw.indices.filter { folded(raw[it].toString()).isNotEmpty() }
                val compact = folded(raw)
                var cursor = 0
                val result = mutableListOf<Pair<Int, Element>>()
                for (line in native.lines) {
                    val text = folded(line.text)
                    if (text.isEmpty()) continue
                    val start = compact.indexOf(text, cursor)
                    if (start < 0 || start != cursor) return@forEach
                    val end = start + text.length
                    val band = baselines.indices.minBy { abs(baselines[it] - line.top) }
                    result += band to cloneTextRange(cell, offsets[start], offsets[end - 1] + 1)
                    cursor = end
                }
                if (cursor != compact.length) return@forEach
                result
            }
            baselines.indices.forEach { band ->
                val replacement = element.ownerDocument.createElementNS(XHTML, "tr")
                cells.forEachIndexed { column, cell ->
                    val content = cell.cloneNode(false) as Element
                    fragments[column].filter { it.first == band }.forEachIndexed { index, (_, fragment) ->
                        if (index > 0) content.appendChild(element.ownerDocument.createTextNode(" "))
                        while (fragment.hasChildNodes()) content.appendChild(fragment.firstChild)
                    }
                    replacement.appendChild(content)
                }
                row.parentNode.insertBefore(replacement, row)
            }
            row.parentNode.removeChild(row)
        }
    }

    private fun cloneTextRange(element: Element, start: Int, end: Int): Element {
        var offset = 0
        fun clone(node: Node): Node? {
            if (node.nodeType == Node.TEXT_NODE) {
                val text = node.nodeValue
                val from = (start - offset).coerceIn(0, text.length)
                val to = (end - offset).coerceIn(0, text.length)
                offset += text.length
                return if (to > from) element.ownerDocument.createTextNode(text.substring(from, to)) else null
            }
            val result = node.cloneNode(false)
            (0 until node.childNodes.length).forEach { index -> clone(node.childNodes.item(index))?.let { result.appendChild(it) } }
            return result.takeIf { it.hasChildNodes() }
        }
        return requireNotNull(clone(element)) as Element
    }

    private fun chartRegions(page: MuPdfPageRecord, tables: List<MuPdfTable>): List<FigureBounds> {
        val area = page.bounds.width * page.bounds.height
        val vectors = page.vectors.filter { it.width * it.height < area * .85f &&
            ((it.height > 8 && it.width > 8) || it.height > 30) }
        val seeds = (page.images + vectors).filter { seed -> tables.none { it.bounds.containsCenter(seed) } }
        fun caption(text: String) = CAPTION.containsMatchIn(text.trim())
        val prose = page.blocks.filter { !caption(it.text) && (longProse(it.text) ||
            (it.text.trim().endsWith('.') && Regex("\\p{L}{4,}").findAll(it.text).count() >= 3)) }.map { it.bounds }
        val eligibleGroups = page.groups.filter { group -> group.kind == "Div" &&
            group.bounds.width * group.bounds.height < area * .82f && prose.none { group.bounds.containsCenter(it) } }
        val groupCrops = mutableMapOf<MuPdfRegion, FigureBounds>()
        val regions = seeds.map { seed ->
            val tagged = page.groups.filter { it.kind.equals("Figure", true) && it.bounds.containsCenter(seed) }
                .minByOrNull { it.bounds.width * it.bounds.height }
            tagged?.bounds ?: eligibleGroups.filter { it.bounds.encloses(seed) }
                .maxByOrNull { it.bounds.width * it.bounds.height }?.let { group -> groupCrops.getOrPut(group) {
                    val drawings = seeds.filter { group.bounds.containsCenter(it) }
                    val parts = drawings + page.blocks.filter { block ->
                        val decoration = profile.margin(block.bounds, page.bounds) &&
                            (profile.repeated(block.text) || Regex("^(?:\\d+|[ivxlcdm]+)$", RegexOption.IGNORE_CASE).matches(block.text.trim())) &&
                            drawings.none { it.near(block.bounds, bodySize * 2) }
                        val sectionHeading = block.structure.startsWith("H") && drawings.none { it.near(block.bounds, bodySize * 2) }
                        group.bounds.containsCenter(block.bounds) && !caption(block.text) && !decoration && !sectionHeading
                    }.map { it.bounds }
                    parts.reduceOrNull(FigureBounds::union) ?: seed
                } } ?: seed
        }.distinct()
        val result = mutableListOf<FigureBounds>()
        regions.forEach { region ->
            var merged = region
            var index = 0
            while (index < result.size) {
                if (merged.near(result[index], 2f)) { merged = merged.union(result.removeAt(index)); index = 0 }
                else index++
            }
            result += merged
        }
        return result.map { it.padded(1f).clippedTo(page.bounds) }
    }

    private fun displayEquations(page: MuPdfPageRecord, charts: List<FigureBounds>, tables: List<MuPdfTable>): List<FigureBounds> {
        val available = page.blocks.filter { text -> charts.none { it.containsCenter(text.bounds) } &&
            tables.none { it.bounds.containsCenter(text.bounds) } && text.text.length < 240 && !longProse(text.text) }
        val seeds = available.filter { block ->
            val glyphs = block.glyphs.filter { it.text.isNotBlank() }
            block.size >= bodySize * .9f && block.text.contains('=') &&
                glyphs.count { it.math || it.italic || it.text.any { c -> c in MATH } } > glyphs.size * .18f
        }
        val regions = mutableListOf<FigureBounds>()
        seeds.forEach { seed ->
            var box = seed.bounds
            repeat(3) {
                available.filter { other -> other.bounds.top <= box.bottom + bodySize * .5f &&
                    other.bounds.bottom >= box.top - bodySize * .5f &&
                    (other.bounds.near(box, bodySize) || Regex("^\\(\\d+\\)$").matches(other.text.trim())) }
                    .forEach { box = box.union(it.bounds) }
            }
            if (regions.none { it.intersects(box) }) regions += box
            else {
                val existing = regions.indexOfFirst { it.intersects(box) }
                regions[existing] = regions[existing].union(box)
            }
        }
        return regions.map { it.padded(1f).clippedTo(page.bounds) }
    }

    private fun inlineMath(page: MuPdfPageRecord, charts: List<FigureBounds>, tables: List<MuPdfTable>): List<FigureBounds> {
        val glyphs = page.blocks.flatMap { it.glyphs }.filter { it.text.isNotBlank() }
        val regions = mutableListOf<FigureBounds>()
        page.vectors.filter { rule -> rule.height <= 2f && rule.width in 3f..bodySize * 5 &&
            charts.none { it.containsCenter(rule) } && tables.none { it.bounds.containsCenter(rule) } }.forEach { rule ->
            val nearby = glyphs.filter { glyph ->
                glyph.bounds.left >= rule.left - bodySize * .9f && glyph.bounds.right <= rule.right + bodySize * .5f &&
                    glyph.bounds.top >= rule.top - bodySize * 1.3f && glyph.bounds.bottom <= rule.bottom + bodySize * 1.8f &&
                    (glyph.math || glyph.size < bodySize * .85f || glyph.text.any { it in MATH })
            }
            if (nearby.any { it.text == "\u221a" } ||
                (nearby.any { it.bounds.bottom < rule.top + 1 } && nearby.any { it.bounds.top > rule.bottom - 1 })) {
                var box = nearby.fold(rule) { current, glyph -> current.union(glyph.bounds) }.padded(.6f).clippedTo(page.bounds)
                val overlaps = regions.filter { it.intersects(box) }
                overlaps.forEach { box = box.union(it) }; regions.removeAll(overlaps.toSet()); regions += box
            }
        }
        return regions
    }

    private fun replaceVisual(units: MutableList<UnitBlock>, box: FigureBounds, kind: String, svg: (FigureBounds, Boolean) -> MuPdfVisual) {
        val selected = units.filter { box.containsCenter(it.bounds) }
        val first = selected.firstOrNull() ?: units.firstOrNull() ?: return
        val transcript = selected.joinToString(" ") { it.text }
        val element = first.element.ownerDocument.createElementNS(XHTML, if (kind == "figure") "figure" else "div")
        visual(element, box, transcript, svg, false, kind == "figure")
        element.setAttribute("class", kind)
        val index = if (selected.isNotEmpty()) units.indexOf(first)
            else units.indexOfFirst { it.bounds.top >= box.bottom }.takeIf { it >= 0 } ?: units.size
        units.removeAll(selected.toSet())
        units.add(index, UnitBlock(element, selected.flatMap { it.texts }, box, kind))
    }

    private fun visual(element: Element, box: FigureBounds, transcript: String,
        svg: (FigureBounds, Boolean) -> MuPdfVisual, inline: Boolean, standalone: Boolean = false): Element {
        val exported = svg(box, standalone)
        if (!standalone) {
            val original = MuPdfStructure.parse(exported.markup)
            val prefix = exported.name.substringBeforeLast('.') + "-"
            original.descendants().forEach { node ->
                if (node.hasAttribute("id")) node.setAttribute("id", prefix + node.getAttribute("id"))
                (0 until node.attributes.length).map { node.attributes.item(it) }.forEach { attribute ->
                    var value = attribute.nodeValue.replace("url(#", "url(#$prefix")
                    if (attribute.localName == "href" && value.startsWith('#')) value = "#$prefix${value.drop(1)}"
                    if (attribute.localName in setOf("fill", "stroke") && value.lowercase() in setOf("#000", "#000000", "black")) value = "currentColor"
                    attribute.nodeValue = value
                }
            }
            val image = element.ownerDocument.importNode(original, true) as Element
            image.setAttribute("class", "math-layout")
            image.setAttribute("role", "img"); image.setAttribute("aria-label", transcript)
            image.setAttribute("fill", "currentColor")
            image.setAttribute("style", if (inline)
                "height:${box.height / bodySize}em;width:auto;vertical-align:-.2em;max-width:100%;overflow:hidden"
                else "width:${box.width / bodySize}em;height:auto;max-width:100%;overflow:hidden")
            image.insertBefore(element.ownerDocument.createElementNS(SVG, "title").apply { textContent = transcript }, image.firstChild)
            element.appendChild(image)
            return element
        }
        val image = element.ownerDocument.createElementNS(XHTML, "img")
        image.setAttribute("src", "../images/${exported.name}")
        image.setAttribute("alt", transcript)
        element.appendChild(image)
        return element
    }

    private fun positioned(unit: UnitBlock): List<PositionedChar> {
        val native = unit.texts.flatMap { it.glyphs }.filter { folded(it.text).isNotEmpty() }
        val nativeOffsets = native.flatMap { glyph -> List(folded(glyph.text).length) { glyph } }
        val output = unit.element.textContent
        val wanted = folded(output)
        val compact = native.joinToString("") { folded(it.text) }
        var nativeIndex = compact.indexOf(wanted).takeIf { it >= 0 } ?: return emptyList()
        val result = mutableListOf<PositionedChar>()
        var offset = 0
        output.codePoints().forEach { cp ->
            val text = String(Character.toChars(cp))
            if (folded(text).isNotEmpty() && nativeIndex < nativeOffsets.size) {
                result += PositionedChar(offset, offset + text.length, nativeOffsets[nativeIndex])
                nativeIndex += folded(text).length
            }
            offset += text.length
        }
        return result
    }

    private fun replaceInline(units: List<UnitBlock>, regions: List<FigureBounds>, svg: (FigureBounds, Boolean) -> MuPdfVisual) {
        val emitted = mutableSetOf<FigureBounds>()
        val mapped = units.filter { it.kind == "paragraph" }.map { it to positioned(it) }
        val transcripts = regions.associateWith { box -> mapped.flatMap { it.second }
            .filter { box.containsCenter(it.glyph.bounds) }.joinToString("") { it.glyph.text } }
        mapped.forEach { (unit, positions) ->
            val replacements = regions.mapNotNull { box ->
                val selected = positions.filter { box.containsCenter(it.glyph.bounds) }
                if (selected.isEmpty()) null else Triple(box, selected.minOf { it.start }, selected.maxOf { it.end })
            }
            replacements.sortedByDescending { it.second }.forEach { (box, start, end) ->
                val replacement = if (!emitted.add(box)) null else unit.element.ownerDocument.createElementNS(XHTML, "span").also {
                    visual(it, box, transcripts.getValue(box), svg, true)
                }
                replaceText(unit.element, start, end, replacement)
            }
        }
    }

    private fun normalizeScripts(units: List<UnitBlock>) {
        units.filter { it.kind == "paragraph" }.forEach { unit ->
            val positions = positioned(unit)
            val bases = unit.texts.flatMap { it.glyphs }.filter { it.size >= bodySize * .9f }
            val scripts = positions.mapNotNull { position ->
                val glyph = position.glyph
                if (glyph.size >= bodySize * .88f) return@mapNotNull null
                val baseline = bases.filter {
                    abs(it.baseline - glyph.baseline) < bodySize * .8f && it.bounds.near(glyph.bounds, bodySize) }
                    .minByOrNull { abs(it.bounds.left - glyph.bounds.left) }?.baseline
                if (baseline != null && glyph.size < bodySize * .88f && glyph.baseline > baseline + bodySize * .12f)
                    position else null
            }
            val ranges = mutableListOf<Pair<Int, Int>>()
            scripts.forEach { position ->
                val last = ranges.lastOrNull()
                if (last?.second == position.start) ranges[ranges.lastIndex] = last.first to position.end
                else ranges += position.start to position.end
            }
            ranges.asReversed().forEach { (start, end) ->
                val replacement = unit.element.ownerDocument.createElementNS(XHTML, "sub").apply {
                    textContent = unit.element.textContent.substring(start, end)
                }
                replaceText(unit.element, start, end, replacement)
            }
        }
    }

    private fun normalizeHeadings(units: MutableList<UnitBlock>, outline: Set<String>) {
        var index = 0
        while (index < units.size) {
            val unit = units[index]
            val next = units.getOrNull(index + 1)
            if (unit.kind == "paragraph" && Regex("^\\d+(?:\\.\\d+)*\\.?$").matches(unit.text) && next?.kind == "paragraph" &&
                next.text.length < 150 && abs(unit.bounds.top - next.bounds.top) < bodySize &&
                (next.texts.any { it.structure.startsWith("H") || it.bold } || next.text in outline)) {
                append(unit, next); units.removeAt(index + 1)
            }
            if (unit.kind == "paragraph") {
                val text = unit.text
                val numbered = Regex("^(\\d+(?:\\.\\d+)*)\\.?\\s+\\p{L}.*$").matchEntire(text)
                val heading = unit.texts.any { it.structure.startsWith("H") } || text in outline ||
                    (text.length < 120 && unit.texts.any { it.size > bodySize * 1.2f || (it.bold && numbered != null) })
                if (heading && text.length < 200 && !text.startsWith("Figure ") && !text.startsWith("Table ") &&
                    (numbered != null || text in outline || (!text.endsWith('.') && text.firstOrNull()?.isLowerCase() != true))) {
                    val level = numbered?.groupValues?.get(1)?.count { it == '.' }?.plus(2)?.coerceAtMost(6) ?: 2
                    unit.kind = "heading"; unit.element = rename(unit.element, "h$level")
                }
            }
            index++
        }
    }

    private fun joinParagraphFragments(units: MutableList<UnitBlock>) {
        var index = 1
        while (index < units.size) {
            val previous = units[index - 1]; val current = units[index]
            val gap = current.bounds.top - previous.bounds.bottom
            val sameSource = previous.texts.any { it in current.texts }
            if (previous.kind == "paragraph" && current.kind == "paragraph" &&
                !isMarker(current.text) && !REFERENCE.containsMatchIn(current.text) &&
                (sameSource || (gap in -bodySize..bodySize * .45f &&
                    min(previous.bounds.right, current.bounds.right) - max(previous.bounds.left, current.bounds.left) >
                        min(previous.bounds.width, current.bounds.width) * .6f &&
                    (abs(previous.bounds.left - current.bounds.left) < bodySize * 1.5f ||
                        (current.bounds.top < previous.bounds.bottom && current.bounds.left > previous.bounds.left &&
                            current.text.length < 8))))) {
                append(previous, current); units.removeAt(index)
            } else index++
        }
    }

    private fun joinDetachedListMarkers(units: MutableList<UnitBlock>) {
        var index = 0
        while (index + 1 < units.size) {
            val marker = units[index]; val content = units[index + 1]
            if (marker.kind == "paragraph" && content.kind == "paragraph" &&
                DETACHED_LIST.matches(marker.text) && abs(marker.bounds.top - content.bounds.top) < bodySize &&
                content.bounds.left > marker.bounds.right && content.bounds.left - marker.bounds.right < bodySize * 3) {
                append(marker, content); units.removeAt(index + 1)
            }
            index++
        }
    }

    private fun normalizeReferencesAndLists(units: MutableList<UnitBlock>) {
        var reference: UnitBlock? = null
        var item: UnitBlock? = null
        var index = 0
        val continuing = bibliography
        while (index < units.size) {
            val unit = units[index]
            if (unit.kind == "heading") {
                bibliography = Regex("^(References|Bibliography|Daftar Pustaka)$", RegexOption.IGNORE_CASE).matches(unit.text)
                reference = null; item = null; index++; continue
            }
            if (unit.kind == "figure") bibliography = false
            if (unit.kind == "paragraph" && CAPTION.containsMatchIn(unit.text)) {
                bibliography = false; reference = null; item = null
                unit.kind = "caption"; unit.element.setAttribute("class", "caption")
                index++; continue
            }
            if (unit.kind != "paragraph") { reference = null; item = null; index++; continue }
            val text = unit.text
            if (REFERENCE.containsMatchIn(text)) {
                bibliography = true; reference = unit; item = null
                unit.kind = "reference"; unit.element.setAttribute("class", "reference")
            } else if (bibliography) {
                if (reference == null) {
                    reference = unit; unit.kind = "reference"
                    unit.element.setAttribute("class", if (continuing && index == 0) "reference-continuation" else "reference")
                } else { append(reference, unit); units.removeAt(index); continue }
            } else {
                val marker = LIST.find(text)
                if (marker != null) {
                    unit.marker = marker.groupValues[1]; unit.kind = "list"
                    replaceText(unit.element, 0, marker.value.length, null)
                    item = unit
                } else if (item != null && unit.bounds.top - item.bounds.bottom < bodySize * .5f &&
                    unit.bounds.left >= item.bounds.left - bodySize) {
                    append(item, unit); units.removeAt(index); continue
                } else item = null
            }
            index++
        }
    }

    private fun append(target: UnitBlock, source: UnitBlock) {
        val first = source.element.textNodes().firstOrNull()
        val last = target.element.textNodes().lastOrNull()
        val hyphen = (last?.nodeValue?.endsWith('-') == true || target.texts.lastOrNull()?.text?.trim()?.endsWith('-') == true) &&
            first?.nodeValue?.firstOrNull()?.isLowerCase() == true
        if (hyphen && last?.nodeValue?.endsWith('-') == true) last.nodeValue = last.nodeValue.dropLast(1)
        else if (hyphen) { /* MuPDF already removed a line-ending hyphen. */ }
        else if (source.text.firstOrNull() in listOf('.', ',', ';', ':', '!', '?', ')', ']')) {
            source.element.textNodes().takeWhile { it.nodeValue.isBlank() }.forEach { it.parentNode.removeChild(it) }
            source.element.textNodes().firstOrNull()?.let { it.nodeValue = it.nodeValue.trimStart() }
        } else target.element.appendChild(target.element.ownerDocument.createTextNode(" "))
        while (source.element.hasChildNodes()) target.element.appendChild(source.element.firstChild)
        target.bounds = target.bounds.union(source.bounds)
        target.texts = (target.texts + source.texts).distinct()
    }

    private fun rename(element: Element, tag: String): Element {
        if (element.localTag == tag) return element
        val replacement = element.ownerDocument.createElementNS(XHTML, tag)
        (0 until element.attributes.length).map { element.attributes.item(it) }.forEach { replacement.setAttribute(it.nodeName, it.nodeValue) }
        while (element.hasChildNodes()) replacement.appendChild(element.firstChild)
        element.parentNode?.replaceChild(replacement, element)
        return replacement
    }

    private fun replaceText(element: Element, start: Int, end: Int, replacement: Node?) {
        var offset = 0
        var inserted = false
        element.textNodes().forEach { node ->
            val text = node.nodeValue
            val from = (start - offset).coerceIn(0, text.length)
            val to = (end - offset).coerceIn(0, text.length)
            offset += text.length
            if (to > from) {
                val parent = node.parentNode
                if (from > 0) parent.insertBefore(element.ownerDocument.createTextNode(text.substring(0, from)), node)
                if (!inserted && replacement != null) { parent.insertBefore(replacement, node); inserted = true }
                if (to < text.length) parent.insertBefore(element.ownerDocument.createTextNode(text.substring(to)), node)
                parent.removeChild(node)
            }
        }
    }

    private fun isMarker(text: String) = LIST.containsMatchIn(text)
    private fun longProse(text: String) = Regex("\\p{L}{4,}").findAll(text).count() >= 12
    private fun folded(text: String) = text.filterNot { it.isWhitespace() || it in "-\u00ad" }
    private fun FigureBounds.containsCenter(other: FigureBounds) = (other.left + other.right) / 2 in left..right &&
        (other.top + other.bottom) / 2 in top..bottom
    private fun FigureBounds.encloses(other: FigureBounds) = left <= other.left + .5f && top <= other.top + .5f &&
        right >= other.right - .5f && bottom >= other.bottom - .5f
    private fun FigureBounds.intersects(other: FigureBounds) = left < other.right && right > other.left && top < other.bottom && bottom > other.top
    private fun FigureBounds.padded(amount: Float) = FigureBounds(left - amount, top - amount, right + amount, bottom + amount)

    companion object {
        private const val XHTML = "http://www.w3.org/1999/xhtml"
        private const val SVG = "http://www.w3.org/2000/svg"
        private val TEXT_TAGS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6")
        private const val MATH = "=\u2248\u2260\u2264\u2265\u2211\u220f\u222b\u221a\u00b1\u00d7\u00f7\u2202\u2207"
        private val REFERENCE = Regex("^\\s*\\[\\d+]\\s*")
        private val CAPTION = Regex("^(?:Figure|Fig\\.|Table|Gambar|Tabel)\\s*\\d", RegexOption.IGNORE_CASE)
        private val LIST = Regex("^\\s*([\u2022\u2023\u25e6\u25aa\u25cf]|\\d+[.)]|\\(\\d+\\)|[a-z][.)])\\s+")
        private val DETACHED_LIST = Regex("^(?:[\u2022\u2023\u25e6\u25aa\u25cf]|\\d+[.)]|\\(\\d+\\)|[a-z][.)])$")
    }
}
