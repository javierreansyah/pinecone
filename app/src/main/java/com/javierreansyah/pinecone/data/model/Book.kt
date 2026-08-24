package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity

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
    val spaces: List<SpaceEntity> = emptyList()
) {
    val spaceIds: List<String> get() = spaces.map { it.id }

    companion object {
        fun fromEntity(entity: BookWithDetails): Book = Book(
            id = entity.book.id,
            title = entity.book.title,
            authors = entity.sortedAuthors.map { it.name },
            coverPath = entity.book.coverPath,
            progress = entity.book.progression,
            lastOpened = entity.book.lastReadDate,
            language = entity.book.language,
            addedDate = entity.book.addedDate,
            isArchived = entity.book.isArchived,
            description = entity.book.description,
            publisher = entity.book.publisher,
            published = entity.book.published,
            tags = entity.tags.map { it.name },
            identifier = entity.book.identifier,
            mediaType = entity.book.mediaType,
            filePath = entity.book.filePath,
            isRead = entity.book.isRead,
            furthestProgression = entity.book.furthestProgression,
            lastLocatorJson = entity.book.lastLocatorJson,
            furthestLocatorJson = entity.book.furthestLocatorJson,
            spaces = entity.spaces
        )
    }
}
