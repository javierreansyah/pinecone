package com.example.readerapp.ui.features.library.components.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Bookmark_add
import com.composables.icons.materialsymbols.outlined.Bookmark_remove
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Radio_button_unchecked
import com.example.readerapp.R
import com.example.readerapp.data.model.Book

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookContextMenu(
    bookId: String,
    shelfId: String? = null,
    allBooks: List<Book>,
    onNavigateToBookInfo: (String) -> Unit,
    onToggleArchive: () -> Unit,
    onToggleReadStatus: () -> Unit,
    onRemoveFromShelf: () -> Unit,
    onAddToShelf: (String) -> Unit,
    onDeleteBook: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    val book = allBooks.find { it.id == bookId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            if (book != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AutoWidthCoverImage(
                        book = book,
                        modifier = Modifier.heightIn(max = 80.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(
                        horizontal = 16.dp, vertical = 8.dp
                    )
                )
            }

            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.book_info_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                leadingContent = {
                    Icon(
                        MaterialSymbols.Outlined.Info, contentDescription = null
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onNavigateToBookInfo(bookId)
                    onDismiss()
                })

            ListItem(
                headlineContent = {
                    val labelText =
                        if (book?.isRead == true) stringResource(R.string.book_mark_as_unread) else stringResource(
                            R.string.book_mark_as_read
                        )
                    Text(labelText, style = MaterialTheme.typography.titleMedium)
                },
                leadingContent = {
                    Icon(
                        if (book?.isRead == true) MaterialSymbols.Outlined.Radio_button_unchecked else MaterialSymbols.Outlined.Check_circle,
                        contentDescription = null
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onToggleReadStatus()
                    onDismiss()
                })
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.library_label_add_to_shelf),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                leadingContent = {
                    Icon(
                        MaterialSymbols.Outlined.Bookmark_add, contentDescription = null
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onAddToShelf(bookId)
                    onDismiss()
                })
            if (shelfId != null && shelfId != "unshelved") {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.library_label_remove_from_shelf),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    leadingContent = {
                        Icon(
                            MaterialSymbols.Outlined.Bookmark_remove, contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onRemoveFromShelf()
                        onDismiss()
                    })
            }
            ListItem(
                headlineContent = {
                    val labelText =
                        if (book?.isArchived == true) stringResource(R.string.book_unarchive) else stringResource(
                            R.string.book_archive
                        )
                    Text(labelText, style = MaterialTheme.typography.titleMedium)
                },
                leadingContent = {
                    Icon(
                        MaterialSymbols.Outlined.Archive, contentDescription = null
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onToggleArchive()
                    onDismiss()
                })
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    Icon(
                        MaterialSymbols.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    showDeleteConfirmation = true
                })
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(onDismissRequest = { showDeleteConfirmation = false }, title = {
            Text(
                stringResource(R.string.book_delete_title),
                style = MaterialTheme.typography.titleLarge
            )
        }, text = {
            Text(
                stringResource(R.string.book_delete_message),
                style = MaterialTheme.typography.bodyMedium
            )
        }, confirmButton = {
            TextButton(
                onClick = {
                    onDeleteBook()
                    showDeleteConfirmation = false
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(
                    stringResource(R.string.action_delete),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showDeleteConfirmation = false }) {
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        })
    }
}
