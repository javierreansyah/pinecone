package com.javierreansyah.pinecone.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PublicationCredit(
    val bookId: String,
    val role: String,
    val position: Int,
    val name: String,
    val identifier: String? = null,
    val metadataJson: String = "{}",
)

data class LibrarySpace(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
