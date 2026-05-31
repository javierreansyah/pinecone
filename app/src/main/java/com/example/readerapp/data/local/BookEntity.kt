package com.example.readerapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val coverPath: String? = null,
    val filePath: String,
    val mediaType: String? = null,
    val identifier: String? = null,
    val language: String? = null,
    val progression: Double = 0.0,
    val lastLocatorJson: String? = null,
    val addedDate: Long = System.currentTimeMillis(),
    val lastReadDate: Long? = null,
    val isArchived: Boolean = false
)
