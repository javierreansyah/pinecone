package com.javierreansyah.pinecone.reader.pdf

import kotlinx.serialization.Serializable

/** Normalized page coordinates, independent of bitmap resolution, zoom and PDF crop-box origin. */
@Serializable
data class PdfSearchPoint(val x: Float, val y: Float)

@Serializable
data class PdfSearchQuad(
    val topLeft: PdfSearchPoint,
    val topRight: PdfSearchPoint,
    val bottomRight: PdfSearchPoint,
    val bottomLeft: PdfSearchPoint,
)

data class PdfSearchMatch(
    val resultIndex: Int,
    val href: String,
    val pageIndex: Int,
    val quads: List<PdfSearchQuad>,
)

data class PdfSearchHighlights(
    val matches: List<PdfSearchMatch> = emptyList(),
    val focusedIndex: Int? = null,
)

data class PdfSearchHit(val start: Int, val end: Int, val quads: List<PdfSearchQuad>)
data class PdfSearchPage(val text: String, val matches: List<PdfSearchHit>)
data class PdfSearchGlyph(
    val start: Int,
    val end: Int,
    val line: Int,
    val quad: PdfSearchQuad
)

private val searchWhitespace = Regex("\\s+")

/** Preserve one shared match order for snippets, navigation and painted glyphs. */
internal fun pdfSearchHits(
    text: String,
    query: String,
    glyphs: List<PdfSearchGlyph>
): List<PdfSearchHit> {
    val needle = query.trim().replace(searchWhitespace, " ")
    if (needle.isEmpty()) return emptyList()
    return buildList {
        var start = text.indexOf(needle, ignoreCase = true)
        var firstGlyph = 0
        while (start >= 0 && size < 10_000) {
            val end = start + needle.length
            while (firstGlyph < glyphs.size && glyphs[firstGlyph].end <= start) firstGlyph++
            var afterGlyph = firstGlyph
            while (afterGlyph < glyphs.size && glyphs[afterGlyph].start < end) afterGlyph++
            val quads = glyphs.subList(firstGlyph, afterGlyph).groupBy { it.line }.values
                .map { line -> mergePdfSearchQuads(line.map { it.quad }) }
            if (quads.isNotEmpty()) add(PdfSearchHit(start, end, quads))
            start = text.indexOf(needle, end, ignoreCase = true)
        }
    }
}

/** An oriented bound keeps RTL and rotated runs correct without retaining a quad per character. */
internal fun mergePdfSearchQuads(quads: List<PdfSearchQuad>): PdfSearchQuad {
    val first = quads.first()
    val dx = first.topRight.x - first.topLeft.x
    val dy = first.topRight.y - first.topLeft.y
    val length = kotlin.math.hypot(dx, dy)
    val ux = if (length > .000001f) dx / length else 1f
    val uy = if (length > .000001f) dy / length else 0f
    val points = quads.flatMap { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
    val along = points.map { it.x * ux + it.y * uy }
    val across = points.map { -it.x * uy + it.y * ux }
    fun point(u: Float, v: Float) = PdfSearchPoint(u * ux - v * uy, u * uy + v * ux)
    return PdfSearchQuad(
        point(along.min(), across.min()), point(along.max(), across.min()),
        point(along.max(), across.max()), point(along.min(), across.max())
    )
}

data class PdfImageViewport(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** Same centered ContentScale.Fit transform as the PDF Image. */
fun pdfImageViewport(
    width: Float,
    height: Float,
    imageWidth: Int,
    imageHeight: Int
): PdfImageViewport {
    val scale = minOf(width / imageWidth, height / imageHeight)
    val fittedWidth = imageWidth * scale
    val fittedHeight = imageHeight * scale
    return PdfImageViewport(
        (width - fittedWidth) / 2,
        (height - fittedHeight) / 2,
        fittedWidth,
        fittedHeight
    )
}
