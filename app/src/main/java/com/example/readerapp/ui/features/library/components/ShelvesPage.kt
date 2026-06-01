package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No shelves yet. Long press a book to create one.")
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(shelves) { shelfWithCovers ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (shelfWithCovers.books.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clickable { onShelfClick(shelfWithCovers.shelf.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Empty Shelf", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(shelfWithCovers.books) { bookEntity ->
                                val book = Book.fromEntity(bookEntity)
                                Box(
                                    modifier = Modifier
                                        .height(140.dp)
                                        .aspectRatio(1f / 1.4f)
                                        .clickable { onBookClick(book.id) }
                                ) {
                                    Card(shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxSize()) {
                                        CoverImage(book = book)
                                    }
                                }
                            }
                        }
                    }
                    
                    Text(
                        text = shelfWithCovers.shelf.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShelfClick(shelfWithCovers.shelf.id) }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
