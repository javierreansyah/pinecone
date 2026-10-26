package com.javierreansyah.pinecone.data.local.database.library

/** Only metadata columns are writable here; reading state can change while a cover is copied. */
data class BookMetadataUpdate(
    val id: String,
    val title: String,
    val description: String?,
    val coverPath: String?,
    val metadataJson: String,
    val language: String?,
    val identifier: String?,
    val publisher: String?,
    val published: String?,
)
