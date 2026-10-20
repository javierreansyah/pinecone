package com.javierreansyah.pinecone.data.local.preferences

import com.javierreansyah.pinecone.reader.pdf.MuPdfPreferences
import kotlinx.serialization.Serializable

/** Independent profiles: PDF page layout must not overwrite EPUB typography or audio settings. */
@Serializable
data class FormatPreferences(
    val pdf: MuPdfPreferences = MuPdfPreferences(),
    val audioSpeed: Double = 1.0,
    val audioPitch: Double = 1.0,
    val epubSpread: String = "auto",
    val epubLigatures: Boolean? = null,
    val epubVerticalText: Boolean? = null,
) {
    init {
        require(epubSpread in setOf("auto", "never", "always"))
        require(audioSpeed.isFinite() && audioSpeed in 0.25..3.0)
        require(audioPitch.isFinite() && audioPitch in 0.5..2.0)
    }
}
