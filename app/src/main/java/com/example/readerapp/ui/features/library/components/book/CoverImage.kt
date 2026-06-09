package com.example.readerapp.ui.features.library.components.book

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.example.readerapp.R
import com.example.readerapp.data.model.Book
import java.io.File

@Composable
fun CoverImage(
    book: Book, modifier: Modifier = Modifier
) {
    if (book.coverPath != null) {
        Box(
            modifier = modifier, contentAlignment = Alignment.BottomCenter
        ) {
            AsyncImage(
                model = File(book.coverPath),
                contentDescription = stringResource(R.string.book_cover_description, book.title),
                modifier = Modifier.clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = book.title.take(1), style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}
