package com.javierreansyah.pinecone.ui.features.library.archive

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Select
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.features.library.components.FilterResultBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectAppBar
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.book.BookCollection
import com.javierreansyah.pinecone.ui.features.library.components.book.BookContextMenu

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ArchiveViewModel = viewModel(factory = object :
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application) {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArchiveViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return ArchiveViewModel(context.applicationContext as Application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    })

    val uiState by viewModel.uiState.collectAsState()
    val archivedBooks by viewModel.archivedBooks.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isEmpty = archivedBooks.isEmpty()

    val isMultiSelect = isInMultiSelectMode

    BackHandler(enabled = isMultiSelect) {
        selectedBooks = emptySet()
        isInMultiSelectMode = false
    }

    Scaffold(
        modifier = if (isEmpty || isMultiSelect) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MultiSelectTopBarTransition(
                isMultiSelect = isMultiSelect,
                multiSelectBar = {
                    val showMarkAsRead = allBooks
                        .filter { it.id in selectedBooks }
                        .any { !it.isRead }
                    MultiSelectAppBar(
                        selectedCount = selectedBooks.size,
                        isAllSelected = selectedBooks.size == archivedBooks.size,
                        showMarkAsRead = showMarkAsRead,
                        onCloseMultiSelect = {
                            selectedBooks = emptySet()
                            isInMultiSelectMode = false
                        },
                        onClearSelection = { selectedBooks = emptySet() },
                        onSelectAll = { selectedBooks = archivedBooks.map { it.id }.toSet() },
                        onMarkAsReadUnread = {
                            val booksToProcess = selectedBooks.toList()
                            selectedBooks = emptySet()
                            isInMultiSelectMode = false
                            booksToProcess.forEach { bookId ->
                                val book = allBooks.find { it.id == bookId }
                                if (book != null && book.isRead != showMarkAsRead) {
                                    viewModel.toggleReadStatus(bookId)
                                }
                            }
                        },
                        onAddToShelf = {
                            val ids = selectedBooks.joinToString(",")
                            selectedBooks = emptySet()
                            isInMultiSelectMode = false
                            onNavigateToAddToShelf(ids)
                        },
                        onArchive = {
                            val booksToProcess = selectedBooks.toList()
                            selectedBooks = emptySet()
                            isInMultiSelectMode = false
                            booksToProcess.forEach { bookId ->
                                viewModel.toggleArchive(bookId)
                            }
                        },
                        onDelete = {
                            val booksToProcess = selectedBooks.toList()
                            selectedBooks = emptySet()
                            isInMultiSelectMode = false
                            booksToProcess.forEach { viewModel.deleteBook(it) }
                        },
                        isUnarchive = true
                    )
                },
                defaultBar = {
                    LibraryTopAppBar(
                        title = { Text(stringResource(R.string.library_archives_title)) },
                        onBack = onNavigateBack,
                        isEmpty = isEmpty,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = { isInMultiSelectMode = true }
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Select,
                                    contentDescription = stringResource(R.string.action_select_multiple)
                                )
                            }
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = { showFilterSheet = true }
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Tune,
                                    contentDescription = stringResource(R.string.action_filter)
                                )
                            }
                        }
                    )
                }
            )
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (archivedBooks.isEmpty()) {
                EmptyState(
                    icon = MaterialSymbols.Outlined.Book,
                    text = stringResource(R.string.library_empty_archives),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                BookCollection(
                    books = archivedBooks,
                    layoutMode = uiState.bookPreferences.layoutMode,
                    selectedBooks = selectedBooks,
                    isInMultiSelectMode = isInMultiSelectMode,
                    onBookClick = { bookId ->
                        if (isInMultiSelectMode) {
                            selectedBooks = if (selectedBooks.contains(bookId)) {
                                selectedBooks - bookId
                            } else {
                                selectedBooks + bookId
                            }
                        } else {
                            onNavigateToReader(bookId)
                        }
                    },
                    onBookLongClick = { selectedBookForMenu = it },
                    scrollKey = Triple(
                        uiState.bookPreferences.sortType,
                        uiState.bookPreferences.isAscending,
                        uiState.bookPreferences.selectedStatus
                    )
                )
            }
        }
    }

    selectedBookForMenu?.let { bookId ->
        BookContextMenu(
            bookId = bookId,
            shelfId = null,
            allBooks = allBooks,
            onNavigateToBookInfo = onNavigateToBookInfo,
            onToggleArchive = { viewModel.toggleArchive(bookId) },
            onToggleReadStatus = { viewModel.toggleReadStatus(bookId) },
            onRemoveFromShelf = {},
            onAddToShelf = onNavigateToAddToShelf,
            onDeleteBook = { viewModel.deleteBook(bookId) },
            onEnterMultiSelect = {
                selectedBooks = setOf(bookId)
                isInMultiSelectMode = true
            },
            onDismiss = { selectedBookForMenu = null })
    }

    if (showFilterSheet) {
        FilterResultBottomSheet(
            preferences = uiState.bookPreferences,
            onLayoutModeChange = { viewModel.onLayoutModeChange(it) },
            onSortTypeChange = { viewModel.onSortTypeChange(it) },
            onStatusToggle = { viewModel.toggleStatusFilter(it) },
            onDismiss = { showFilterSheet = false }
        )
    }
}
