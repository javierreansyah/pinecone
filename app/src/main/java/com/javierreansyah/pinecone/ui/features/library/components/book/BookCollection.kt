package com.javierreansyah.pinecone.ui.features.library.components.book

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding

@Composable
fun BookCollection(
    books: List<Book>,
    layoutMode: LayoutMode,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    selectedBooks: Set<String> = emptySet(),
    isInMultiSelectMode: Boolean = false,
    scrollKey: Any? = null,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = LocalAudioPlayerBottomPadding.current,
) {
    val layoutEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val bookIds = remember(books) { books.map { it.id } }
    val transitionKey = remember(layoutMode, scrollKey, bookIds) {
        Triple(layoutMode, scrollKey, bookIds)
    }

    var displayedKey by remember { mutableStateOf(transitionKey) }
    var displayedLayoutMode by remember { mutableStateOf(layoutMode) }
    var displayedScrollKey by remember { mutableStateOf(scrollKey) }
    var displayedBooks by remember { mutableStateOf(books) }
    val alpha = remember { Animatable(1f) }

    val latestBooks by rememberUpdatedState(books)
    val latestLayoutMode by rememberUpdatedState(layoutMode)
    val latestScrollKey by rememberUpdatedState(scrollKey)

    SideEffect {
        if (transitionKey == displayedKey) {
            displayedBooks = books
        }
    }

    LaunchedEffect(transitionKey) {
        if (transitionKey != displayedKey) {
            if (displayedBooks.isNotEmpty()) {
                alpha.animateTo(0f, animationSpec = layoutEffectsSpec)
            } else {
                alpha.snapTo(0f)
            }
            displayedKey = transitionKey
            displayedLayoutMode = latestLayoutMode
            displayedScrollKey = latestScrollKey
            displayedBooks = latestBooks
        }
        if (alpha.value < 1f) {
            alpha.animateTo(1f, animationSpec = layoutEffectsSpec)
        }
    }

    val activeBooks = if (transitionKey == displayedKey) books else displayedBooks

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        key(displayedScrollKey, displayedLayoutMode) {
            BookGrid(
                books = activeBooks,
                layoutMode = displayedLayoutMode,
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick,
                selectedBooks = selectedBooks,
                isInMultiSelectMode = isInMultiSelectMode,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
            )
        }
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    layoutMode: LayoutMode = LayoutMode.Grid,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null,
    selectedBooks: Set<String> = emptySet(),
    isInMultiSelectMode: Boolean = false,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }

    val isList = layoutMode == LayoutMode.List
    val itemWidth = when {
        screenWidth >= 840.dp -> if (layoutMode == LayoutMode.BigGrid) 200.dp else 140.dp
        screenWidth >= 600.dp -> if (layoutMode == LayoutMode.BigGrid) 180.dp else 120.dp
        else -> if (layoutMode == LayoutMode.BigGrid) 150.dp else 100.dp
    }

    val horizontalPadding = when {
        isList -> if (screenWidth >= 600.dp) 16.dp else 0.dp
        screenWidth >= 840.dp -> 24.dp
        screenWidth >= 600.dp -> 16.dp
        else -> 8.dp
    }

    LazyVerticalGrid(
        columns = if (isList) GridCells.Fixed(if (screenWidth >= 600.dp) 2 else 1)
        else GridCells.Adaptive(minSize = itemWidth),
        contentPadding = PaddingValues(top = topContentPadding, bottom = bottomContentPadding),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding),
    ) {
        items(
            items = books,
            key = { book -> if (isList) book.id else "${book.id}-${layoutMode.name}" },
            contentType = { if (isList) null else layoutMode },
        ) { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = if (selectedBooks.isNotEmpty()) null else {
                    { onBookLongClick?.invoke(book.id) }
                },
                isList = isList,
                isSelected = book.id in selectedBooks,
                isInMultiSelectMode = isInMultiSelectMode,
            )
        }
    }
}
