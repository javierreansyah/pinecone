package com.example.readerapp.ui.features.library.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Folder
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.components.EmptyState
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.components.ShelfListItem
import com.example.readerapp.ui.features.library.components.book.BookItem

@Composable
fun ShelvesPage(
    modifier: Modifier = Modifier,
    shelves: List<ShelfWithCovers>,
    onShelfClick: (String, String, Int) -> Unit,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String, String) -> Unit)? = null,
    layoutMode: LayoutMode = LayoutMode.BigList,
    scrollKey: Any? = null
) {
    key(scrollKey) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            if (shelves.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (layoutMode != LayoutMode.List) Modifier.padding(vertical = 4.dp)
                            else Modifier
                        ), contentPadding = PaddingValues(top = 8.dp)
                ) {
                    items(shelves, key = { it.shelf.id }) { shelfWithCovers ->
                        val visibleBooks = shelfWithCovers.books
                        val booksCount = visibleBooks.size

                        if (layoutMode == LayoutMode.List) {
                            ShelfListItem(
                                shelfWithCovers = shelfWithCovers, onClick = {
                                    onShelfClick(
                                        shelfWithCovers.shelf.id,
                                        shelfWithCovers.shelf.name,
                                        booksCount
                                    )
                                })
                        } else {
                            ShelfRowItem(
                                shelfWithCovers = shelfWithCovers,
                                booksCount = booksCount,
                                visibleBooks = visibleBooks,
                                onShelfClick = onShelfClick,
                                onBookClick = onBookClick,
                                onBookLongClick = onBookLongClick
                            )
                        }
                    }
                }
            } else {
                EmptyState(
                    icon = MaterialSymbols.Outlined.Folder,
                    text = stringResource(R.string.library_empty_shelves),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ShelfRowItem(
    shelfWithCovers: ShelfWithCovers,
    booksCount: Int,
    visibleBooks: List<com.example.readerapp.data.local.database.library.BookWithDetails>,
    onShelfClick: (String, String, Int) -> Unit,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String, String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onShelfClick(
                    shelfWithCovers.shelf.id,
                    shelfWithCovers.shelf.name,
                    booksCount
                )
            }
            .padding(vertical = 4.dp)
    ) {
        ShelfRowHeader(
            shelfName = shelfWithCovers.shelf.name,
            booksCount = booksCount
        )
        ShelfBooksHorizontalRow(
            shelfId = shelfWithCovers.shelf.id,
            visibleBooks = visibleBooks,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick
        )
    }
}

@Composable
private fun ShelfRowHeader(
    shelfName: String,
    booksCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 2.dp)
    ) {
        Text(
            text = shelfName,
            style = MaterialTheme.typography.titleMediumEmphasized
        )
        val countText = pluralStringResource(
            R.plurals.library_shelf_count, booksCount, booksCount
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ShelfBooksHorizontalRow(
    shelfId: String,
    visibleBooks: List<com.example.readerapp.data.local.database.library.BookWithDetails>,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String, String) -> Unit)?
) {
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
        val density = LocalDensity.current
        val containerSize = LocalWindowInfo.current.containerSize
        val screenWidth = with(density) { containerSize.width.toDp() }
        val fitsOnScreen = (120.dp * visibleBooks.size + 16.dp) <= screenWidth

        LazyRow(
            userScrollEnabled = !fitsOnScreen,
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(visibleBooks, key = { it.book.id }) { bookEntity ->
                val book = Book.fromEntity(bookEntity)
                BookItem(
                    book = book,
                    onClick = { onBookClick(book.id) },
                    onLongClick = {
                        onBookLongClick?.invoke(book.id, shelfId)
                    },
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}
