package com.example.readerapp.ui.features.library.filters

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.R
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.components.FilterResultBottomSheet
import com.example.readerapp.ui.features.library.components.RenameFilterDialog
import com.example.readerapp.ui.features.library.components.book.BookCollection
import com.example.readerapp.ui.features.library.components.book.BookContextMenu

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterResultScreen(
    filterType: String, // "author" or "tag"
    filterValue: String,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToMerged: (String) -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: FilterResultViewModel = viewModel(factory = object :
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application) {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FilterResultViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return FilterResultViewModel(
                    context.applicationContext as Application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    })

    val uiState by viewModel.uiState.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()

    val allAuthors by viewModel.allAuthors.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    val suggestionList = remember(filterType, allAuthors, allTags) {
        if (filterType == "author") allAuthors.map { it.name }
        else allTags.map { it.name }
    }

    val baseBooksFlow = remember(filterType, filterValue) {
        if (filterType == "author") viewModel.getBooksByAuthor(filterValue)
        else viewModel.getBooksByTag(filterValue)
    }
    val books by viewModel.getFilteredAndSortedBooks(baseBooksFlow)
        .collectAsState(initial = emptyList())

    FilterResultContent(
        filterType = filterType,
        filterValue = filterValue,
        uiState = uiState,
        shelves = shelves,
        allBooks = allBooks,
        books = books,
        suggestionList = suggestionList,
        onNavigateBack = onNavigateBack,
        onNavigateToReader = onNavigateToReader,
        onNavigateToMerged = onNavigateToMerged,
        onNavigateToBookInfo = onNavigateToBookInfo,
        onLayoutModeChange = { mode -> viewModel.onLayoutModeChange(mode) },
        onSortTypeChange = { sort -> viewModel.onSortTypeChange(sort) },
        onStatusToggle = { status -> viewModel.toggleStatusFilter(status) },
        onDeleteFilterItem = {
            viewModel.deleteFilterItem(filterType, filterValue) {
                onNavigateBack()
            }
        },
        onRenameFilterItem = { newName ->
            viewModel.renameFilterItem(filterType, filterValue, newName) { confirmedName ->
                onNavigateToMerged(confirmedName)
            }
        },
        onToggleArchive = { bookId -> viewModel.toggleArchive(bookId) },
        onToggleReadStatus = { bookId -> viewModel.toggleReadStatus(bookId) },
        onRemoveFromShelf = { shelfId, bookId -> viewModel.removeBookFromShelf(shelfId, bookId) },
        onAddToShelf = { shelfId, bookId -> viewModel.addBookToShelf(shelfId, bookId) },
        onDeleteBook = { bookId -> viewModel.deleteBook(bookId) },
        onCreateShelfAndAdd = { name, bookId -> viewModel.createShelfAndAddBook(name, bookId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterResultContent(
    filterType: String,
    filterValue: String,
    uiState: FilterResultUiState,
    shelves: List<com.example.readerapp.data.local.database.library.ShelfWithCovers>,
    allBooks: List<Book>,
    books: List<Book>,
    suggestionList: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToMerged: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onLayoutModeChange: (com.example.readerapp.ui.features.library.LayoutMode) -> Unit,
    onSortTypeChange: (com.example.readerapp.ui.features.library.SortType) -> Unit,
    onStatusToggle: (com.example.readerapp.ui.features.library.StatusFilter) -> Unit,
    onDeleteFilterItem: () -> Unit,
    onRenameFilterItem: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onToggleReadStatus: (String) -> Unit,
    onRemoveFromShelf: (String, String) -> Unit,
    onAddToShelf: (String, String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreateShelfAndAdd: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FilterResultTopAppBar(
                filterValue = filterValue,
                showMenu = showMenu,
                onNavigateBack = onNavigateBack,
                onFilterClick = { showFilterSheet = true },
                onMenuToggle = { showMenu = !showMenu },
                onMenuDismiss = { showMenu = false },
                onRenameClick = {
                    showMenu = false
                    showRenameDialog = true
                },
                onDeleteClick = {
                    showMenu = false
                    showDeleteConfirm = true
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        FilterResultBookContent(
            books = books,
            layoutMode = uiState.bookPreferences.layoutMode,
            innerPadding = innerPadding,
            onNavigateToReader = onNavigateToReader,
            onBookLongClick = { selectedBookContext = it to null }
        )

        FilterResultDialogsAndSheets(
            filterType = filterType,
            filterValue = filterValue,
            uiState = uiState,
            shelves = shelves,
            allBooks = allBooks,
            selectedBookContext = selectedBookContext,
            suggestionList = suggestionList,
            showFilterSheet = showFilterSheet,
            showDeleteConfirm = showDeleteConfirm,
            showRenameDialog = showRenameDialog,
            onFilterDismiss = { showFilterSheet = false },
            onLayoutModeChange = onLayoutModeChange,
            onSortTypeChange = onSortTypeChange,
            onStatusToggle = onStatusToggle,
            onDeleteDismiss = { showDeleteConfirm = false },
            onDeleteConfirm = {
                showDeleteConfirm = false
                onDeleteFilterItem()
            },
            onRenameDismiss = { showRenameDialog = false },
            onRenameConfirm = { newName ->
                showRenameDialog = false
                onRenameFilterItem(newName)
            },
            onBookMenuDismiss = { selectedBookContext = null },
            onNavigateToBookInfo = onNavigateToBookInfo,
            onToggleArchive = onToggleArchive,
            onToggleReadStatus = onToggleReadStatus,
            onRemoveFromShelf = onRemoveFromShelf,
            onAddToShelf = onAddToShelf,
            onDeleteBook = onDeleteBook,
            onCreateShelfAndAdd = onCreateShelfAndAdd
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterResultTopAppBar(
    filterValue: String,
    showMenu: Boolean,
    onNavigateBack: () -> Unit,
    onFilterClick: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    LargeFlexibleTopAppBar(
        title = { Text(filterValue) },
        navigationIcon = {
            FilledTonalIconButton(
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                onClick = onNavigateBack
            ) {
                Icon(
                    MaterialSymbols.Outlined.Arrow_back,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
        },
        actions = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = onFilterClick
            ) {
                Icon(
                    MaterialSymbols.Outlined.Tune,
                    contentDescription = stringResource(R.string.action_filter)
                )
            }
            Box {
                IconButton(onClick = onMenuToggle) {
                    Icon(
                        MaterialSymbols.Outlined.More_vert,
                        contentDescription = stringResource(R.string.action_more)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = onMenuDismiss) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = onRenameClick
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = onDeleteClick
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}

@Composable
private fun FilterResultBookContent(
    books: List<Book>,
    layoutMode: com.example.readerapp.ui.features.library.LayoutMode,
    innerPadding: PaddingValues,
    onNavigateToReader: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {
        if (books.isEmpty()) {
            Text(
                stringResource(R.string.library_empty_books),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            BookCollection(
                books = books,
                layoutMode = layoutMode,
                onBookClick = onNavigateToReader,
                onBookLongClick = onBookLongClick
            )
        }
    }
}

@Composable
private fun FilterResultDialogsAndSheets(
    filterType: String,
    filterValue: String,
    uiState: FilterResultUiState,
    shelves: List<com.example.readerapp.data.local.database.library.ShelfWithCovers>,
    allBooks: List<Book>,
    selectedBookContext: Pair<String, String?>?,
    suggestionList: List<String>,
    showFilterSheet: Boolean,
    showDeleteConfirm: Boolean,
    showRenameDialog: Boolean,
    onFilterDismiss: () -> Unit,
    onLayoutModeChange: (com.example.readerapp.ui.features.library.LayoutMode) -> Unit,
    onSortTypeChange: (com.example.readerapp.ui.features.library.SortType) -> Unit,
    onStatusToggle: (com.example.readerapp.ui.features.library.StatusFilter) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onRenameDismiss: () -> Unit,
    onRenameConfirm: (String) -> Unit,
    onBookMenuDismiss: () -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onToggleReadStatus: (String) -> Unit,
    onRemoveFromShelf: (String, String) -> Unit,
    onAddToShelf: (String, String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreateShelfAndAdd: (String, String?) -> Unit
) {
    if (showFilterSheet) {
        FilterResultBottomSheet(
            preferences = uiState.bookPreferences,
            onLayoutModeChange = onLayoutModeChange,
            onSortTypeChange = onSortTypeChange,
            onStatusToggle = onStatusToggle,
            onDismiss = onFilterDismiss
        )
    }

    selectedBookContext?.let { context ->
        val bookId = context.first
        val contextShelfId = context.second
        BookContextMenu(
            bookId = bookId,
            shelfId = contextShelfId,
            shelves = shelves,
            allBooks = allBooks,
            onNavigateToBookInfo = onNavigateToBookInfo,
            onToggleArchive = { onToggleArchive(bookId) },
            onToggleReadStatus = { onToggleReadStatus(bookId) },
            onRemoveFromShelf = {
                contextShelfId?.let {
                    onRemoveFromShelf(it, bookId)
                }
            },
            onAddToShelf = { shelfId -> onAddToShelf(shelfId, bookId) },
            onDeleteBook = { onDeleteBook(bookId) },
            onCreateShelfAndAdd = { name -> onCreateShelfAndAdd(name, bookId) },
            onDismiss = onBookMenuDismiss
        )
    }

    if (showDeleteConfirm) {
        val titleType =
            if (filterType == "author") stringResource(R.string.library_sort_author) else stringResource(
                R.string.library_sort_label
            )
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.library_delete_item_title, titleType)) },
            text = { Text(stringResource(R.string.library_delete_item_message, filterValue)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            })
    }

    if (showRenameDialog) {
        RenameFilterDialog(
            initialName = filterValue,
            suggestions = suggestionList,
            onDismiss = onRenameDismiss,
            onConfirm = onRenameConfirm
        )
    }
}

