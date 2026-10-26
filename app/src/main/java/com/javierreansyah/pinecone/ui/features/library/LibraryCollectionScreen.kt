package com.javierreansyah.pinecone.ui.features.library

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
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

@Composable
internal fun LibraryCollectionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    viewModel: LibraryCollectionViewModel,
    title: String,
    emptyText: String,
    emptyIcon: ImageVector,
    archived: Boolean,
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val books by viewModel.filteredBooks.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()

    var showFilterSheet by remember { mutableStateOf(value = false) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(value = false) }

    val exitMultiSelect = {
        selectedBooks = emptySet()
        isInMultiSelectMode = false
    }

    BackHandler(enabled = isInMultiSelectMode, onBack = exitMultiSelect)

    CollectionContent(
        title = title,
        emptyText = emptyText,
        emptyIcon = emptyIcon,
        archived = archived,
        books = books,
        allBooks = allBooks,
        preferences = preferences,
        selectedBooks = selectedBooks,
        isInMultiSelectMode = isInMultiSelectMode,
        onNavigateBack = onNavigateBack,
        onEnterMultiSelect = {
            selectedBooks = emptySet()
            isInMultiSelectMode = true
        },
        onExitMultiSelect = exitMultiSelect,
        onInfo = selectedBooks.singleOrNull()?.let { bookId ->
            {
                exitMultiSelect()
                onNavigateToBookInfo(bookId)
            }
        },
        onSelectAll = {
            selectedBooks =
                books.map { book -> book.id }
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
            viewModel.bookActions.markBooksReadStatus(selectedBooks, showMarkAsRead)
            exitMultiSelect()
        },
        onArchiveSelected = {
            viewModel.bookActions.setArchived(selectedBooks, !archived)
            exitMultiSelect()
        },
        onDeleteSelected = {
            viewModel.bookActions.deleteBooks(selectedBooks)
            exitMultiSelect()
        },
        onOrganizeSelected = {
            val ids = selectedBooks
            exitMultiSelect()
            onNavigateToOrganize(ids)
        },
        onOpenFilter = { showFilterSheet = true },
        onBookClick = onNavigateToReader,
        onBookLongClick = { bookId ->
            selectedBooks = setOf(bookId)
            isInMultiSelectMode = true
        },
    )

    if (showFilterSheet) {
        FilterResultBottomSheet(
            preferences = preferences,
            availableFormats = allBooks.mapTo(mutableSetOf()) { it.fileFormat() },
            onLayoutModeChange = viewModel::onLayoutModeChange,
            onSortTypeChange = viewModel::onSortTypeChange,
            onStatusToggle = viewModel::toggleStatusFilter,
            onFileFilterToggle = viewModel::toggleFileFilter,
        ) { showFilterSheet = false }
    }
}

@Composable
private fun CollectionContent(
    title: String,
    emptyText: String,
    emptyIcon: ImageVector,
    archived: Boolean,
    books: List<Book>,
    allBooks: List<Book>,
    preferences: FilterSortPreferences,
    selectedBooks: Set<String>,
    isInMultiSelectMode: Boolean,
    onNavigateBack: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onInfo: (() -> Unit)?,
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
    val isEmpty = books.isEmpty()

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
            CollectionTopBar(
                title = title,
                archived = archived,
                wording = allBooks.filter { it.id in selectedBooks }.publicationWording(),
                isInMultiSelectMode = isInMultiSelectMode,
                isEmpty = isEmpty,
                selectedCount = selectedBooks.size,
                isAllSelected = books.isNotEmpty() && selectedBooks.size == books.size,
                showMarkAsRead = showMarkAsRead,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onEnterMultiSelect = onEnterMultiSelect,
                onExitMultiSelect = onExitMultiSelect,
                onInfo = onInfo,
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
                    icon = emptyIcon,
                    text = emptyText,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            } else {
                BookCollection(
                    books = books,
                    layoutMode = preferences.layoutMode,
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
                        preferences.sortType,
                        preferences.isAscending,
                        preferences.selectedStatus,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CollectionTopBar(
    title: String,
    archived: Boolean,
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
    onInfo: (() -> Unit)?,
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
                onInfo = onInfo,
                isUnarchive = archived,
            )
        },
    ) {
        LibraryTopAppBar(
            title = { Text(title) },
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
