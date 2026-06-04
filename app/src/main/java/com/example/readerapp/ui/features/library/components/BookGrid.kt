package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readerapp.data.model.Book

import com.example.readerapp.ui.features.library.LayoutMode

@Composable
fun BookGrid(
    books: List<Book>,
    layoutMode: LayoutMode = LayoutMode.Grid,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (layoutMode == LayoutMode.BigGrid) 150.dp else 100.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books) { book ->
            BookItem(
                book = book, 
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookLongClick?.invoke(book.id) }
            )
        }
    }
}
