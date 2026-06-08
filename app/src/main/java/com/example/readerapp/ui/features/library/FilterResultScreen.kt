package com.example.readerapp.ui.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Tune
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.More_vert
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.example.readerapp.ui.features.library.components.*
import com.example.readerapp.ui.features.library.components.book.*

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
    val viewModel: LibraryViewModel = viewModel(
        factory = object : ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application) {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return LibraryViewModel(context.applicationContext as android.app.Application, "filter_result") as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val allAuthors by viewModel.allAuthors.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(filterValue) },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onNavigateBack
                    ) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showFilterSheet = true }) {
                        Icon(MaterialSymbols.Outlined.Tune, contentDescription = stringResource(R.string.action_filter))
                    }
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(MaterialSymbols.Outlined.More_vert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (books.isEmpty()) {
                Text(stringResource(R.string.library_empty_books), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            } else {
                BookCollection(
                    books = books,
                    layoutMode = uiState.bookPreferences.layoutMode,
                    onBookClick = onNavigateToReader,
                    onBookLongClick = { selectedBookContext = it to null }
                )
            }
        }

        if (showFilterSheet) {
            FilterResultBottomSheet(
                preferences = uiState.bookPreferences,
                onLayoutModeChange = viewModel::onLayoutModeChange,
                onSortTypeChange = viewModel::onSortTypeChange,
                onStatusToggle = viewModel::toggleStatusFilter,
                onDismiss = { showFilterSheet = false }
            )
        }

        selectedBookContext?.let { context ->
            BookContextMenu(
                viewModel = viewModel,
                bookId = context.first,
                shelfId = context.second,
                onNavigateToBookInfo = onNavigateToBookInfo,
                onDismiss = { selectedBookContext = null }
            )
        }

        if (showDeleteConfirm) {
            val titleType = if (filterType == "author") stringResource(R.string.library_sort_author) else stringResource(R.string.library_sort_label)
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.library_delete_item_title, titleType)) },
                text = { Text(stringResource(R.string.library_delete_item_message, filterValue)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteFilterItem(filterType, filterValue) {
                            onNavigateBack()
                        }
                    }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showRenameDialog) {
            RenameFilterDialog(
                initialName = filterValue,
                suggestions = suggestionList,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    showRenameDialog = false
                    viewModel.renameFilterItem(filterType, filterValue, newName) { confirmedName ->
                        onNavigateToMerged(confirmedName)
                    }
                }
            )
        }
    }
}

