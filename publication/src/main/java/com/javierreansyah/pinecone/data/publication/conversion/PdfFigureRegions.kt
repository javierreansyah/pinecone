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
