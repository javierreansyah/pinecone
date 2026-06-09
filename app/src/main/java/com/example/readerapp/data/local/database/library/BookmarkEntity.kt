package com.example.readerapp.data.local.database.library


import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "bookmarks", foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index("bookId")]
)
@Serializable
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
