package com.example.readerapp.ui.features.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.material.icons.filled.Reorder
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.components.BookGrid
import com.example.readerapp.ui.features.library.components.BookList
import com.example.readerapp.ui.features.library.components.FilterSortBottomSheet
import com.example.readerapp.ui.features.library.components.BookItem
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
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
                    return LibraryViewModel(context.applicationContext as android.app.Application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var isReordering by remember { mutableStateOf(false) }

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
            MediumTopAppBar(
                title = { Text(shelfWithCovers?.shelf?.name ?: "Shelf") },
                navigationIcon = {
                    if (isReordering) {
                        IconButton(onClick = { isReordering = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
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
                        IconButton(onClick = { 
                            if (shelfWithCovers != null) {
                                reorderBooks = shelfWithCovers.books.map { Book.fromEntity(it) }
                                isReordering = true 
                            }
                        }) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = "Custom Order")
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(MaterialSymbols.Outlined.Tune, contentDescription = "Filter")
                        }
                    }
                },
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(reorderBooks, { it.id }) { item ->
                            ReorderableItem(reorderState, key = item.id) { isDragging ->
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small,
                                    tonalElevation = elevation,
                                    shadowElevation = elevation
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            BookItem(book = item, onClick = {}, isList = true)
                                        }
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Reorder",
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .draggableHandle(
                                                    onDragStarted = {},
                                                    onDragStopped = {},
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (uiState.layoutMode == LayoutMode.Grid) {
                        BookGrid(
                            books = books,
                            onBookClick = onNavigateToReader
                        )
                    } else {
                        BookList(
                            books = books,
                            onBookClick = onNavigateToReader
                        )
                    }
                }
            }
        }

        if (showFilterSheet) {
            FilterSortBottomSheet(
                uiState = uiState,
                onLayoutModeChange = viewModel::onLayoutModeChange,
                onSortTypeChange = viewModel::onSortTypeChange,
                onStatusToggle = viewModel::toggleStatusFilter,
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}
