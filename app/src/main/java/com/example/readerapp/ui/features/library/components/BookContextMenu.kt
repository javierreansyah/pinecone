package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import com.example.readerapp.ui.features.library.LibraryViewModel

enum class MenuState {
    Main,
    AddToShelf
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookContextMenu(
    viewModel: LibraryViewModel,
    bookId: String,
    shelfId: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val shelves by viewModel.shelves.collectAsState()

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var menuState by remember { mutableStateOf(MenuState.Main) }
    var newShelfName by remember { mutableStateOf("") }

    if (menuState == MenuState.Main) {
        val bookEntity = shelves.flatMap { it.books }.find { it.id == bookId }
        val book = bookEntity?.let { com.example.readerapp.data.model.Book.fromEntity(it) }

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
                                text = book.author ?: "Unknown Author",
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
                    headlineContent = { Text("Info", style = MaterialTheme.typography.titleMedium) },
                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        val intent = android.content.Intent(context, com.example.readerapp.ui.features.info.BookInfoActivity::class.java).apply {
                            putExtra(com.example.readerapp.ui.features.info.BookInfoActivity.EXTRA_BOOK_ID, bookId)
                        }
                        context.startActivity(intent)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { 
                        Text(if (book?.isArchived == true) "Unarchive" else "Archive", style = MaterialTheme.typography.titleMedium) 
                    },
                    leadingContent = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.toggleArchive(bookId)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { 
                        Text(if (book?.isRead == true) "Mark as Unread" else "Mark as Read", style = MaterialTheme.typography.titleMedium) 
                    },
                    leadingContent = { 
                        Icon(if (book?.isRead == true) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle, contentDescription = null) 
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.toggleReadStatus(bookId)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text("Add to Shelf", style = MaterialTheme.typography.titleMedium) },
                    leadingContent = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        menuState = MenuState.AddToShelf
                    }
                )
                if (shelfId != null && shelfId != "unshelved") {
                    ListItem(
                        headlineContent = { Text("Remove from Shelf", style = MaterialTheme.typography.titleMedium) },
                        leadingContent = { Icon(Icons.Outlined.BookmarkRemove, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            viewModel.removeBookFromShelf(shelfId, bookId)
                            onDismiss()
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text("Delete", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text("Select Shelf") },
                            navigationIcon = {
                                IconButton(onClick = { menuState = MenuState.Main }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showCreateShelfDialog = true }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create Shelf")
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                        val validShelves = shelves.filter { it.shelf.id != "unshelved" }
                        if (validShelves.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No shelves available.", style = MaterialTheme.typography.bodyLarge)
                            }
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
            title = { Text("Delete Book", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Are you sure you want to delete this book? This action cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(bookId)
                        showDeleteConfirmation = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // Add to Shelf Dialog removed as it's now handled by MenuState.AddToShelf

    // Create Shelf Dialog
    if (showCreateShelfDialog) {
        AlertDialog(
            onDismissRequest = { showCreateShelfDialog = false },
            title = { Text("Create New Shelf", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = newShelfName,
                    onValueChange = { newShelfName = it },
                    label = { Text("Shelf Name", style = MaterialTheme.typography.labelMedium) },
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
                    Text("Create", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateShelfDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}
