package com.example.readerapp.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class BookWithDetails(
    @Embedded val book: BookEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookAuthorCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "authorId"
        )
    )
    val authors: List<AuthorEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookTagCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)
