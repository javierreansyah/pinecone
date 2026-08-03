package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class BookWithDetails(
    @Embedded val book: BookEntity,

    @Relation(
        parentColumn = "id", entityColumn = "id", associateBy = Junction(
            value = BookAuthorCrossRef::class, parentColumn = "bookId", entityColumn = "authorId"
        )
    ) val authors: List<AuthorEntity>,

    @Relation(
        parentColumn = "id", entityColumn = "bookId"
    ) val authorCrossRefs: List<BookAuthorCrossRef>,

    @Relation(
        parentColumn = "id", entityColumn = "id", associateBy = Junction(
            value = BookTagCrossRef::class, parentColumn = "bookId", entityColumn = "tagId"
        )
    ) val tags: List<TagEntity>,

    @Relation(
        parentColumn = "collectionId", entityColumn = "id"
    ) val collection: CollectionEntity?
) {
    val sortedAuthors: List<AuthorEntity>
        get() {
            val orderMap = authorCrossRefs.associate { it.authorId to it.authorOrder }
            return authors.sortedBy { orderMap[it.id] ?: 0 }
        }
}
