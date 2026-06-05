package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.readerapp.data.model.Book

@Composable
fun BookList(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        books.forEach { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookLongClick?.invoke(book.id) },
                isList = true
            )
        }
    }
}
