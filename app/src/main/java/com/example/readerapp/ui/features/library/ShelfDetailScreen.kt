package com.example.readerapp.ui.features.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Tune
import androidx.compose.material.icons.filled.FormatListNumbered
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.components.FilterSortBottomSheet
import com.example.readerapp.ui.features.library.components.BookItem
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShelfDetailScreen(
    shelfId: String,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = object : ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application) {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return LibraryViewModel(context.applicationContext as android.app.Application, "shelf_detail") as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var isReordering by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }
    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }

    val shelves by viewModel.shelves.collectAsState()
    val shelfWithCovers = shelves.find { it.shelf.id == shelfId }

    // Use a flow to get filtered and sorted books
    val baseBooksFlow = remember(shelfWithCovers) {
        flowOf(shelfWithCovers?.books?.map { Book.fromEntity(it) } ?: emptyList())
    }
    
    val books by viewModel.getFilteredAndSortedBooks(baseBooksFlow)
        .collectAsState(initial = emptyList())

    // Local mutable state for reordering
    var reorderBooks by remember { mutableStateOf(emptyList<Book>()) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(shelfWithCovers?.shelf?.name ?: "Shelf") },
                subtitle = { Text("${books.size} book${if (books.size != 1) "s" else ""}") },
                navigationIcon = {
                    if (isReordering) {
                        FilledTonalIconButton(
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            onClick = { isReordering = false }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    } else {
                        FilledTonalIconButton(
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            onClick = onNavigateBack
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isReordering) {
                        TextButton(onClick = {
                            viewModel.updateShelfOrder(shelfId, reorderBooks.map { it.id })
                            isReordering = false
                        }) {
                            Text("Save")
                        }
                    } else {
                        if (shelfId != "unshelved") {
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { 
                                if (shelfWithCovers != null) {
                                    reorderBooks = shelfWithCovers.books.map { Book.fromEntity(it) }
                                    isReordering = true 
                                }
                            }) {
                                Icon(Icons.Default.FormatListNumbered, contentDescription = "Custom Order")
                            }
                        }
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showFilterSheet = true }) {
                            Icon(MaterialSymbols.Outlined.Tune, contentDescription = "Filter")
                        }
                        if (shelfId != "unshelved") {
                            Box {
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            newShelfName = shelfWithCovers?.shelf?.name ?: ""
                                            showRenameDialog = true
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showDeleteDialog = true
                                            showMoreMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (shelfWithCovers == null) {
                Text("Shelf not found.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            } else if (books.isEmpty() && !isReordering) {
                Text("No books found.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            } else if (reorderBooks.isEmpty() && isReordering) {
                Text("No books to reorder.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            } else {
                if (isReordering) {
                    val lazyListState = rememberLazyListState()
                    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        reorderBooks = reorderBooks.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(reorderBooks, { it.id }) { item ->
                            ReorderableItem(reorderState, key = item.id) { isDragging ->
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = if (isDragging) MaterialTheme.shapes.small else androidx.compose.ui.graphics.RectangleShape,
                                    tonalElevation = elevation,
                                    shadowElevation = elevation
                                ) {
                                    BookItem(
                                        book = item,
                                        onClick = {},
                                        onLongClick = { selectedBookForMenu = item.id },
                                        isList = true,
                                        trailingContent = {
                                            Box(modifier = Modifier.height(100.dp), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.DragHandle,
                                                    contentDescription = "Reorder",
                                                    modifier = Modifier
                                                        .draggableHandle(
                                                            onDragStarted = {},
                                                            onDragStopped = {},
                                                        )
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    com.example.readerapp.ui.features.library.components.BookCollection(
                        books = books,
                        layoutMode = uiState.bookPreferences.layoutMode,
                        onBookClick = onNavigateToReader,
                        onBookLongClick = { selectedBookForMenu = it }
                    )
                }
            }
        }

        if (showFilterSheet) {
            FilterSortBottomSheet(
                preferences = uiState.bookPreferences,
                onLayoutModeChange = viewModel::onLayoutModeChange,
                onSortTypeChange = viewModel::onSortTypeChange,
                onStatusToggle = viewModel::toggleStatusFilter,
                onDismiss = { showFilterSheet = false },
                showViewPicker = true,
                showStatusFilter = true,
                availableSortTypes = if (shelfId == "unshelved") {
                    SortType.entries.filter { it != SortType.Custom }
                } else {
                    SortType.entries // Allows Custom Order
                }
            )
        }
        if (showDeleteDialog && shelfWithCovers != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Shelf") },
                text = { Text("Are you sure you want to delete '${shelfWithCovers.shelf.name}'? The books inside will not be deleted.") },
                confirmButton = {
                    TextButton(onClick = { 
                        viewModel.deleteShelf(shelfId)
                        showDeleteDialog = false 
                        onNavigateBack()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRenameDialog && shelfWithCovers != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Shelf") },
                text = {
                    OutlinedTextField(
                        value = newShelfName,
                        onValueChange = { newShelfName = it },
                        label = { Text("Shelf Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { 
                        if (newShelfName.isNotBlank()) {
                            viewModel.renameShelf(shelfId, newShelfName)
                            showRenameDialog = false 
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (selectedBookForMenu != null) {
            com.example.readerapp.ui.features.library.components.BookContextMenu(
                viewModel = viewModel,
                bookId = selectedBookForMenu!!,
                shelfId = shelfId,
                onDismiss = { selectedBookForMenu = null }
            )
        }
    }
}
