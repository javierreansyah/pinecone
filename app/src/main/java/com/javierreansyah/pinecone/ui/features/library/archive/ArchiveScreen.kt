package com.javierreansyah.pinecone.ui.features.library.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Select
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.features.library.LibraryCollectionUiState
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.components.FilterResultBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectAppBar
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.book.BookCollection
import com.javierreansyah.pinecone.ui.features.library.components.book.BookContextMenu
import com.javierreansyah.pinecone.ui.features.library.fileFormat
import com.javierreansyah.pinecone.ui.features.library.publicationWording

@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (String) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val archivedBooks by viewModel.filteredBooks.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()

    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(value = false) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(value = false) }

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
            selectedBooks = bookId?.let(::setOf) ?: emptySet()
            isInMultiSelectMode = true
        },
        onExitMultiSelect = exitMultiSelect,
        onSelectAll = {
            selectedBooks =
                archivedBooks.map { book -> book.id }
                    .toSet()
        },
        onClearSelection = { selectedBooks = emptySet() },
        onToggleBookSelection = { bookId ->
            selectedBooks = if (bookId in selectedBooks) {
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
        onBookLongClick = { selectedBookForMenu = it },
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
        ) { selectedBookForMenu = null }
    }

    if (showFilterSheet) {
        FilterResultBottomSheet(
            preferences = uiState.bookPreferences,
            availableFormats = allBooks.mapTo(mutableSetOf()) { it.fileFormat() },
            onLayoutModeChange = viewModel::onLayoutModeChange,
            onSortTypeChange = viewModel::onSortTypeChange,
            onStatusToggle = viewModel::toggleStatusFilter,
            onFileFilterToggle = viewModel::toggleFileFilter,
        ) { showFilterSheet = false }
    }
}

@Composable
fun ArchiveContent(
    archivedBooks: List<Book>,
    allBooks: List<Book>,
    uiState: LibraryCollectionUiState,
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
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isEmpty = archivedBooks.isEmpty()

    val showMarkAsRead = remember(allBooks, selectedBooks) {
        allBooks
            .filter { book -> book.id in selectedBooks }
            .any { book -> !book.isRead }
    }

    Scaffold(
        modifier = if (isEmpty || isInMultiSelectMode) {
            modifier
        } else {
            modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        },
        topBar = {
            ArchiveTopBar(
                wording = allBooks.filter { it.id in selectedBooks }.publicationWording(),
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
                onOpenFilter = onOpenFilter,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (isEmpty) {
                EmptyState(
                    icon = MaterialSymbols.Outlined.Book,
                    text = stringResource(R.string.library_empty_archives),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
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
                        uiState.bookPreferences.selectedStatus,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ArchiveTopBar(
    wording: PublicationWording,
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
    onOpenFilter: () -> Unit,
) {
    MultiSelectTopBarTransition(
        isMultiSelect = isInMultiSelectMode,
        multiSelectBar = {
            MultiSelectAppBar(
                wording = wording,
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
                isUnarchive = true,
            )
        },
    ) {
        LibraryTopAppBar(
            title = { Text(stringResource(R.string.library_archives_title)) },
            onBack = onNavigateBack,
            isEmpty = isEmpty,
            scrollBehavior = scrollBehavior,
            actions = {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onEnterMultiSelect,
                ) {
                    Icon(
                        MaterialSymbols.Outlined.Select,
                        contentDescription = stringResource(R.string.action_select_multiple),
                    )
                }
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onOpenFilter,
                ) {
                    Icon(
                        MaterialSymbols.Outlined.Tune,
                        contentDescription = stringResource(R.string.action_filter),
                    )
                }
            },
        )
    }
}
