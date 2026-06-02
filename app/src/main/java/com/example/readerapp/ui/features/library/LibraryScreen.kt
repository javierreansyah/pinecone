package com.example.readerapp.ui.features.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerapp.data.local.ShelfWithCovers
import com.example.readerapp.ui.features.library.components.*
import com.example.readerapp.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onNavigateToReader: (String) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onNavigateToShelf: (String) -> Unit
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
    val filteredBooks by viewModel.filteredBooks.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    // Context Menu State
    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var showAddToShelfDialog by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }

    val pagerState = rememberPagerState(pageCount = { 2 })

    Scaffold(
        topBar = {
            LibraryTopBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onOpenDrawerClick = onOpenDrawerClick,
                onTuneClick = { showFilterSheet = true }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    // Books Page
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (uiState.layoutMode == LayoutMode.Grid) {
                            BookGrid(
                                books = filteredBooks,
                                onBookClick = onNavigateToReader,
                                onBookLongClick = { selectedBookForMenu = it }
                            )
                        } else {
                            BookList(
                                books = filteredBooks,
                                onBookClick = onNavigateToReader,
                                onBookLongClick = { selectedBookForMenu = it }
                            )
                        }

                        if (uiState.isImporting) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(150.dp),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 8.dp
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium, Alignment.CenterVertically),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Text("Importing...")
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Shelves Page
                    ShelvesPage(
                        shelves = shelves,
                        onShelfClick = onNavigateToShelf,
                        onBookClick = onNavigateToReader
                    )
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

        // Context Menu
        if (selectedBookForMenu != null) {
            ModalBottomSheet(onDismissRequest = { selectedBookForMenu = null }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("Options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Info") },
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(context, com.example.readerapp.ui.features.info.BookInfoActivity::class.java).apply {
                                putExtra(com.example.readerapp.ui.features.info.BookInfoActivity.EXTRA_BOOK_ID, selectedBookForMenu!!)
                            }
                            context.startActivity(intent)
                            selectedBookForMenu = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Archive") },
                        modifier = Modifier.clickable {
                            viewModel.toggleArchive(selectedBookForMenu!!)
                            selectedBookForMenu = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Add to Shelf") },
                        modifier = Modifier.clickable {
                            showAddToShelfDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Create New Shelf") },
                        modifier = Modifier.clickable {
                            showCreateShelfDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            showDeleteConfirmation = true
                        }
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Book") },
                text = { Text("Are you sure you want to delete this book? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBook(selectedBookForMenu!!)
                            showDeleteConfirmation = false
                            selectedBookForMenu = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add to Shelf Dialog
        if (showAddToShelfDialog) {
            AlertDialog(
                onDismissRequest = { showAddToShelfDialog = false },
                title = { Text("Add to Shelf") },
                text = {
                    if (shelves.isEmpty()) {
                        Text("No shelves available.")
                    } else {
                        LazyColumn {
                            items(shelves) { shelfWithCovers ->
                                ListItem(
                                    headlineContent = { Text(shelfWithCovers.shelf.name) },
                                    modifier = Modifier.clickable {
                                        viewModel.addBookToShelf(shelfWithCovers.shelf.id, selectedBookForMenu!!)
                                        showAddToShelfDialog = false
                                        selectedBookForMenu = null
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddToShelfDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Create Shelf Dialog
        if (showCreateShelfDialog) {
            AlertDialog(
                onDismissRequest = { showCreateShelfDialog = false },
                title = { Text("Create New Shelf") },
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
                            viewModel.createShelfAndAddBook(newShelfName, selectedBookForMenu)
                            newShelfName = ""
                            showCreateShelfDialog = false
                            selectedBookForMenu = null
                        }
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateShelfDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
