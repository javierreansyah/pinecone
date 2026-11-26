package com.javierreansyah.pinecone.ui.features.library.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.Inbox
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.ImportActionsMenu
import com.javierreansyah.pinecone.ui.components.horizontalPadding
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.LibrarySelection
import com.javierreansyah.pinecone.ui.features.library.LibraryView
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.components.ImportFeedback
import com.javierreansyah.pinecone.ui.features.library.components.ImportFeedbackState
import com.javierreansyah.pinecone.ui.features.library.components.LibraryFilterBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.SelectedBooksBar
import com.javierreansyah.pinecone.ui.features.library.components.ShelvesMultiSelectAppBar
import com.javierreansyah.pinecone.ui.features.library.components.ShelvesPage
import com.javierreansyah.pinecone.ui.features.library.components.book.BookCollection
import com.javierreansyah.pinecone.ui.features.library.components.book.BookContextMenu
import com.javierreansyah.pinecone.ui.features.library.fileFormat
import com.javierreansyah.pinecone.ui.features.library.organize.SelectSpaceForShelfDialog
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import com.javierreansyah.pinecone.ui.features.settings.components.rememberRestoreBackupAction
import com.javierreansyah.pinecone.ui.root.AppDrawer
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
    onNavigateToReader: (String) -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToTag: (String) -> Unit = {},
    onNavigateToAllAuthors: () -> Unit = {},
    onNavigateToAllTags: () -> Unit = {},
    onNavigateToAllSpaces: () -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    onNavigateToUnsorted: () -> Unit = {},
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDictionaries: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
    importFeedbackState: ImportFeedbackState = ImportFeedbackState(),
    importFeedbackEnabled: Boolean = false,
    onCancelImport: (String) -> Unit = {},
    onDismissImportReport: (String) -> Unit = {},
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    com.javierreansyah.pinecone.ui.components.MutationErrorDialog(viewModel.bookActions.mutationErrors)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onRestoreBackupClick = rememberRestoreBackupAction()

    LibraryScreen(
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSearchCategoryChange = viewModel::onSearchCategoryChange,
        onLayoutModeChange = viewModel::onLayoutModeChange,
        onSortTypeChange = viewModel::onSortTypeChange,
        toggleStatusFilter = viewModel::toggleStatusFilter,
        toggleShelfFilter = viewModel::toggleShelfFilter,
        toggleFileFilter = viewModel::toggleFileFilter,
        onLibraryViewChange = viewModel::onLibraryViewChange,
        transferShelves = viewModel::transferShelves,
        toggleArchive = viewModel.bookActions::toggleArchive,
        toggleReadStatus = viewModel.bookActions::toggleReadStatus,
        setGlobalSpace = viewModel::setGlobalSpace,
        deleteBook = viewModel.bookActions::deleteBook,
        renameShelf = viewModel::renameShelf,
        markBooksReadStatus = viewModel.bookActions::markBooksReadStatus,
        archiveBooks = viewModel.bookActions::archiveBooks,
        deleteBooks = viewModel.bookActions::deleteBooks,
        deleteShelves = viewModel::deleteShelves,
        onNavigateToReader = onNavigateToReader,
        onNavigateToShelf = onNavigateToShelf,
        onNavigateToAuthor = onNavigateToAuthor,
        onNavigateToTag = onNavigateToTag,
        onNavigateToAllAuthors = onNavigateToAllAuthors,
        onNavigateToAllTags = onNavigateToAllTags,
        onNavigateToAllSpaces = onNavigateToAllSpaces,
        onNavigateToBookInfo = onNavigateToBookInfo,
        onNavigateToOrganize = onNavigateToOrganize,
        onNavigateToUnsorted = onNavigateToUnsorted,
        onNavigateToArchives = onNavigateToArchives,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToDictionaries = onNavigateToDictionaries,
        onImportFilesClick = onImportFilesClick,
        onScanFolderClick = onScanFolderClick,
        onRestoreBackupClick = onRestoreBackupClick,
        importFeedbackState = importFeedbackState,
        importFeedbackEnabled = importFeedbackEnabled,
        onCancelImport = onCancelImport,
        onDismissImportReport = onDismissImportReport,
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryScreenUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    onLayoutModeChange: (LayoutMode, Boolean) -> Unit,
    onSortTypeChange: (SortType, Boolean) -> Unit,
    toggleStatusFilter: (StatusFilter, Boolean) -> Unit,
    toggleShelfFilter: (ShelfFilter, Boolean) -> Unit,
    toggleFileFilter: (FileFilter, Boolean) -> Unit,
    onLibraryViewChange: (LibraryView) -> Unit,
    transferShelves: (Collection<String>, String, Boolean) -> Unit,
    toggleArchive: (String) -> Unit,
    toggleReadStatus: (String) -> Unit,
    setGlobalSpace: (String?) -> Unit,
    deleteBook: (String) -> Unit,
    renameShelf: (String, String) -> Unit,
    markBooksReadStatus: (Collection<String>, Boolean) -> Unit = { _, _ -> },
    archiveBooks: (Collection<String>) -> Unit = {},
    deleteBooks: (Collection<String>) -> Unit = {},
    deleteShelves: (Collection<String>) -> Unit = {},
    onNavigateToReader: (String) -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToTag: (String) -> Unit = {},
    onNavigateToAllAuthors: () -> Unit = {},
    onNavigateToAllTags: () -> Unit = {},
    onNavigateToAllSpaces: () -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    onNavigateToUnsorted: () -> Unit = {},
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDictionaries: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
    onRestoreBackupClick: () -> Unit = {},
    importFeedbackState: ImportFeedbackState = ImportFeedbackState(),
    importFeedbackEnabled: Boolean = false,
    onCancelImport: (String) -> Unit = {},
    onDismissImportReport: (String) -> Unit = {},
) {
    val drawerState =
        rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Collapsed)
    val scope = rememberCoroutineScope()

    val showFilterSheet = remember { mutableStateOf(value = false) }
    var isSearchOpen by rememberSaveable { mutableStateOf(value = false) }
    var shouldAutoFocusSearch by rememberSaveable { mutableStateOf(value = false) }

    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val selection = remember { LibrarySelection() }
    var selectedBooks by selection::ids
    var selectedShelves by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by selection::active

    var transferCopy by remember { mutableStateOf<Boolean?>(null) }
    val libraryView = uiState.bookPreferences.libraryView
    val pagerState = rememberPagerState { if (libraryView == LibraryView.Both) 2 else 1 }
    LaunchedEffect(libraryView, uiState.globalSpaceId) {
        selectedBooks = emptySet()
        selectedShelves = emptySet()
        isInMultiSelectMode = false
    }

    val showEmptyState by remember(uiState.filteredBooks, uiState.isBooksLoading) {
        derivedStateOf { uiState.filteredBooks.isEmpty() && !uiState.isBooksLoading }
    }

    val isShelvesTab =
        libraryView == LibraryView.Shelves || libraryView == LibraryView.Both && pagerState.currentPage == 1

    val isMultiSelect = isInMultiSelectMode && !isShelvesTab
    val isShelvesMultiSelect = isInMultiSelectMode && isShelvesTab

    val exitMultiSelect = {
        if (isShelvesTab) {
            selectedShelves = emptySet()
        } else {
            selectedBooks = emptySet()
        }
        isInMultiSelectMode = false
    }

    BackHandler(enabled = isMultiSelect || isShelvesMultiSelect, onBack = exitMultiSelect)

    val prefs by remember(isShelvesTab, uiState.shelvesPreferences, uiState.bookPreferences) {
        derivedStateOf {
            if (isShelvesTab) uiState.shelvesPreferences else uiState.bookPreferences
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppDrawer(
                drawerState = drawerState,
                allSpaces = uiState.allSpaces,
                selectedSpaceId = uiState.globalSpaceId,
                onSpaceSelected = { setGlobalSpace(it) },
                hasUnsortedBooks = uiState.hasUnsortedBooks,
                showAllSpaces = uiState.showAllSpaces,
                spacesEnabled = uiState.spacesEnabled,
                onNavigateToUnsorted = onNavigateToUnsorted,
                onNavigateToAllSpaces = onNavigateToAllSpaces,
                onNavigateToArchives = onNavigateToArchives,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToDictionaries = onNavigateToDictionaries,
                onImportFilesClick = onImportFilesClick,
                onScanFolderClick = onScanFolderClick,
            )

            Box(modifier = Modifier.weight(1f)) {
                Scaffold(
                    topBar = {
                        val wording = uiState.allBooks.publicationWording()
                        val activeCustomSpaceName = if (
                            uiState.spacesEnabled &&
                            uiState.globalSpaceId != null &&
                            uiState.globalSpaceId != "_all_"
                        ) {
                            uiState.allSpaces.find { it.id == uiState.globalSpaceId }?.name
                        } else {
                            null
                        }
                        val homeTitle = activeCustomSpaceName ?: stringResource(
                            if (isShelvesTab) R.string.library_tab_shelves else wording.libraryTab,
                        )
                        LibraryTopBar(
                            title = homeTitle,
                            isShelvesTab = isShelvesTab,
                            isInMultiSelectMode = isInMultiSelectMode,
                            showViewSwitcher = libraryView == LibraryView.Both && uiState.showLibraryBottomNavigation,
                            wording = wording,
                            shelves = uiState.shelves,
                            selectedShelves = selectedShelves,
                            onOpenDrawerClick = { scope.launch { drawerState.expand() } },
                            onSearchClick = {
                                shouldAutoFocusSearch = true
                                isSearchOpen = true
                            },
                            onFilterClick = { showFilterSheet.value = true },
                            onToggleView = {
                                scope.launch {
                                    pagerState.animateScrollToPage(if (isShelvesTab) 0 else 1)
                                }
                            },
                            onExitMultiSelect = exitMultiSelect,
                            onClearShelvesSelection = { selectedShelves = emptySet() },
                            onSelectAllShelves = {
                                selectedShelves = uiState.shelves
                                    .filter { (itShelf, _) -> itShelf.id != "unshelved" }
                                    .map { (itShelf2, _) -> itShelf2.id }
                                    .toSet()
                            },
                            onRenameShelf = { newName ->
                                val shelfId = selectedShelves.firstOrNull()
                                shelfId?.let { renameShelf(it, newName) }
                                exitMultiSelect()
                            },
                            onTransferShelves = if (uiState.spacesEnabled) { copy ->
                                transferCopy = copy
                            } else null,
                            onDeleteShelves = {
                                deleteShelves(selectedShelves)
                                exitMultiSelect()
                            },
                            content = {
                                SelectedBooksBar(
                                    selection = selection,
                                    books = uiState.filteredBooks,
                                    allBooks = uiState.allBooks,
                                    onMarkRead = markBooksReadStatus,
                                    onArchive = archiveBooks,
                                    onDelete = deleteBooks,
                                    onInfo = onNavigateToBookInfo,
                                    onOrganize = onNavigateToOrganize,
                                )
                            },
                        )
                    },
                ) { innerPadding ->
                    var miniPlayerHeight by remember { mutableIntStateOf(0) }
                    val miniPlayerPadding = with(LocalDensity.current) { miniPlayerHeight.toDp() }
                    val topPadding = innerPadding.calculateTopPadding()
                    val bottomPadding = innerPadding.calculateBottomPadding()
                    Box(
                        Modifier
                            .horizontalPadding(innerPadding)
                            .fillMaxSize()
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = !isInMultiSelectMode,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            when (if (libraryView == LibraryView.Shelves) 1 else page) {
                                0 -> {
                                    LibraryBooksTabContent(
                                        showEmptyState = showEmptyState,
                                        isLibraryEmpty = !uiState.hasAnyBooksInDatabase,
                                        hasUnsortedBooks = uiState.hasUnsortedBooks,
                                        books = uiState.filteredBooks,
                                        topContentPadding = topPadding,
                                        bottomContentPadding = bottomPadding + miniPlayerPadding,
                                        layoutMode = uiState.bookPreferences.layoutMode,
                                        selectedBooks = selectedBooks,
                                        onBookClick = { bookId ->
                                            if (isInMultiSelectMode) {
                                                selection.toggle(bookId)
                                            } else {
                                                onNavigateToReader(bookId)
                                            }
                                        },
                                        onBookLongClick = { selectedBookContext = it to null },
                                        isInMultiSelectMode = isInMultiSelectMode && !isShelvesTab,
                                        scrollKey = listOf(
                                            uiState.globalSpaceId,
                                            uiState.bookPreferences.sortType,
                                            uiState.bookPreferences.isAscending,
                                            uiState.bookPreferences.selectedStatus,
                                            uiState.bookPreferences.selectedShelfFilter,
                                            uiState.bookPreferences.selectedFormats,
                                        ),
                                        emptyAction = when {
                                            !uiState.hasAnyBooksInDatabase -> {
                                                {
                                                    EmptyLibraryActions(
                                                        onImportFilesClick = onImportFilesClick,
                                                        onScanFolderClick = onScanFolderClick,
                                                        onRestoreBackupClick = onRestoreBackupClick,
                                                    )
                                                }
                                            }

                                            uiState.hasUnsortedBooks -> {
                                                {
                                                    EmptyLibraryUnsortedAction(
                                                        onOpenUnsortedClick = onNavigateToUnsorted,
                                                    )
                                                }
                                            }

                                            else -> null
                                        },
                                    )
                                }

                                1 -> {
                                    if (uiState.shelves.isEmpty() && uiState.isShelvesLoading) {
                                        // Loading state handled in ShelvesPage if needed
                                    } else {
                                        ShelvesPage(
                                            topContentPadding = topPadding,
                                            bottomContentPadding = bottomPadding + miniPlayerPadding,
                                            shelves = uiState.shelves,
                                            spaceNames = if (uiState.spacesEnabled && (uiState.globalSpaceId == null || uiState.globalSpaceId == "_all_")) uiState.allSpaces.associate { it.id to it.name } else emptyMap(),
                                            onShelfClick = onNavigateToShelf,
                                            onBookClick = onNavigateToReader,
                                            onBookLongClick = { bookId, shelfId ->
                                                selectedBookContext = bookId to shelfId
                                            },
                                            layoutMode = uiState.shelvesPreferences.layoutMode,
                                            scrollKey = uiState.shelvesPreferences.sortType to uiState.shelvesPreferences.isAscending,
                                            isInMultiSelectMode = isInMultiSelectMode,
                                            selectedShelves = selectedShelves,
                                            onShelfLongClick = { id ->
                                                selectedShelves = setOf(id)
                                                isInMultiSelectMode = true
                                            },
                                        ) { id ->
                                            selectedShelves = if (id in selectedShelves) {
                                                selectedShelves - id
                                            } else {
                                                selectedShelves + id
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomPadding)
                                .onSizeChanged { miniPlayerHeight = it.height }) {
                            com.javierreansyah.pinecone.ui.root.AudioMiniPlayerBar(
                                includeNavigationBarPadding = false,
                                bottomInset = bottomPadding,
                            )
                        }
                        if (importFeedbackEnabled) {
                            ImportFeedback(
                                state = importFeedbackState,
                                onCancel = onCancelImport,
                                onDismissReport = onDismissImportReport,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = bottomPadding + miniPlayerPadding),
                            )
                        }
                    }

                    if (showFilterSheet.value) {
                        LibraryFilterBottomSheet(
                            isShelvesTab = isShelvesTab,
                            preferences = prefs,
                            availableFormats = uiState.allBooks.mapTo(mutableSetOf()) { it.fileFormat() },
                            onLayoutModeChange = { onLayoutModeChange(it, isShelvesTab) },
                            onSortTypeChange = { onSortTypeChange(it, isShelvesTab) },
                            onStatusToggle = { toggleStatusFilter(it, isShelvesTab) },
                            onShelfFilterToggle = { toggleShelfFilter(it, isShelvesTab) },
                            onFileFilterToggle = { toggleFileFilter(it, isShelvesTab) },
                            libraryView = libraryView,
                            onLibraryViewChange = onLibraryViewChange,
                        ) { showFilterSheet.value = false }
                    }
                }

                selectedBookContext?.let { (contextFirst, _) ->
                    val bookId = contextFirst
                    BookContextMenu(
                        bookId = bookId,
                        allBooks = uiState.allBooks,
                        showSelectMultiple = !isShelvesTab,
                        onNavigateToBookInfo = onNavigateToBookInfo,
                        onToggleArchive = { toggleArchive(bookId) },
                        onToggleReadStatus = { toggleReadStatus(bookId) },
                        onOrganize = { onNavigateToOrganize(setOf(bookId)) },
                        onDeleteBook = { deleteBook(bookId) },
                        onEnterMultiSelect = {
                            selectedBooks = setOf(bookId)
                            isInMultiSelectMode = true
                        },
                    ) { selectedBookContext = null }
                }
            }
        }

        val searchOffsetSpec = MaterialTheme.motionScheme.defaultEffectsSpec<IntOffset>()
        val closeSearch = {
            isSearchOpen = false
            shouldAutoFocusSearch = false
            onSearchQueryChange("")
            onSearchCategoryChange(SearchCategory.All)
        }
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = slideInHorizontally(animationSpec = searchOffsetSpec) { it },
            exit = ExitTransition.None,
        ) {
            LibrarySearchOverlay(
                searchQuery = uiState.searchQuery,
                searchCategory = uiState.searchCategory,
                searchResults = uiState.searchResults,
                onSearchQueryChange = onSearchQueryChange,
                onSearchCategoryChange = onSearchCategoryChange,
                onClose = closeSearch,
                onNavigateToReader = onNavigateToReader,
                onNavigateToShelf = onNavigateToShelf,
                onNavigateToAuthor = onNavigateToAuthor,
                onNavigateToTag = onNavigateToTag,
                onAuthorsHeaderClick = onNavigateToAllAuthors,
                onTagsHeaderClick = onNavigateToAllTags,
                autoFocus = shouldAutoFocusSearch,
                onAutoFocusConsumed = { shouldAutoFocusSearch = false },
            )
        }
    }
    transferCopy?.let { copy ->
        SelectSpaceForShelfDialog(
            shelfName = selectedShelves.size.toString(),
            spaces = uiState.allSpaces.filter { destination -> copy || uiState.allShelves.any { it.id in selectedShelves && it.spaceId != destination.id } },
            titleRes = if (copy) R.string.library_copy_shelves else R.string.library_move_shelves,
            confirmRes = if (copy) R.string.action_copy else R.string.action_move,
            singleSelect = true,
            onDismiss = { transferCopy = null },
        ) { destinations ->
            transferShelves(selectedShelves, destinations.single(), copy)
            transferCopy = null
            exitMultiSelect()
        }
    }

}

