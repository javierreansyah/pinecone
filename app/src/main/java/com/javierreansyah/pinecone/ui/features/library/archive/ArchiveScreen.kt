package com.javierreansyah.pinecone.ui.features.library.archive

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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Select
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.features.library.components.FilterResultBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectAppBar
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.book.BookCollection
import com.javierreansyah.pinecone.ui.features.library.components.book.BookContextMenu

@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (String) -> Unit,
    viewModel: ArchiveViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val archivedBooks by viewModel.archivedBooks.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()

    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(false) }

    val exitMultiSelect = {
        selectedBooks = emptySet()
        isInMultiSelectMode = false
    }

    BackHandler(enabled = isInMultiSelectMode, onBack = exitMultiSelect)

    ArchiveContent(
        archivedBooks = archivedBooks,
        allBooks = allBooks,
        uiState = uiState,
        selectedBooks = selectedBooks,
        isInMultiSelectMode = isInMultiSelectMode,
        onNavigateBack = onNavigateBack,
        onEnterMultiSelect = { bookId ->
            selectedBooks = if (bookId != null) setOf(bookId) else emptySet()
            isInMultiSelectMode = true
        },
        onExitMultiSelect = exitMultiSelect,
        onSelectAll = { selectedBooks = archivedBooks.map { it.id }.toSet() },
        onClearSelection = { selectedBooks = emptySet() },
        onToggleBookSelection = { bookId ->
            selectedBooks = if (selectedBooks.contains(bookId)) {
                selectedBooks - bookId
            } else {
                selectedBooks + bookId
            }
        },
        onMarkAsReadUnread = { showMarkAsRead ->
            viewModel.markBooksReadStatus(selectedBooks, showMarkAsRead)
            exitMultiSelect()
        },
        onArchiveSelected = {
            viewModel.toggleArchiveBooks(selectedBooks)
            exitMultiSelect()
        },
        onDeleteSelected = {
            viewModel.deleteBooks(selectedBooks)
            exitMultiSelect()
        },
        onOrganizeSelected = {
            val ids = selectedBooks.joinToString(",")
            exitMultiSelect()
            onNavigateToOrganize(ids)
        },
        onOpenFilter = { showFilterSheet = true },
        onBookClick = onNavigateToReader,
        onBookLongClick = { selectedBookForMenu = it }
    )

    selectedBookForMenu?.let { bookId ->
        BookContextMenu(
            bookId = bookId,
            allBooks = allBooks,
            onNavigateToBookInfo = onNavigateToBookInfo,
            onToggleArchive = { viewModel.toggleArchive(bookId) },
            onToggleReadStatus = { viewModel.toggleReadStatus(bookId) },
            onOrganize = onNavigateToOrganize,
            onDeleteBook = { viewModel.deleteBook(bookId) },
            onEnterMultiSelect = {
                selectedBooks = setOf(bookId)
                isInMultiSelectMode = true
            },
            onDismiss = { selectedBookForMenu = null }
        )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveContent(
    archivedBooks: List<Book>,
    allBooks: List<Book>,
    uiState: ArchiveUiState,
    selectedBooks: Set<String>,
    isInMultiSelectMode: Boolean,
    onNavigateBack: () -> Unit,
    onEnterMultiSelect: (String?) -> Unit,
    onExitMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleBookSelection: (String) -> Unit,
    onMarkAsReadUnread: (Boolean) -> Unit,
    onArchiveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOrganizeSelected: () -> Unit,
    onOpenFilter: () -> Unit,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isEmpty = archivedBooks.isEmpty()

    val showMarkAsRead = remember(allBooks, selectedBooks) {
        allBooks
            .filter { it.id in selectedBooks }
            .any { !it.isRead }
    }

    Scaffold(
        modifier = if (isEmpty || isInMultiSelectMode) {
            modifier
        } else {
            modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        },
        topBar = {
            ArchiveTopBar(
                isInMultiSelectMode = isInMultiSelectMode,
                isEmpty = isEmpty,
                selectedCount = selectedBooks.size,
                isAllSelected = archivedBooks.isNotEmpty() && selectedBooks.size == archivedBooks.size,
                showMarkAsRead = showMarkAsRead,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onEnterMultiSelect = { onEnterMultiSelect(null) },
                onExitMultiSelect = onExitMultiSelect,
                onSelectAll = onSelectAll,
                onClearSelection = onClearSelection,
                onMarkAsReadUnread = { onMarkAsReadUnread(showMarkAsRead) },
                onArchive = onArchiveSelected,
                onDelete = onDeleteSelected,
                onOrganize = onOrganizeSelected,
                onOpenFilter = onOpenFilter
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (isEmpty) {
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
                            onToggleBookSelection(bookId)
                        } else {
                            onBookClick(bookId)
                        }
                    },
                    onBookLongClick = onBookLongClick,
                    scrollKey = Triple(
                        uiState.bookPreferences.sortType,
                        uiState.bookPreferences.isAscending,
                        uiState.bookPreferences.selectedStatus
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArchiveTopBar(
    isInMultiSelectMode: Boolean,
    isEmpty: Boolean,
    selectedCount: Int,
    isAllSelected: Boolean,
    showMarkAsRead: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMarkAsReadUnread: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onOrganize: () -> Unit,
    onOpenFilter: () -> Unit
) {
    MultiSelectTopBarTransition(
        isMultiSelect = isInMultiSelectMode,
        multiSelectBar = {
            MultiSelectAppBar(
                selectedCount = selectedCount,
                isAllSelected = isAllSelected,
                showMarkAsRead = showMarkAsRead,
                onCloseMultiSelect = onExitMultiSelect,
                onClearSelection = onClearSelection,
                onSelectAll = onSelectAll,
                onMarkAsReadUnread = onMarkAsReadUnread,
                onOrganize = onOrganize,
                onArchive = onArchive,
                onDelete = onDelete,
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
                        onClick = onEnterMultiSelect
                    ) {
                        Icon(
                            MaterialSymbols.Outlined.Select,
                            contentDescription = stringResource(R.string.action_select_multiple)
                        )
                    }
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = onOpenFilter
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
}
