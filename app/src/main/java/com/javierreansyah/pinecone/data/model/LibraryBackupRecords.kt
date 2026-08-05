package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.database.library.*
import kotlinx.serialization.Serializable

@Serializable
data class BookBackupRecord(
    val id: String,
    val title: String,
    val coverPath: String? = null,
    val filePath: String,
    val mediaType: String? = null,
    val identifier: String? = null,
    val language: String? = null,
    val progression: Double = 0.0,
    val lastLocatorJson: String? = null,
    val addedDate: Long,
    val lastReadDate: Long? = null,
    val isArchived: Boolean = false,
    val description: String? = null,
    val publisher: String? = null,
    val published: String? = null,
    val isRead: Boolean = false,
    val furthestProgression: Double = 0.0,
    val furthestLocatorJson: String? = null,
    val jumpOriginLocatorJson: String? = null
)

fun BookEntity.toBackupRecord() = BookBackupRecord(
    id, title, coverPath, filePath, mediaType, identifier, language, progression,
    lastLocatorJson, addedDate, lastReadDate, isArchived, description, publisher,
    published, isRead, furthestProgression, furthestLocatorJson, jumpOriginLocatorJson
)

fun BookBackupRecord.toEntity() = BookEntity(
    id, title, coverPath, filePath, mediaType, identifier, language, progression,
    lastLocatorJson, addedDate, lastReadDate, isArchived, description, publisher,
    published, isRead, furthestProgression, furthestLocatorJson, jumpOriginLocatorJson
)

@Serializable
data class BookmarkBackupRecord(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val createdAt: Long
)

fun BookmarkEntity.toBackupRecord() =
    BookmarkBackupRecord(id, bookId, locatorJson, chapterTitle, createdAt)
fun BookmarkBackupRecord.toEntity() =
    BookmarkEntity(id, bookId, locatorJson, chapterTitle, createdAt)

@Serializable
data class NoteBackupRecord(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val noteText: String,
    val color: Int = -1,
    val createdAt: Long
)

fun NoteEntity.toBackupRecord() =
    NoteBackupRecord(id, bookId, locatorJson, chapterTitle, noteText, color, createdAt)
fun NoteBackupRecord.toEntity() =
    NoteEntity(id, bookId, locatorJson, chapterTitle, noteText, color, createdAt)

@Serializable
data class ShelfBackupRecord(val id: String, val name: String, val createdAt: Long)
fun ShelfEntity.toBackupRecord() = ShelfBackupRecord(id, name, createdAt)
fun ShelfBackupRecord.toEntity() = ShelfEntity(id, name, createdAt)

@Serializable
data class ShelfBookBackupRecord(
    val shelfId: String,
    val bookId: String,
    val addedAt: Long,
    val orderIndex: Int = 0
)
fun ShelfBookCrossRefEntity.toBackupRecord() =
    ShelfBookBackupRecord(shelfId, bookId, addedAt, orderIndex)
fun ShelfBookBackupRecord.toEntity() =
    ShelfBookCrossRefEntity(shelfId, bookId, addedAt, orderIndex)

@Serializable
data class AuthorBackupRecord(val id: Long = 0, val name: String)
fun AuthorEntity.toBackupRecord() = AuthorBackupRecord(id, name)
fun AuthorBackupRecord.toEntity() = AuthorEntity(id, name)

@Serializable
data class TagBackupRecord(val id: Long = 0, val name: String)
fun TagEntity.toBackupRecord() = TagBackupRecord(id, name)
fun TagBackupRecord.toEntity() = TagEntity(id, name)

@Serializable
data class BookAuthorBackupRecord(
    val bookId: String,
    val authorId: Long,
    val authorOrder: Int = 0
)
fun BookAuthorCrossRef.toBackupRecord() = BookAuthorBackupRecord(bookId, authorId, authorOrder)
fun BookAuthorBackupRecord.toEntity() = BookAuthorCrossRef(bookId, authorId, authorOrder)

@Serializable
data class BookTagBackupRecord(val bookId: String, val tagId: Long)
fun BookTagCrossRef.toBackupRecord() = BookTagBackupRecord(bookId, tagId)
fun BookTagBackupRecord.toEntity() = BookTagCrossRef(bookId, tagId)

@Serializable
data class SpaceBackupRecord(val id: String, val name: String, val createdAt: Long)
fun SpaceEntity.toBackupRecord() = SpaceBackupRecord(id, name, createdAt)
fun SpaceBackupRecord.toEntity() = SpaceEntity(id, name, createdAt)

@Serializable
data class BookSpaceBackupRecord(val bookId: String, val spaceId: String)
fun BookSpaceCrossRef.toBackupRecord() = BookSpaceBackupRecord(bookId, spaceId)
fun BookSpaceBackupRecord.toEntity() = BookSpaceCrossRef(bookId, spaceId)