@Composable
private fun LibraryTopBar(
    title: String,
    isShelvesTab: Boolean,
    isInMultiSelectMode: Boolean,
    showViewSwitcher: Boolean,
    wording: PublicationWording,
    shelves: List<ShelfWithCovers>,
    selectedShelves: Set<String>,
    onOpenDrawerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    onToggleView: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onClearShelvesSelection: () -> Unit,
    onSelectAllShelves: () -> Unit,
    onRenameShelf: (String) -> Unit,
    onDeleteShelves: () -> Unit,
    onTransferShelves: ((Boolean) -> Unit)?,
    content: @Composable () -> Unit,
) {
    MultiSelectTopBarTransition(
        isMultiSelect = isInMultiSelectMode,
        multiSelectBar = {
            if (isShelvesTab) {
                val selectedShelfName =
                    shelves.find { (itShelf3, _) -> itShelf3.id == selectedShelves.firstOrNull() }?.shelf?.name
                        ?: ""
                val selectableShelvesCount =
                    shelves.count { (itShelf4, _) -> itShelf4.id != "unshelved" }
                ShelvesMultiSelectAppBar(
                    wording = publicationWording(shelves.filter { it.shelf.id in selectedShelves }
                        .flatMap { it.books }.map { it.book.fileFormat() }),
                    selectedCount = selectedShelves.size,
                    isAllSelected = selectedShelves.isNotEmpty() && selectedShelves.size == selectableShelvesCount,
                    selectedShelfName = selectedShelfName,
                    onCloseMultiSelect = onExitMultiSelect,
                    onClearSelection = onClearShelvesSelection,
                    onSelectAll = onSelectAllShelves,
                    onRename = onRenameShelf,
                    onDelete = onDeleteShelves,
                    onTransfer = onTransferShelves,
                )
            } else {
                content()
            }
        },
    ) {
        LibraryHomeTopAppBar(
            title = title,
            onOpenDrawerClick = onOpenDrawerClick,
            onSearchClick = onSearchClick,
            onFilterClick = onFilterClick,
            showViewSwitcher = showViewSwitcher,
            isShelvesTab = isShelvesTab,
            wording = wording,
            onToggleView = onToggleView,
        )
    }
}

