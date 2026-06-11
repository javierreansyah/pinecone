package com.example.readerapp.ui.features.library.components.book

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.LayoutMode

@Composable
fun BookCollection(
    books: List<Book>,
    layoutMode: LayoutMode,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    scrollKey: Any? = null
) {
    key(scrollKey) {
        if (layoutMode != LayoutMode.List) {
            BookGrid(
                books = books,
                layoutMode = layoutMode,
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick,
            )
        } else {
            BookList(
                books = books,
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick,
            )
        }
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    layoutMode: LayoutMode = LayoutMode.Grid,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null,
) {
    val itemWidth = if (layoutMode == LayoutMode.BigGrid) 150.dp else 100.dp
    val horizontalPadding = 8.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = itemWidth),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)
    ) {
        items(books, key = { it.id }) { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookLongClick?.invoke(book.id) },
            )
        }
    }
}

@Composable
private fun BookList(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        items(books, key = { it.id }) { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookLongClick?.invoke(book.id) },
                isList = true,
            )
        }
    }
}
