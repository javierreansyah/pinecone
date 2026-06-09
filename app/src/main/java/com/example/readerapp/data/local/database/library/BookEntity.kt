package com.example.readerapp.data.local.database.library


import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "books")
@Serializable
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coverPath: String? = null,
    val filePath: String,
    val mediaType: String? = null,
    val identifier: String? = null,
    val language: String? = null,
    val progression: Double = 0.0,
    val lastLocatorJson: String? = null,
    val addedDate: Long = System.currentTimeMillis(),
    val lastReadDate: Long? = null,
    val isArchived: Boolean = false,
    val description: String? = null,
    val publisher: String? = null,
    val published: String? = null,
    val isRead: Boolean = false
)
