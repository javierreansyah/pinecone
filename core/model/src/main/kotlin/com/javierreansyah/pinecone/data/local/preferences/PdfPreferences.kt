package com.javierreansyah.pinecone.data.local.preferences

import kotlinx.serialization.Serializable

/** Stored PDF controls are independent of the navigator implementation. JSON field names are stable. */
@Serializable
data class PdfPreferences(
    val scroll: Boolean = true,
    val horizontal: Boolean = false,
    val rightToLeft: Boolean = false,
    val fitWidth: Boolean = false,
    val pageSpacing: Double = 12.0,
    val invert: Boolean? = null,
) {
    init { require(pageSpacing.isFinite() && pageSpacing in 0.0..64.0) }
    fun resolveTheme(isDark: Boolean): PdfPreferences = copy(invert = invert ?: isDark)
}
