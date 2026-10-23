package com.javierreansyah.pinecone.data.publication.conversion

import kotlin.math.max
import kotlin.math.min

/** Coordinates in PDF points; kept independent of the native engine for geometry tests. */
@kotlinx.serialization.Serializable
data class FigureBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun union(other: FigureBounds): FigureBounds = FigureBounds(
        min(left, other.left),
        min(top, other.top),
        max(right, other.right),
        max(bottom, other.bottom)
    )

    fun clippedTo(page: FigureBounds): FigureBounds = FigureBounds(
        max(left, page.left), max(top, page.top), min(right, page.right), min(bottom, page.bottom)
    )

    fun near(other: FigureBounds, gap: Float): Boolean = left <= other.right + gap &&
            right + gap >= other.left && top <= other.bottom + gap && bottom + gap >= other.top
}

/** Group neighboring drawing paths into figures, excluding page backgrounds and divider rules. */
internal fun pdfFigureRegions(page: FigureBounds, paths: List<FigureBounds>): List<FigureBounds> {
    fun isPageSized(box: FigureBounds) = box.width * box.height >= page.width * page.height * .85f
    val regions = mutableListOf<FigureBounds>()
    paths.forEach { path ->
        val box = path.clippedTo(page)
        if (!listOf(box.left, box.top, box.right, box.bottom).all { it.isFinite() } ||
            box.width < 0 || box.height < 0 || isPageSized(box) ||
            (box.width >= page.width * .8f && box.height <= 2f)
        ) return@forEach
        var merged = box
        // Restart after a union: the larger region can now touch a previously separate part.
        var index = 0
        while (index < regions.size) {
            if (merged.near(regions[index], 6f)) {
                merged = merged.union(regions.removeAt(index))
                index = 0
            } else index++
        }
        regions += merged
    }
    return regions.filter { it.width >= 10f && it.height >= 10f && !isPageSized(it) }
        .map { FigureBounds(it.left - 3, it.top - 3, it.right + 3, it.bottom + 3).clippedTo(page) }
        .sortedWith(compareBy({ it.top }, { it.left }))
}
