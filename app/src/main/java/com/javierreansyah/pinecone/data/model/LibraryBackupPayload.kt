package com.javierreansyah.pinecone.data.model


import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.BookAuthorCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookTagCrossRef
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import kotlinx.serialization.Serializable

@Serializable
data class LibraryBackupPayload(
    val version: Int = 1,
    val books: List<BookEntity>,
    val bookmarks: List<BookmarkEntity>,
    val shelves: List<ShelfEntity>,
    val shelfBookCrossRefs: List<ShelfBookCrossRefEntity>,
    val notes: List<NoteEntity>,
    val authors: List<AuthorEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val bookAuthorCrossRefs: List<BookAuthorCrossRef> = emptyList(),
    val bookTagCrossRefs: List<BookTagCrossRef> = emptyList()
)
