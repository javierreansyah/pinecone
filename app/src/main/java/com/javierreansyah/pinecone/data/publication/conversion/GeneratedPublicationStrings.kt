package com.javierreansyah.pinecone.data.publication.conversion

/** UI-authored content is supplied separately from the source document's language and text. */
class GeneratedPublicationStrings(
    val contents: String,
    val scannedPageNotice: String,
    val pageLabel: (Int) -> String,
)
