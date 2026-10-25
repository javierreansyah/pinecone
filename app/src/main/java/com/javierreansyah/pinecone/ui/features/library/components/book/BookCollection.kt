package com.javierreansyah.pinecone.ui.features.library.components.book

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
    bottomContentPadding: Dp = LocalAudioPlayerBottomPadding.current,
) {
    val layoutEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    key(scrollKey) {
        AnimatedContent(
            targetState = layoutMode,
            transitionSpec = {
                fadeIn(animationSpec = layoutEffectsSpec) +
                        scaleIn(
                            initialScale = 0.9f,
                            animationSpec = layoutEffectsSpec,
                        ) togetherWith fadeOut(animationSpec = layoutEffectsSpec) + scaleOut(
                    targetScale = 0.9f,
                    animationSpec = layoutEffectsSpec
                )
            },
            label = "bookCollectionLayoutTransition",
        ) { targetLayoutMode ->
            if (targetLayoutMode != LayoutMode.List) {
                BookGrid(
                    books = books,
                    layoutMode = targetLayoutMode,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    selectedBooks = selectedBooks,
                    isInMultiSelectMode = isInMultiSelectMode,
                    bottomContentPadding = bottomContentPadding,
                )
            } else {
                BookList(
                    books = books,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    selectedBooks = selectedBooks,
                    isInMultiSelectMode = isInMultiSelectMode,
                    bottomContentPadding = bottomContentPadding,
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
    selectedBooks: Set<String> = emptySet(),
    isInMultiSelectMode: Boolean = false,
    bottomContentPadding: Dp = 0.dp,
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
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding),
    ) {
        items(
            items = books,
            key = { book -> "${book.id}-${layoutMode.name}" },
            contentType = { layoutMode },
        ) { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = if (selectedBooks.isNotEmpty()) null else {
                    { onBookLongClick?.invoke(book.id) }
                },
                isSelected = book.id in selectedBooks,
                isInMultiSelectMode = isInMultiSelectMode,
            )
        }
    }
}

@Composable
private fun BookList(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    onBookLongClick: ((String) -> Unit)? = null,
    selectedBooks: Set<String> = emptySet(),
    isInMultiSelectMode: Boolean = false,
    bottomContentPadding: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val columns = if (screenWidth >= 600.dp) 2 else 1
    val horizontalPadding = if (screenWidth >= 600.dp) 16.dp else 0.dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding),
    ) {
        items(
            books,
            key = { book -> book.id }) { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = if (selectedBooks.isNotEmpty()) null else {
                    { onBookLongClick?.invoke(book.id) }
                },
                isList = true,
                isSelected = book.id in selectedBooks,
                isInMultiSelectMode = isInMultiSelectMode,
            )
        }
    }
}
