package com.example.readerapp.data.local


import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
@Serializable
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
