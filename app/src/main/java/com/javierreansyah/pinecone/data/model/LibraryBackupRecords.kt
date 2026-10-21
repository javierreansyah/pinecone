package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.BookAuthorCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookSpaceCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookTagCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
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
    val jumpOriginLocatorJson: String? = null,
    val activeRendition: String = "original",
    val metadataJson: String = "{}",
    val sourceMetadataJson: String = "{}",
    val originalFilename: String = "",
    val isUnsorted: Boolean = false,
)

fun BookEntity.toBackupRecord(): BookBackupRecord = BookBackupRecord(
    id, title, coverPath, filePath, mediaType, identifier, language, progression,
    lastLocatorJson, addedDate, lastReadDate, isArchived, description, publisher,
    published, isRead, furthestProgression, furthestLocatorJson, jumpOriginLocatorJson,
    activeRendition, metadataJson, sourceMetadataJson, originalFilename, isUnsorted,
)

fun BookBackupRecord.toEntity(): BookEntity = BookEntity(
    id, title, coverPath, filePath, mediaType, identifier, language, progression,
    lastLocatorJson, addedDate, lastReadDate, isArchived, description, publisher,
    published, isRead, furthestProgression, furthestLocatorJson, jumpOriginLocatorJson,
    activeRendition, metadataJson, sourceMetadataJson, originalFilename, isUnsorted,
)

@Serializable
data class BookmarkBackupRecord(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val createdAt: Long,
    val renditionKey: String = "original",
)

fun BookmarkEntity.toBackupRecord(): BookmarkBackupRecord =
    BookmarkBackupRecord(id, bookId, locatorJson, chapterTitle, createdAt, renditionKey)

fun BookmarkBackupRecord.toEntity(): BookmarkEntity =
    BookmarkEntity(id, bookId, locatorJson, chapterTitle, createdAt, renditionKey)

@Serializable
data class NoteBackupRecord(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val noteText: String,
    val color: Int = -1,
    val createdAt: Long,
    val renditionKey: String = "original",
)

fun NoteEntity.toBackupRecord(): NoteBackupRecord =
    NoteBackupRecord(
        id,
        bookId,
        locatorJson,
        chapterTitle,
        noteText,
        color,
        createdAt,
        renditionKey
    )

fun NoteBackupRecord.toEntity(): NoteEntity =
    NoteEntity(id, bookId, locatorJson, chapterTitle, noteText, color, createdAt, renditionKey)

@Serializable
data class ShelfBackupRecord(
    val id: String,
    val spaceId: String,
    val name: String,
    val createdAt: Long,
)

fun ShelfEntity.toBackupRecord(): ShelfBackupRecord =
    ShelfBackupRecord(id, spaceId, name, createdAt)

fun ShelfBackupRecord.toEntity(): ShelfEntity = ShelfEntity(id, spaceId, name, createdAt)

@Serializable
data class ShelfBookBackupRecord(
    val shelfId: String,
    val bookId: String,
    val addedAt: Long,
    val orderIndex: Int = 0,
)

fun ShelfBookCrossRefEntity.toBackupRecord(): ShelfBookBackupRecord =
    ShelfBookBackupRecord(shelfId, bookId, addedAt, orderIndex)

fun ShelfBookBackupRecord.toEntity(): ShelfBookCrossRefEntity =
    ShelfBookCrossRefEntity(shelfId, bookId, addedAt, orderIndex)

@Serializable
data class AuthorBackupRecord(val id: Long = 0, val name: String)

fun AuthorEntity.toBackupRecord(): AuthorBackupRecord = AuthorBackupRecord(id, name)
fun AuthorBackupRecord.toEntity(): AuthorEntity = AuthorEntity(id, name)

@Serializable
data class TagBackupRecord(val id: Long = 0, val name: String)

fun TagEntity.toBackupRecord(): TagBackupRecord = TagBackupRecord(id, name)
fun TagBackupRecord.toEntity(): TagEntity = TagEntity(id, name)

@Serializable
data class BookAuthorBackupRecord(
    val bookId: String,
    val authorId: Long,
    val authorOrder: Int = 0,
)

fun BookAuthorCrossRef.toBackupRecord(): BookAuthorBackupRecord =
    BookAuthorBackupRecord(bookId, authorId, authorOrder)

fun BookAuthorBackupRecord.toEntity(): BookAuthorCrossRef =
    BookAuthorCrossRef(bookId, authorId, authorOrder)

@Serializable
data class BookTagBackupRecord(val bookId: String, val tagId: Long)

fun BookTagCrossRef.toBackupRecord(): BookTagBackupRecord = BookTagBackupRecord(bookId, tagId)
fun BookTagBackupRecord.toEntity(): BookTagCrossRef = BookTagCrossRef(bookId, tagId)

@Serializable
data class SpaceBackupRecord(val id: String, val name: String, val createdAt: Long)

fun SpaceEntity.toBackupRecord(): SpaceBackupRecord = SpaceBackupRecord(id, name, createdAt)
fun SpaceBackupRecord.toEntity(): SpaceEntity = SpaceEntity(id, name, createdAt)

@Serializable
data class BookSpaceBackupRecord(val bookId: String, val spaceId: String)

fun BookSpaceCrossRef.toBackupRecord(): BookSpaceBackupRecord =
    BookSpaceBackupRecord(bookId, spaceId)

fun BookSpaceBackupRecord.toEntity(): BookSpaceCrossRef = BookSpaceCrossRef(bookId, spaceId)
