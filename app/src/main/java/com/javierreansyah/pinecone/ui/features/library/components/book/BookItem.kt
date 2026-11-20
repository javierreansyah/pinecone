package com.javierreansyah.pinecone.ui.features.library.components.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.InsetListItemDefaults

@Composable
fun BookItem(
    modifier: Modifier = Modifier,
    book: Book,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isList: Boolean = false,
    isSelected: Boolean = false,
    isInMultiSelectMode: Boolean = false,
    content: @Composable (() -> Unit)? = null,
) {
    if (isList) {
        ListItem(
            onClick = onClick,
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
                        .aspectRatio(2f / 3f),
                ) {
                    BookCoverWithSelection(
                        book = book,
                        isSelected = isSelected,
                        isInMultiSelectMode = isInMultiSelectMode,
                        isList = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
            } else {
                content
            },
            content = {
                Column(
                    modifier = Modifier.height(100.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (book.authors.isNotEmpty()) book.authors.joinToString(", ") else stringResource(
                                R.string.publication_unknown_author,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (book.isRead) {
                        Text(
                            text = "100% ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = "${(book.furthestProgression * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        )
    } else {
        Column(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(
                            onClick = onClick,
                        )
                    },
                )
                .padding(8.dp),
        ) {
            BookCoverWithSelection(
                book = book,
                isSelected = isSelected,
                isInMultiSelectMode = isInMultiSelectMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (book.authors.isNotEmpty()) book.authors.joinToString(", ") else stringResource(
                        R.string.publication_unknown_author,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (book.isRead) {
                    Text(
                        text = "100%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "${(book.furthestProgression * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun BookCoverWithSelection(
    modifier: Modifier = Modifier,
    book: Book,
    isSelected: Boolean,
    isInMultiSelectMode: Boolean = false,
    isList: Boolean = false,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
        CoverImage(
            book = book,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.matchParentSize()) {
                AnimatedVisibility(
                    visible = isSelected && !isList,
                    enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.small)
                            .background(Color.Black.copy(alpha = 0.4f)),
                    )
                }
            }
        }
        if (!isList && isInMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
