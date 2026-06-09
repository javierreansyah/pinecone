package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.components.book.CoverImage

@Composable
fun ShelfListItem(
    shelfWithCovers: ShelfWithCovers,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleBooks = shelfWithCovers.books.filter { !it.book.isArchived }
    val booksCount = visibleBooks.size
    
    // We only need up to 2 covers for the thumbnail
    val booksForThumbnail = visibleBooks.take(2).map { Book.fromEntity(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thumbnail Box
        // The first book is on top, second is behind shifted right, third is behind shifted further right.
        Box(
            modifier = Modifier
                .height(100.dp)
                .width(76.dp)
        ) {
            // Draw in reverse so the first item has the highest z-index naturally
            booksForThumbnail.forEachIndexed { index, book ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(2f / 3f)
                        .offset(x = (index * 9).dp)
                        .zIndex(3f - index)
                ) {
                    CoverImage(
                        book = book,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Details
        Column(
            modifier = Modifier.height(100.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = shelfWithCovers.shelf.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            val countText = androidx.compose.ui.res.pluralStringResource(
                R.plurals.library_shelf_count, 
                booksCount, 
                booksCount
            )
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
