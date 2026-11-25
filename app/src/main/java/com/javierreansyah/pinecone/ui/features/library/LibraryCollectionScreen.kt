package com.javierreansyah.pinecone.ui.features.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Select
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconButton
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBarTitle
import com.javierreansyah.pinecone.ui.components.TopAppBarActionGroup
import com.javierreansyah.pinecone.ui.features.library.components.FilterResultBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.SelectedBooksBar
import com.javierreansyah.pinecone.ui.features.library.components.book.BookCollection
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding

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
    emptySupportingText: String? = null,
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
                    title = { LibraryTopAppBarTitle(title) },
                    onBack = onNavigateBack,
                    isEmpty = isEmpty,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        TopAppBarActionGroup {
                            ConnectedGroupIconButton(
                                onClick = { selection.enter() },
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Select,
                                    contentDescription = stringResource(R.string.action_select_multiple),
                                )
                            }
                            ConnectedGroupIconButton(
                                onClick = { showFilterSheet = true },
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Tune,
                                    contentDescription = stringResource(R.string.action_filter),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            Modifier
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .fillMaxSize()
        ) {
            if (isEmpty) {
                EmptyState(
                    icon = emptyIcon,
                    text = emptyText,
                    supportingText = emptySupportingText,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding(),
                        )
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
                    topContentPadding = innerPadding.calculateTopPadding(),
                    bottomContentPadding = innerPadding.calculateBottomPadding() + LocalAudioPlayerBottomPadding.current,
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
