package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.ui.components.InsetListItemDefaults
import com.javierreansyah.pinecone.ui.features.library.components.book.CoverImage
import com.javierreansyah.pinecone.ui.features.library.fileFormat
import com.javierreansyah.pinecone.ui.features.library.publicationCountLabel
import com.javierreansyah.pinecone.ui.features.library.publicationWording

@Composable
fun ShelfListItem(
    modifier: Modifier = Modifier,
    shelfWithCovers: ShelfWithCovers,
    onClick: () -> Unit,
    spaceName: String? = null,
    isInMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onToggleSelect: (() -> Unit)? = null,
) {
    val visibleBooks = shelfWithCovers.books
    val booksCount = visibleBooks.size

    val booksForThumbnail = remember(visibleBooks) {
        visibleBooks.asSequence().take(2).map { Book.fromEntity(it) }.toList()
    }
    val countText = publicationCountLabel(
        booksCount,
        publicationWording(visibleBooks.map { it.book.fileFormat() }),
    )

    ListItem(
        onClick = {
            if (isInMultiSelectMode && onToggleSelect != null) {
                onToggleSelect()
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        modifier = modifier.padding(horizontal = InsetListItemDefaults.OuterHorizontalPadding),
        shapes = InsetListItemDefaults.shapes(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        leadingContent = {
            Box(
                modifier = Modifier
                    .height(100.dp)
                    .width(76.dp),
            ) {
                booksForThumbnail.forEachIndexed { index, book ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(2f / 3f)
                            .offset(x = (index * 9).dp)
                            .zIndex(3f - index),
                    ) {
                        CoverImage(
                            book = book,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = listOfNotNull(spaceName, countText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (isInMultiSelectMode) {
            {
                Box(
                    modifier = Modifier.height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                    )
                }
            }
        } else null,
        content = {
            Text(
                text = shelfWithCovers.shelf.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
