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
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.example.readerapp.data.local.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.components.book.BookItem
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Book
import com.example.readerapp.ui.components.EmptyState

@Composable
fun ShelvesPage(
    modifier: Modifier = Modifier,
    shelves: List<ShelfWithCovers>,
    onShelfClick: (String, String, Int) -> Unit,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String, String) -> Unit)? = null,
    layoutMode: LayoutMode = LayoutMode.BigList,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        if (shelves.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (layoutMode != LayoutMode.List) Modifier.padding(vertical = 4.dp)
                        else Modifier
                    ),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                items(shelves, key = { it.shelf.id }) { shelfWithCovers ->
                    val visibleBooks = shelfWithCovers.books.filter { !it.book.isArchived }
                    val booksCount = visibleBooks.size

                    if (layoutMode == LayoutMode.List) {
                        ShelfListItem(
                            shelfWithCovers = shelfWithCovers,
                            onClick = { onShelfClick(shelfWithCovers.shelf.id, shelfWithCovers.shelf.name, booksCount) }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShelfClick(shelfWithCovers.shelf.id, shelfWithCovers.shelf.name, booksCount) }
                                .padding(vertical = 4.dp)
                        ) {
                            // Header Column
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 2.dp)
                            ) {
                                Text(
                                    text = shelfWithCovers.shelf.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val countText = androidx.compose.ui.res.pluralStringResource(
                                    R.plurals.library_shelf_count, 
                                    booksCount, 
                                    booksCount
                                )
                                Text(
                                    text = countText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Books horizontal scroll
                            if (visibleBooks.isEmpty()) {
                                EmptyState(
                                    icon = MaterialSymbols.Outlined.Book,
                                    text = stringResource(R.string.library_empty_shelf),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .padding(horizontal = 8.dp)
                                )
                            } else {
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val maxW = maxWidth
                                    val fitsOnScreen = (120.dp * visibleBooks.size + 16.dp) <= maxW
                                    
                                    LazyRow(
                                        userScrollEnabled = !fitsOnScreen,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(visibleBooks) { bookEntity ->
                                            val book = Book.fromEntity(bookEntity)
                                            BookItem(
                                                book = book,
                                                onClick = { onBookClick(book.id) },
                                                onLongClick = { onBookLongClick?.invoke(book.id, shelfWithCovers.shelf.id) },
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
        } else {
            EmptyState(
                icon = MaterialSymbols.Outlined.Folder,
                text = stringResource(R.string.library_empty_shelves),
                modifier = Modifier.fillMaxSize().padding(16.dp)
            )
        }
    }
}
