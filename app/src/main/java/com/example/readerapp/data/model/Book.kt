package com.example.readerapp.data.model

import com.example.readerapp.data.local.BookWithDetails

/**
 * UI-layer model for displaying books in the library.
 */
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
    val isRead: Boolean = false
) {
    companion object {
        fun fromEntity(entity: BookWithDetails): Book = Book(
            id = entity.book.id,
            title = entity.book.title,
            authors = entity.authors.map { it.name },
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
            isRead = entity.book.isRead
        )
    }
}
