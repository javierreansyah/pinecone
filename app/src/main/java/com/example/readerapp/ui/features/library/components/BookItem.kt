package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import com.example.readerapp.ui.theme.spacing
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.example.readerapp.data.model.Book

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItem(
    modifier: Modifier = Modifier.fillMaxWidth(),
    book: Book,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isList: Boolean = false
) {
    if (isList) {
        Row(
            modifier = modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space16)
        ) {
            Box(modifier = Modifier
                .height(100.dp)
                .aspectRatio(2f / 3f)) {
                CoverImage(
                    book = book,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.height(100.dp),
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
                        text = book.author ?: "Unknown Author",
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
        }
    } else {
        Column(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(8.dp)
        ) {
            CoverImage(
                book = book,
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
                    text = book.author ?: "Unknown Author",
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
