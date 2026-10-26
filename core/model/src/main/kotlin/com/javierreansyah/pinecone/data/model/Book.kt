package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.publication.PublicationMetadata

data class Book(
    val id: String,
    val title: String,
    val authors: List<String>,
    val coverPath: String?,
    val progress: Double,
    val lastOpened: Long?,
    val language: String?,
    val addedDate: Long,
    val isArchived: Boolean,
    val description: String? = null,
    val publisher: String? = null,
    val published: String? = null,
    val tags: List<String> = emptyList(),
    val identifier: String? = null,
    val mediaType: String? = null,
    val filePath: String = "",
    val isRead: Boolean = false,
    val furthestProgression: Double = 0.0,
    val lastLocatorJson: String? = null,
    val furthestLocatorJson: String? = null,
    val spaces: List<LibrarySpace> = emptyList(),
    val metadata: PublicationMetadata = PublicationMetadata(),
    val credits: List<PublicationCredit> = emptyList(),
    val activeRendition: String = "original",
    val isUnsorted: Boolean = false,
    val originalFilename: String = "",
) {
    val spaceIds: List<String> get() = spaces.map { (itId, _, _) -> itId }

    companion object

    fun matchesLibraryQuery(query: String): Boolean =
        sequenceOf(title, metadata.subtitle, metadata.sortTitle, metadata.issue, metadata.volume)
            .plus(authors.asSequence()).plus(credits.asSequence().map { it.name })
            .plus(metadata.identifiers.values.asSequence())
            .any { it.contains(query, ignoreCase = true) }
}
