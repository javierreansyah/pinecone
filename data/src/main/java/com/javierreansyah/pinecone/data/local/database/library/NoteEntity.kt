package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = PublicationRenditionEntity::class, parentColumns = ["bookId", "key"],
            childColumns = ["bookId", "renditionKey"], onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index(value = ["bookId", "renditionKey"])],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val noteText: String,
    val color: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val renditionKey: String = "original",
)
