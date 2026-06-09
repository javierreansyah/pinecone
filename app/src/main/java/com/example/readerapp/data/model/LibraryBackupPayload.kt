package com.example.readerapp.data.model


import kotlinx.serialization.Serializable
import com.example.readerapp.data.local.database.library.AuthorEntity
import com.example.readerapp.data.local.database.library.BookAuthorCrossRef
import com.example.readerapp.data.local.database.library.BookEntity
import com.example.readerapp.data.local.database.library.BookTagCrossRef
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.data.local.database.library.ShelfBookCrossRefEntity
import com.example.readerapp.data.local.database.library.ShelfEntity
import com.example.readerapp.data.local.database.library.TagEntity

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