@Composable
private fun LibraryBooksTabContent(
    showEmptyState: Boolean,
    isLibraryEmpty: Boolean,
    hasUnsortedBooks: Boolean,
    books: List<Book>,
    layoutMode: LayoutMode,
    selectedBooks: Set<String>,
    isInMultiSelectMode: Boolean,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    scrollKey: Any? = null,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    emptyAction: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        if (showEmptyState) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Book,
                text = stringResource(
                    if (isLibraryEmpty) R.string.library_empty_library_title
                    else R.string.library_empty_items,
                ),
                supportingText = stringResource(
                    when {
                        isLibraryEmpty -> R.string.library_empty_library_subtitle
                        hasUnsortedBooks -> R.string.library_empty_items_unsorted_subtitle
                        else -> R.string.library_empty_items_subtitle
                    },
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topContentPadding, bottom = bottomContentPadding)
                    .padding(16.dp),
                action = emptyAction,
            )
        } else {
            BookCollection(
                books = books,
                layoutMode = layoutMode,
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick,
                selectedBooks = selectedBooks,
                isInMultiSelectMode = isInMultiSelectMode,
                scrollKey = scrollKey,
                topContentPadding = topContentPadding + 8.dp,
                bottomContentPadding = bottomContentPadding,
            )
        }
    }
}

@Composable
private fun EmptyLibraryActions(
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(value = false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Button(
                onClick = { menuExpanded = true },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.action_import))
            }

            ImportActionsMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                onImportFilesClick = onImportFilesClick,
                onScanFolderClick = onScanFolderClick,
            )
        }

        TextButton(
            onClick = onRestoreBackupClick,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.History,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.nav_restore_backup))
        }
    }
}

@Composable
private fun EmptyLibraryUnsortedAction(
    onOpenUnsortedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onOpenUnsortedClick,
        modifier = modifier,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(
            imageVector = MaterialSymbols.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = stringResource(R.string.library_open_unsorted))
    }
}
