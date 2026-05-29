package com.example.readerapp.ui.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.readerapp.data.model.Book
import java.io.File

@Composable
fun CoverImage(
    book: Book,
    modifier: Modifier = Modifier
) {
    if (book.coverPath != null) {
        AsyncImage(
            model = File(book.coverPath),
            contentDescription = "Cover for ${book.title}",
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = book.title.take(1))
            }
        }
    }
}
