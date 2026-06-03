package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readerapp.data.local.ShelfWithCovers
import com.example.readerapp.data.model.Book

@Composable
fun ShelvesPage(
    shelves: List<ShelfWithCovers>,
    onShelfClick: (String) -> Unit,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No shelves yet. Long press a book to create one.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(shelves) { shelfWithCovers ->
                val visibleBooks = shelfWithCovers.books.filter { !it.isArchived }
                val booksCount = visibleBooks.size

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShelfClick(shelfWithCovers.shelf.id) }
                        .padding(vertical = 16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = shelfWithCovers.shelf.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                        val countText = when (booksCount) {
                            1 -> "1 book"
                            else -> "$booksCount books"
                        }
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Books horizontal scroll
                    if (visibleBooks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "Empty Shelf",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(visibleBooks) { bookEntity ->
                                val book = Book.fromEntity(bookEntity)
                                BookItem(
                                    book = book,
                                    onClick = { onBookClick(book.id) },
                                    onLongClick = { onBookLongClick?.invoke(book.id) },
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
