package com.javierreansyah.pinecone.ui.features.reader.components.contents

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.composables.icons.materialsymbols.outlined.Delete
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.InsetListItemDefaults
import com.javierreansyah.pinecone.ui.features.reader.toLocatorOrNull
import org.readium.r2.shared.publication.Locator

@Composable
fun BookmarksList(
    bookmarks: List<BookmarkEntity>,
    getPositionLabel: (Locator) -> String,
    onBookmarkClick: (Locator) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
) {
    val context = LocalContext.current
    if (bookmarks.isEmpty()) {
        EmptyState(
            icon = MaterialSymbols.Outlined.Bookmark,
            text = stringResource(R.string.reader_no_bookmarks),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(bookmarks) { bookmark ->
                val locator = bookmark.locatorJson.toLocatorOrNull()
                if (locator != null) {
                    val inDocument = stringResource(R.string.reader_in_document)
                    val chapterTitle =
                        bookmark.chapterTitle?.takeIf { it.isNotBlank() && it != inDocument }
                            ?: inDocument

                    val positionLabel = getPositionLabel(locator)
                    val formattedDate = remember(bookmark.createdAt, context) {
                        annotationTimestamp(context, bookmark.createdAt)
                    }
                    val subtitle = if (positionLabel.isNotBlank()) {
                        "$positionLabel | $formattedDate"
                    } else {
                        formattedDate
                    }

                    ListItem(
                        onClick = { onBookmarkClick(locator) },
                        modifier = Modifier.padding(horizontal = InsetListItemDefaults.OuterHorizontalPadding),
                        shapes = InsetListItemDefaults.shapes(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        contentPadding = InsetListItemDefaults.ContentPadding,
                        supportingContent = { Text(text = subtitle) },
                        trailingContent = {
                            IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            }
                        },
                        content = {
                            Text(
                                text = chapterTitle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}
