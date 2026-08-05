package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "book_tag_cross_ref", primaryKeys = ["bookId", "tagId"], foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    ), ForeignKey(
        entity = TagEntity::class,
        parentColumns = ["id"],
        childColumns = ["tagId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index("tagId"), Index("bookId")]
)
data class BookTagCrossRef(
    val bookId: String, val tagId: Long
)
