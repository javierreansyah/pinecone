package com.javierreansyah.pinecone.ui.features.reader

import com.javierreansyah.pinecone.reader.pdf.PdfSearchMatch
import org.readium.r2.shared.publication.Locator

data class SearchResultItem(

    val locator: Locator,

    val chapterTitle: String?,

    val positionLabel: String,

    val textBefore: String?,

    val highlight: String?,

    val textAfter: String?,
    val pdfMatch: PdfSearchMatch? = null,
)
