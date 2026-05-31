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
    val isArchived: Boolean
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
            isArchived = entity.isArchived
        )
    }
}
