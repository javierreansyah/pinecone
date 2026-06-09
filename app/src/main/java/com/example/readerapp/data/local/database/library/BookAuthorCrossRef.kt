package com.example.readerapp.data.local.database.library

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "book_author_cross_ref",
    primaryKeys = ["bookId", "authorId"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    ), ForeignKey(
        entity = AuthorEntity::class,
        parentColumns = ["id"],
        childColumns = ["authorId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("authorId"), Index("bookId")]
)
@Serializable
data class BookAuthorCrossRef(
    val bookId: String, val authorId: Long
)
