package com.javierreansyah.pinecone.ui.features.library.components.book

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.Book
import java.io.File

@Composable
fun CoverImage(
    book: Book, modifier: Modifier = Modifier,
    coverOverlay: @Composable BoxScope.() -> Unit = {}
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

        val painter = rememberAsyncImagePainter(
            model = imageRequest,
            contentScale = ContentScale.Crop
        )

        val intrinsicSize = painter.intrinsicSize
        val imageAspectRatio = if (
            intrinsicSize != Size.Unspecified &&
            intrinsicSize.width > 0f &&
            intrinsicSize.height > 0f
        ) {
            intrinsicSize.width / intrinsicSize.height
        } else {
            2f / 3f // fallback to standard book cover ratio while loading
        }

        // Outer container: the 2:3 area from the caller, no rounding
        Box(
            modifier = modifier,
            contentAlignment = Alignment.BottomCenter
        ) {
            // Inner container: sized to the image's actual aspect ratio,
            // as large as possible within the outer container, with rounding
            Box(
                modifier = Modifier
                    .aspectRatio(imageAspectRatio)
                    .clip(MaterialTheme.shapes.small)
            ) {
                Image(
                    painter = painter,
                    contentDescription = stringResource(
                        R.string.book_cover_description,
                        book.title
                    ),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                coverOverlay()
            }
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = book.title.take(1), style = MaterialTheme.typography.displayMedium)
                coverOverlay()
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
        val painter = rememberAsyncImagePainter(
            model = imageRequest,
            contentScale = ContentScale.Fit
        )
        Image(
            painter = painter,
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
