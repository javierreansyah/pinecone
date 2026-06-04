package com.example.readerapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "shelf_book_cross_ref",
    primaryKeys = ["shelfId", "bookId"],
    foreignKeys = [
        ForeignKey(
            entity = ShelfEntity::class,
            parentColumns = ["id"],
            childColumns = ["shelfId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("shelfId"),
        Index("bookId")
    ]
)
data class ShelfBookCrossRefEntity(
    val shelfId: String,
    val bookId: String,
    val addedAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0
)
