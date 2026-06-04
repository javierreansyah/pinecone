package com.example.readerapp.data.model

import com.example.readerapp.data.local.BookEntity
import com.example.readerapp.data.local.BookmarkEntity
import com.example.readerapp.data.local.NoteEntity
import com.example.readerapp.data.local.ShelfBookCrossRefEntity
import com.example.readerapp.data.local.ShelfEntity

data class BackupPayload(
    val version: Int = 1,
    val books: List<BookEntity>,
    val bookmarks: List<BookmarkEntity>,
    val shelves: List<ShelfEntity>,
    val shelfBookCrossRefs: List<ShelfBookCrossRefEntity>,
    val notes: List<NoteEntity>
)
