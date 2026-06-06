package com.example.readerapp.data.model


import kotlinx.serialization.Serializable
import com.example.readerapp.data.local.AuthorEntity
import com.example.readerapp.data.local.BookAuthorCrossRef
import com.example.readerapp.data.local.BookEntity
import com.example.readerapp.data.local.BookTagCrossRef
import com.example.readerapp.data.local.BookmarkEntity
import com.example.readerapp.data.local.NoteEntity
import com.example.readerapp.data.local.ShelfBookCrossRefEntity
import com.example.readerapp.data.local.ShelfEntity
import com.example.readerapp.data.local.TagEntity

@Serializable
data class BackupPayload(
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
