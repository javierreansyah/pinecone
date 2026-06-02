package com.example.readerapp.data.model

import com.example.readerapp.data.local.BookEntity

/**
 * UI-layer model for displaying books in the library.
 */
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val progress: Double,
    val lastOpened: Long?,
    val language: String?,
    val addedDate: Long,
    val isArchived: Boolean,
    val description: String? = null,
    val publisher: String? = null,
    val published: String? = null,
    val tags: String? = null,
    val identifier: String? = null,
    val mediaType: String? = null,
    val filePath: String = ""
) {
    companion object {
        fun fromEntity(entity: BookEntity): Book = Book(
            id = entity.id,
            title = entity.title,
            author = entity.author,
            coverPath = entity.coverPath,
            progress = entity.progression,
            lastOpened = entity.lastReadDate,
            language = entity.language,
            addedDate = entity.addedDate,
            isArchived = entity.isArchived,
            description = entity.description,
            publisher = entity.publisher,
            published = entity.published,
            tags = entity.tags,
            identifier = entity.identifier,
            mediaType = entity.mediaType,
            filePath = entity.filePath
        )
    }
}
