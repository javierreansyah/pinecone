package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.publication.PublicationMetadata

/** Keep persistence mapping at the data boundary, outside shared models. */
fun Book.Companion.fromEntity(entity: BookWithDetails): Book = with(entity.book) {
    Book(
        id = id,
        title = title,
        authors = entity.sortedAuthors.map { it.name },
        coverPath = coverPath,
        progress = progression,
        lastOpened = lastReadDate,
        language = language,
        addedDate = addedDate,
        isArchived = isArchived,
        description = description,
        publisher = publisher,
        published = published,
        tags = entity.tags.map { it.name },
        identifier = identifier,
        mediaType = mediaType,
        filePath = filePath,
        isRead = isRead,
        furthestProgression = furthestProgression,
        lastLocatorJson = lastLocatorJson,
        furthestLocatorJson = furthestLocatorJson,
        spaces = entity.spaces.map { LibrarySpace(it.id, it.name, it.createdAt) },
        metadata = PublicationMetadata.decode(metadataJson),
        credits = entity.credits.sortedWith(compareBy({ it.role }, { it.position }))
            .map { it.toModel() },
        activeRendition = activeRendition,
        isUnsorted = isUnsorted,
        originalFilename = originalFilename,
    )
}
