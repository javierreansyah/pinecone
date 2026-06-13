package com.example.readerapp.ui.features.library.components.book

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.readerapp.R
import com.example.readerapp.data.model.Book
import java.io.File

@Composable
fun CoverImage(
    book: Book, modifier: Modifier = Modifier
) {
    if (book.coverPath != null) {
        val context = LocalContext.current
        val imageRequest = remember(book.coverPath) {
            ImageRequest.Builder(context)
                .data(File(book.coverPath))
                .memoryCacheKey(book.coverPath)
                .crossfade(150)
                .build()
        }
        Box(
            modifier = modifier,
            contentAlignment = Alignment.BottomCenter
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = stringResource(R.string.book_cover_description, book.title),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = book.title.take(1), style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

@Composable
fun AutoWidthCoverImage(
    book: Book,
    modifier: Modifier = Modifier
) {
    if (book.coverPath != null) {
        val context = LocalContext.current
        val imageRequest = remember(book.coverPath) {
            ImageRequest.Builder(context)
                .data(File(book.coverPath))
                .memoryCacheKey(book.coverPath)
                .crossfade(150)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = stringResource(R.string.book_cover_description, book.title),
            modifier = modifier.clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Fit
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = book.title.take(1), style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

