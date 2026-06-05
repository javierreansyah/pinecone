package com.example.readerapp.ui.features.library.components

import androidx.compose.runtime.Composable
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.LayoutMode

@Composable
fun BookCollection(
    books: List<Book>,
    layoutMode: LayoutMode,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit
) {
    if (layoutMode != LayoutMode.List) {
        BookGrid(
            books = books,
            layoutMode = layoutMode,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick
        )
    } else {
        BookList(
            books = books,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick
        )
    }
}
