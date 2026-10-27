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
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.features.library.components.FilterResultBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.SelectedBooksBar
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
    val preferences by viewModel.filters.preferences.collectAsStateWithLifecycle()
    com.javierreansyah.pinecone.ui.components.MutationErrorDialog(viewModel.bookActions.mutationErrors)
    val books by viewModel.filteredBooks.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    val selection = remember { LibrarySelection() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isEmpty = books.isEmpty()

    BackHandler(enabled = selection.active, onBack = selection::close)
    Scaffold(
        modifier = if (isEmpty || selection.active) Modifier else Modifier.nestedScroll(
            scrollBehavior.nestedScrollConnection
        ),
        topBar = {
            MultiSelectTopBarTransition(
                isMultiSelect = selection.active,
                multiSelectBar = {
                    SelectedBooksBar(
                        selection = selection,
                        books = books,
                        allBooks = allBooks,
                        onMarkRead = viewModel.bookActions::markBooksReadStatus,
                        onArchive = { viewModel.bookActions.setArchived(it, !archived) },
                        onDelete = viewModel.bookActions::deleteBooks,
                        onInfo = onNavigateToBookInfo,
                        onOrganize = onNavigateToOrganize,
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
                            onClick = { selection.enter() }) {
                            Icon(
                                MaterialSymbols.Outlined.Select,
                                contentDescription = stringResource(R.string.action_select_multiple),
                            )
                        }
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = { showFilterSheet = true }) {
                            Icon(
                                MaterialSymbols.Outlined.Tune,
                                contentDescription = stringResource(R.string.action_filter),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier
            .padding(innerPadding)
            .fillMaxSize()) {
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
                    selectedBooks = selection.ids,
                    isInMultiSelectMode = selection.active,
                    onBookClick = {
                        if (selection.active) selection.toggle(it) else onNavigateToReader(
                            it
                        )
                    },
                    onBookLongClick = { selection.enter(setOf(it)) },
                    scrollKey = Triple(
                        preferences.sortType,
                        preferences.isAscending,
                        preferences.selectedStatus
                    ),
                )
            }
        }
    }
    if (showFilterSheet) {
        FilterResultBottomSheet(
            preferences = preferences,
            availableFormats = allBooks.mapTo(mutableSetOf()) { it.fileFormat() },
            onLayoutModeChange = viewModel.filters::onLayoutModeChange,
            onSortTypeChange = viewModel.filters::onSortTypeChange,
            onStatusToggle = viewModel.filters::toggleStatusFilter,
            onFileFilterToggle = viewModel.filters::toggleFileFilter,
        ) { showFilterSheet = false }
    }
}
