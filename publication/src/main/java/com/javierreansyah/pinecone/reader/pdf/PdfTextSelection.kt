package com.javierreansyah.pinecone.reader.pdf

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.readium.r2.shared.publication.Locator
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val PDF_SELECTION_KEY = "pineconePdfSelection"

fun samePdfResource(first: String, second: String): Boolean =
    first.substringBefore('#').removePrefix("/") == second.substringBefore('#').removePrefix("/")

data class PdfTextPage(val text: String, val glyphs: List<PdfSearchGlyph>)
data class PdfTextRange(val start: Int, val end: Int)

@Serializable
data class PdfSelectionAnchor(
    val pageIndex: Int,
    val start: Int,
    val end: Int,
    val quads: List<PdfSearchQuad>,
)

data class PdfAnnotation(val id: Long, val locator: Locator, val color: Int)

/** App-owned bridge around Readium's PDF navigator, which has no selection/decorations API. */
class PdfSelectionController(
    private val onSelectionChanged: (Locator?) -> Unit = {},
    private val onAnnotationActivated: (Long) -> Unit = {},
) {
    private val _selection = MutableStateFlow<Locator?>(null)
    val selection: StateFlow<Locator?> = _selection.asStateFlow()
    private val _annotations = MutableStateFlow<List<PdfAnnotation>>(emptyList())
    val annotations: StateFlow<List<PdfAnnotation>> = _annotations.asStateFlow()

    fun select(locator: Locator) {
        _selection.value = locator
        onSelectionChanged(locator)
    }

    fun clearSelection() {
        _selection.value = null
        onSelectionChanged(null)
    }

    fun applyAnnotations(annotations: List<PdfAnnotation>) {
        _annotations.value = annotations
    }

    fun activateAnnotation(id: Long) {
        clearSelection()
        onAnnotationActivated(id)
    }
}

fun PdfSearchQuad.contains(point: PdfSearchPoint): Boolean {
    val vertices = listOf(topLeft, topRight, bottomRight, bottomLeft)
    var positive = false
    var negative = false
    var area = 0f
    for (index in vertices.indices) {
        val a = vertices[index]
        val b = vertices[(index + 1) % vertices.size]
        val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
        area += a.x * b.y - b.x * a.y
        positive = positive || cross > 0.0000001f
        negative = negative || cross < -0.0000001f
    }
    return !(positive && negative) && abs(area) > 0.0000001f
}

fun pdfGlyphAt(
    page: PdfTextPage,
    point: PdfSearchPoint,
    nearest: Boolean = false
): PdfSearchGlyph? {
    if (!point.x.isFinite() || !point.y.isFinite()) return null
    page.glyphs.firstOrNull { it.quad.contains(point) }?.let { return it }
    if (!nearest) return null
    return page.glyphs.minByOrNull { glyph ->
        val points = listOf(
            glyph.quad.topLeft,
            glyph.quad.topRight,
            glyph.quad.bottomRight,
            glyph.quad.bottomLeft
        )
        val dx = max(0f, max(points.minOf { it.x } - point.x, point.x - points.maxOf { it.x }))
        val dy = max(0f, max(points.minOf { it.y } - point.y, point.y - points.maxOf { it.y }))
        dx * dx + dy * dy
    }
}

fun pdfWordAt(
    page: PdfTextPage,
    point: PdfSearchPoint,
    nearest: Boolean = false
): PdfTextRange? {
    val glyph = pdfGlyphAt(page, point, nearest) ?: return null
    val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(page.text) }
    val start =
        iterator.preceding(glyph.start + 1).takeIf { it != BreakIterator.DONE } ?: glyph.start
    val end = iterator.following(glyph.start).takeIf { it != BreakIterator.DONE } ?: glyph.end
    return PdfTextRange(min(start, glyph.start), max(end, glyph.end))
}

fun pdfSelectionAnchor(
    pageIndex: Int,
    page: PdfTextPage,
    range: PdfTextRange
): PdfSelectionAnchor? {
    if (pageIndex < 0 || range.start < 0 || range.end > page.text.length || range.start >= range.end) return null
    val glyphs = page.glyphs.filter { it.end > range.start && it.start < range.end }
    if (glyphs.isEmpty()) return null
    return PdfSelectionAnchor(
        pageIndex, range.start, range.end,
        glyphs.groupBy { it.line }.values.map { line -> mergePdfSearchQuads(line.map { it.quad }) })
}

fun pdfSelectionLocator(
    base: Locator,
    page: PdfTextPage,
    anchor: PdfSelectionAnchor
): Locator =
    base.copy(
        locations = base.locations.copy(
            fragments = listOf("page=${anchor.pageIndex + 1}"),
            position = anchor.pageIndex + 1,
            otherLocations = base.locations.otherLocations + (PDF_SELECTION_KEY to Json.encodeToString(
                anchor
            )),
        ),
        text = Locator.Text(
            before = page.text.substring(max(0, anchor.start - 60), anchor.start),
            highlight = page.text.substring(anchor.start, anchor.end),
            after = page.text.substring(anchor.end, min(page.text.length, anchor.end + 100)),
        ),
    )

fun pdfSelectionAnchor(locator: Locator): PdfSelectionAnchor? =
    decodePdfSelectionAnchor(locator.locations.otherLocations[PDF_SELECTION_KEY] as? String)

fun decodePdfSelectionAnchor(encoded: String?): PdfSelectionAnchor? {
    if (encoded == null) return null
    if (encoded.length > 262_144) return null
    return runCatching { Json.decodeFromString<PdfSelectionAnchor>(encoded) }.getOrNull()
        ?.takeIf { anchor ->
            anchor.pageIndex >= 0 && anchor.start >= 0 && anchor.end > anchor.start &&
                    anchor.quads.isNotEmpty() && anchor.quads.size <= 4096 && anchor.quads.all { quad ->
                listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).all {
                    it.x.isFinite() && it.y.isFinite()
                }
            }
        }
}
