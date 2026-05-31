package com.example.readerapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val noteText: String,
    val color: Int = -1, // -1 means default
    val createdAt: Long = System.currentTimeMillis()
)
