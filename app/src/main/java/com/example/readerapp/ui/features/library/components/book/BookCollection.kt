package com.example.readerapp.ui.features.library.components.book

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.LayoutMode

@Composable
fun BookCollection(
    books: List<Book>,
    layoutMode: LayoutMode,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    selectedBooks: Set<String> = emptySet(),
    scrollKey: Any? = null
) {
    key(scrollKey) {
        AnimatedContent(
            targetState = layoutMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 100, delayMillis = 100)) +
                        scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(durationMillis = 100, delayMillis = 100)
                        ) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 100)) +
                        scaleOut(targetScale = 0.9f, animationSpec = tween(durationMillis = 100))
            },
            label = "bookCollectionLayoutTransition"
        ) { targetLayoutMode ->
            if (targetLayoutMode != LayoutMode.List) {
                BookGrid(
                    books = books,
                    layoutMode = targetLayoutMode,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    selectedBooks = selectedBooks
                )
            } else {
                BookList(
                    books = books,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    selectedBooks = selectedBooks
                )
            }
        }
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    layoutMode: LayoutMode = LayoutMode.Grid,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null,
    selectedBooks: Set<String> = emptySet()
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }

    val itemWidth = when {
        screenWidth >= 840.dp -> if (layoutMode == LayoutMode.BigGrid) 200.dp else 140.dp
        screenWidth >= 600.dp -> if (layoutMode == LayoutMode.BigGrid) 180.dp else 120.dp
        else -> if (layoutMode == LayoutMode.BigGrid) 150.dp else 100.dp
    }
    
    val horizontalPadding = when {
        screenWidth >= 840.dp -> 24.dp
        screenWidth >= 600.dp -> 16.dp
        else -> 8.dp
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = itemWidth),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)
    ) {
        items(
            items = books,
            key = { "${it.id}-${layoutMode.name}" },
            contentType = { layoutMode }
        ) { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookLongClick?.invoke(book.id) },
                isSelected = selectedBooks.contains(book.id)
            )
        }
    }
}

@Composable
private fun BookList(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null,
    selectedBooks: Set<String> = emptySet()
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val columns = if (screenWidth >= 600.dp) 2 else 1
    val horizontalPadding = if (screenWidth >= 600.dp) 16.dp else 0.dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)
    ) {
        items(books, key = { it.id }) { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookLongClick?.invoke(book.id) },
                isList = true,
                isSelected = selectedBooks.contains(book.id)
            )
        }
    }
}
