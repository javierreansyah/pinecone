package com.example.readerapp.ui.features.library.components.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
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
import com.example.readerapp.R
import com.example.readerapp.data.model.Book

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItem(
    modifier: Modifier = Modifier,
    book: Book,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isList: Boolean = false,
    isSelected: Boolean = false,
    isInMultiSelectMode: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null
) {
    if (isList) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else {
                        Modifier.clickable(
                            onClick = onClick
                        )
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(100.dp)
                    .aspectRatio(2f / 3f)
            ) {
                BookCoverWithSelection(
                    book = book,
                    isSelected = isSelected,
                    isInMultiSelectMode = isInMultiSelectMode,
                    isList = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (book.authors.isNotEmpty()) book.authors.joinToString(", ") else stringResource(
                            R.string.book_unknown_author
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (book.isRead) {
                    Text(
                        text = "100% ✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isInMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
            } else if (trailingContent != null) {
                trailingContent()
            }
        }
    } else {
        Column(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else {
                        Modifier.clickable(
                            onClick = onClick
                        )
                    }
                )
                .padding(8.dp)
        ) {
            BookCoverWithSelection(
                book = book,
                isSelected = isSelected,
                isInMultiSelectMode = isInMultiSelectMode,
                isList = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (book.authors.isNotEmpty()) book.authors.joinToString(", ") else stringResource(
                        R.string.book_unknown_author
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (book.isRead) {
                    Text(
                        text = "100%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BookCoverWithSelection(
    modifier: Modifier = Modifier,
    book: Book,
    isSelected: Boolean,
    isInMultiSelectMode: Boolean = false,
    isList: Boolean = false
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
        CoverImage(
            book = book,
            modifier = Modifier.fillMaxSize(),
            coverOverlay = {
                Box(modifier = Modifier.matchParentSize()) {
                    AnimatedVisibility(
                        visible = isSelected && !isList,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.small)
                                .background(Color.Black.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        )
        if (!isList && isInMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
