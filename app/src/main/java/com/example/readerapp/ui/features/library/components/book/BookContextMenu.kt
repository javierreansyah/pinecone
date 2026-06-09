package com.example.readerapp.ui.features.library.components.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.*
import com.example.readerapp.ui.features.library.LibraryViewModel
import com.example.readerapp.ui.features.library.components.ShelfListItem
import com.example.readerapp.ui.components.EmptyState

enum class MenuState {
    Main,
    AddToShelf
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookContextMenu(
    viewModel: LibraryViewModel,
    bookId: String,
    shelfId: String? = null,
    onNavigateToBookInfo: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val shelves by viewModel.shelves.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var menuState by remember { mutableStateOf(MenuState.Main) }
    var newShelfName by remember { mutableStateOf("") }

    if (menuState == MenuState.Main) {
        val book = allBooks.find { it.id == bookId }

        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                if (book != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .height(72.dp)
                                .aspectRatio(2f / 3f)
                                .clip(MaterialTheme.shapes.small)
                        ) {
                            CoverImage(
                                book = book,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (book.authors.isNotEmpty()) book.authors.joinToString(", ") else stringResource(R.string.book_unknown_author),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.book_info_title), style = MaterialTheme.typography.titleMedium) },
                    leadingContent = { Icon(MaterialSymbols.Outlined.Info, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onNavigateToBookInfo(bookId)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { 
                        val labelText = if (book?.isArchived == true) stringResource(R.string.book_unarchive) else stringResource(R.string.book_archive)
                        Text(labelText, style = MaterialTheme.typography.titleMedium) 
                    },
                    leadingContent = { Icon(MaterialSymbols.Outlined.Archive, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.toggleArchive(bookId)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { 
                        val labelText = if (book?.isRead == true) stringResource(R.string.book_mark_as_unread) else stringResource(R.string.book_mark_as_read)
                        Text(labelText, style = MaterialTheme.typography.titleMedium) 
                    },
                    leadingContent = { 
                        Icon(if (book?.isRead == true) MaterialSymbols.Outlined.Radio_button_unchecked else MaterialSymbols.Outlined.Check_circle, contentDescription = null) 
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.toggleReadStatus(bookId)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_label_add_to_shelf), style = MaterialTheme.typography.titleMedium) },
                    leadingContent = { Icon(MaterialSymbols.Outlined.Bookmark_add, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        menuState = MenuState.AddToShelf
                    }
                )
                if (shelfId != null && shelfId != "unshelved") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.library_label_remove_from_shelf), style = MaterialTheme.typography.titleMedium) },
                        leadingContent = { Icon(MaterialSymbols.Outlined.Bookmark_remove, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            viewModel.removeBookFromShelf(shelfId, bookId)
                            onDismiss()
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(MaterialSymbols.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        showDeleteConfirmation = true
                    }
                )
            }
        }
    } else if (menuState == MenuState.AddToShelf) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.library_select_shelf_title)) },
                            navigationIcon = {
                                FilledTonalIconButton(
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    onClick = { menuState = MenuState.Main }
                                ) {
                                    Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showCreateShelfDialog = true }
                        ) {
                            Icon(MaterialSymbols.Outlined.Add, contentDescription = stringResource(R.string.action_create))
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                        val validShelves = shelves.filter { it.shelf.id != "unshelved" }
                        if (validShelves.isEmpty()) {
                            EmptyState(
                                icon = MaterialSymbols.Outlined.Folder,
                                text = stringResource(R.string.library_empty_shelves),
                                modifier = Modifier.fillMaxSize().padding(16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(validShelves) { shelfWithCovers ->
                                    ShelfListItem(
                                        shelfWithCovers = shelfWithCovers,
                                        onClick = {
                                            viewModel.addBookToShelf(shelfWithCovers.shelf.id, bookId)
                                            menuState = MenuState.Main
                                            onDismiss()
                                        }
                                    )
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
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.book_delete_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.book_delete_message), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(bookId)
                        showDeleteConfirmation = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // Create Shelf Dialog
    if (showCreateShelfDialog) {
        AlertDialog(
            onDismissRequest = { showCreateShelfDialog = false },
            title = { Text(stringResource(R.string.library_create_shelf_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = newShelfName,
                    onValueChange = { newShelfName = it },
                    label = { Text(stringResource(R.string.library_shelf_name_label), style = MaterialTheme.typography.labelMedium) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newShelfName.isNotBlank()) {
                        viewModel.createShelfAndAddBook(newShelfName, bookId)
                        newShelfName = ""
                        showCreateShelfDialog = false
                        onDismiss()
                    }
                }) {
                    Text(stringResource(R.string.action_create), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateShelfDialog = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}
