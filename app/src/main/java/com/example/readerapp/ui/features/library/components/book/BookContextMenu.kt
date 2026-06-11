package com.example.readerapp.ui.features.library.components.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
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
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Bookmark_add
import com.composables.icons.materialsymbols.outlined.Bookmark_remove
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Radio_button_unchecked
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.components.EmptyState
import com.example.readerapp.ui.features.library.components.ShelfListItem

enum class MenuState {
    Main, AddToShelf
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookContextMenu(
    bookId: String,
    shelfId: String? = null,
    shelves: List<ShelfWithCovers>,
    allBooks: List<Book>,
    onNavigateToBookInfo: (String) -> Unit,
    onToggleArchive: () -> Unit,
    onToggleReadStatus: () -> Unit,
    onRemoveFromShelf: () -> Unit,
    onAddToShelf: (String) -> Unit,
    onDeleteBook: () -> Unit,
    onCreateShelfAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var menuState by remember { mutableStateOf(MenuState.Main) }
    var newShelfName by remember { mutableStateOf("") }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    if (menuState == MenuState.Main) {
        val book = allBooks.find { it.id == bookId }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
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
                        menuState = MenuState.AddToShelf
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
    } else if (menuState == MenuState.AddToShelf) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false, decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
            ) {
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.library_select_shelf_title)) },
                        navigationIcon = {
                            FilledTonalIconButton(
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                onClick = { menuState = MenuState.Main }) {
                                Icon(
                                    MaterialSymbols.Outlined.Arrow_back,
                                    contentDescription = stringResource(R.string.action_back)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }, floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showCreateShelfDialog = true }) {
                        Icon(
                            MaterialSymbols.Outlined.Add,
                            contentDescription = stringResource(R.string.action_create)
                        )
                    }
                }) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .padding(paddingValues)
                            .fillMaxSize()
                    ) {
                        val validShelves = shelves.filter { it.shelf.id != "unshelved" }
                        if (validShelves.isEmpty()) {
                            EmptyState(
                                icon = MaterialSymbols.Outlined.Folder,
                                text = stringResource(R.string.library_empty_shelves),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(validShelves) { shelfWithCovers ->
                                    ShelfListItem(
                                        shelfWithCovers = shelfWithCovers, onClick = {
                                            onAddToShelf(shelfWithCovers.shelf.id)
                                            menuState = MenuState.Main
                                            onDismiss()
                                        })
                                }
                            }
                        }
                    }
                }
            }
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

    // Create Shelf Dialog
    if (showCreateShelfDialog) {
        AlertDialog(onDismissRequest = { showCreateShelfDialog = false }, title = {
            Text(
                stringResource(R.string.library_create_shelf_title),
                style = MaterialTheme.typography.titleLarge
            )
        }, text = {
            OutlinedTextField(
                value = newShelfName, onValueChange = { newShelfName = it }, label = {
                    Text(
                        stringResource(R.string.library_shelf_name_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }, confirmButton = {
            TextButton(onClick = {
                if (newShelfName.isNotBlank()) {
                    onCreateShelfAndAdd(newShelfName)
                    newShelfName = ""
                    showCreateShelfDialog = false
                    onDismiss()
                }
            }) {
                Text(
                    stringResource(R.string.action_create),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showCreateShelfDialog = false }) {
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        })
    }
}
