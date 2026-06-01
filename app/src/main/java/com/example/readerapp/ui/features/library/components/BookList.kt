package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readerapp.data.model.Book

@Composable
fun BookList(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books) { book ->
            BookItem(
                book = book, 
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookLongClick?.invoke(book.id) },
                isList = true
            )
        }
    }
}
