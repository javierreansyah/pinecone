package com.javierreansyah.pinecone.ui.features.library.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Folder
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.AnimatedSelectionIcon
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.LibraryView
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.components.LibraryFilterBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectAppBar
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.ShelvesMultiSelectAppBar
import com.javierreansyah.pinecone.ui.features.library.components.book.BookCollection
import com.javierreansyah.pinecone.ui.features.library.components.book.BookContextMenu
import com.javierreansyah.pinecone.ui.features.library.fileFormat
import com.javierreansyah.pinecone.ui.features.library.organize.SelectSpaceForShelfDialog
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import com.javierreansyah.pinecone.ui.root.AppDrawer
import kotlinx.coroutines.launch
import com.composables.icons.materialsymbols.outlinedfilled.Book as FilledBook
import com.composables.icons.materialsymbols.outlinedfilled.Folder as FilledFolder

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
    onNavigateToOrganize: (String) -> Unit,
    onNavigateToUnsorted: () -> Unit = {},
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDictionaries: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.provideFactory(application),
    )

    val uiState by viewModel.uiState.collectAsState()

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
        toggleArchive = viewModel::toggleArchive,
        toggleReadStatus = viewModel::toggleReadStatus,
        setGlobalSpace = viewModel::setGlobalSpace,
        deleteBook = viewModel::deleteBook,
        renameShelf = viewModel::renameShelf,
        markBooksReadStatus = viewModel::markBooksReadStatus,
        archiveBooks = viewModel::archiveBooks,
        deleteBooks = viewModel::deleteBooks,
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
    onNavigateToOrganize: (String) -> Unit,
    onNavigateToUnsorted: () -> Unit = {},
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDictionaries: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
) {
    val drawerState =
        rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Collapsed)
    val scope = rememberCoroutineScope()

    val showFilterSheet = remember { mutableStateOf(value = false) }
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var selectedShelves by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(value = false) }

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
                    modifier = if (isInMultiSelectMode) Modifier else Modifier.nestedScroll(
                        scrollBehavior.nestedScrollConnection,
                    ),
                    topBar = {
                        LibraryTopBar(
                            isShelvesTab = isShelvesTab,
                            isInMultiSelectMode = isInMultiSelectMode,
                            shelves = uiState.shelves,
                            selectedShelves = selectedShelves,
                            allBooks = uiState.allBooks,
                            filteredBooks = uiState.filteredBooks,
                            selectedBooks = selectedBooks,
                            searchQuery = uiState.searchQuery,
                            searchCategory = uiState.searchCategory,
                            searchResults = uiState.searchResults,
                            scrollBehavior = scrollBehavior,
                            onSearchQueryChange = onSearchQueryChange,
                            onSearchCategoryChange = onSearchCategoryChange,
                            onOpenDrawerClick = { scope.launch { drawerState.expand() } },
                            onFilterClick = { showFilterSheet.value = true },
                            onNavigateToReader = onNavigateToReader,
                            onNavigateToShelf = onNavigateToShelf,
                            onNavigateToAuthor = onNavigateToAuthor,
                            onNavigateToTag = onNavigateToTag,
                            onAuthorsHeaderClick = onNavigateToAllAuthors,
                            onTagsHeaderClick = onNavigateToAllTags,
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
                            onClearBooksSelection = { selectedBooks = emptySet() },
                            onSelectAllBooks = {
                                selectedBooks =
                                    uiState.filteredBooks.map { book -> book.id }
                                        .toSet()
                            },
                            onMarkBooksReadStatus = { showMarkAsRead ->
                                markBooksReadStatus(selectedBooks, showMarkAsRead)
                                exitMultiSelect()
                            },
                            onOrganizeBooks = {
                                val ids = selectedBooks.joinToString(",")
                                exitMultiSelect()
                                onNavigateToOrganize(ids)
                            },
                            onArchiveBooks = {
                                archiveBooks(selectedBooks)
                                exitMultiSelect()
                            },
                        ) {
                            deleteBooks(selectedBooks)
                            exitMultiSelect()
                        }
                    },
                    bottomBar = {
                        if (libraryView == LibraryView.Both && uiState.showLibraryBottomNavigation) LibraryShortBottomNavigation(
                            currentPage = pagerState.currentPage,
                            wording = uiState.allBooks.publicationWording(),
                        ) { page ->
                            scope.launch {
                                pagerState.animateScrollToPage(page)
                            }
                        }
                    },
                ) { innerPadding ->
                    var miniPlayerHeight by remember { mutableIntStateOf(0) }
                    val miniPlayerPadding = with(LocalDensity.current) { miniPlayerHeight.toDp() }
                    Box(
                        Modifier
                            .padding(innerPadding)
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
                                        books = uiState.filteredBooks,
                                        bottomContentPadding = miniPlayerPadding,
                                        layoutMode = uiState.bookPreferences.layoutMode,
                                        isImporting = uiState.isImporting,
                                        selectedBooks = selectedBooks,
                                        onBookClick = { bookId ->
                                            if (isInMultiSelectMode) {
                                                selectedBooks =
                                                    if (bookId in selectedBooks) {
                                                        selectedBooks - bookId
                                                    } else {
                                                        selectedBooks + bookId
                                                    }
                                            } else {
                                                onNavigateToReader(bookId)
                                            }
                                        },
                                        onBookLongClick = { selectedBookContext = it to null },
                                        isInMultiSelectMode = isInMultiSelectMode && !isShelvesTab,
                                        scrollKey = uiState.bookPreferences.sortType to uiState.bookPreferences.isAscending,
                                    )
                                }

                                1 -> {
                                    if (uiState.shelves.isEmpty() && uiState.isShelvesLoading) {
                                        // Loading state handled in ShelvesPage if needed
                                    } else {
                                        ShelvesPage(
                                            bottomContentPadding = miniPlayerPadding,
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
                                .onSizeChanged { miniPlayerHeight = it.height }) {
                            com.javierreansyah.pinecone.ui.root.AudioMiniPlayerBar(
                                includeNavigationBarPadding = false,
                                bottomInset = innerPadding.calculateBottomPadding(),
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
                        onOrganize = { onNavigateToOrganize(bookId) },
                        onDeleteBook = { deleteBook(bookId) },
                        onEnterMultiSelect = {
                            selectedBooks = setOf(bookId)
                            isInMultiSelectMode = true
                        },
                    ) { selectedBookContext = null }
                }
            }
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
    isShelvesTab: Boolean,
    isInMultiSelectMode: Boolean,
    shelves: List<ShelfWithCovers>,
    selectedShelves: Set<String>,
    allBooks: List<Book>,
    filteredBooks: List<Book>,
    selectedBooks: Set<String>,
    searchQuery: String,
    searchCategory: SearchCategory,
    searchResults: SearchResults,
    scrollBehavior: SearchBarScrollBehavior,
    onSearchQueryChange: (String) -> Unit,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onFilterClick: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    onAuthorsHeaderClick: () -> Unit,
    onTagsHeaderClick: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onClearShelvesSelection: () -> Unit,
    onSelectAllShelves: () -> Unit,
    onRenameShelf: (String) -> Unit,
    onDeleteShelves: () -> Unit,
    onTransferShelves: ((Boolean) -> Unit)?,
    onClearBooksSelection: () -> Unit,
    onSelectAllBooks: () -> Unit,
    onMarkBooksReadStatus: (Boolean) -> Unit,
    onOrganizeBooks: () -> Unit,
    onArchiveBooks: () -> Unit,
    onDeleteBooks: () -> Unit,
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
                val showMarkAsRead = allBooks
                    .filter { book -> book.id in selectedBooks }
                    .any { book -> !book.isRead }
                MultiSelectAppBar(
                    wording = allBooks.filter { it.id in selectedBooks }.publicationWording(),
                    selectedCount = selectedBooks.size,
                    isAllSelected = filteredBooks.isNotEmpty() && selectedBooks.size == filteredBooks.size,
                    showMarkAsRead = showMarkAsRead,
                    onCloseMultiSelect = onExitMultiSelect,
                    onClearSelection = onClearBooksSelection,
                    onSelectAll = onSelectAllBooks,
                    onMarkAsReadUnread = { onMarkBooksReadStatus(showMarkAsRead) },
                    onOrganize = onOrganizeBooks,
                    onArchive = onArchiveBooks,
                    onDelete = onDeleteBooks,
                )
            }
        },
    ) {
        LibrarySearchTopBar(
            searchQuery = searchQuery,
            searchCategory = searchCategory,
            searchResults = searchResults,
            onSearchQueryChange = onSearchQueryChange,
            onSearchCategoryChange = onSearchCategoryChange,
            onOpenDrawerClick = onOpenDrawerClick,
            onFilterClick = onFilterClick,
            onNavigateToReader = onNavigateToReader,
            onNavigateToShelf = onNavigateToShelf,
            onNavigateToAuthor = onNavigateToAuthor,
            onNavigateToTag = onNavigateToTag,
            onAuthorsHeaderClick = onAuthorsHeaderClick,
            onTagsHeaderClick = onTagsHeaderClick,
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun LibraryShortBottomNavigation(
    currentPage: Int,
    wording: PublicationWording,
    onTabSelected: (Int) -> Unit,
) {
    ShortNavigationBar {
        ShortNavigationBarItem(
            selected = currentPage == 0,
            onClick = { onTabSelected(0) },
            icon = {
                AnimatedSelectionIcon(
                    selected = currentPage == 0,
                    outlinedIcon = MaterialSymbols.Outlined.Book,
                    filledIcon = MaterialSymbols.OutlinedFilled.FilledBook,
                    contentDescription = stringResource(wording.libraryTab),
                )
            },
            label = { Text(stringResource(wording.libraryTab)) },
        )
        ShortNavigationBarItem(
            selected = currentPage == 1,
            onClick = { onTabSelected(1) },
            icon = {
                AnimatedSelectionIcon(
                    selected = currentPage == 1,
                    outlinedIcon = MaterialSymbols.Outlined.Folder,
                    filledIcon = MaterialSymbols.OutlinedFilled.FilledFolder,
                    contentDescription = stringResource(R.string.library_tab_shelves),
                )
            },
            label = { Text(stringResource(R.string.library_tab_shelves)) },
        )
    }
}

@Composable
private fun LibraryBooksTabContent(
    showEmptyState: Boolean,
    books: List<Book>,
    layoutMode: LayoutMode,
    isImporting: Boolean,
    selectedBooks: Set<String>,
    isInMultiSelectMode: Boolean,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    scrollKey: Any? = null,
    bottomContentPadding: Dp = 0.dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (showEmptyState) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Book,
                text = stringResource(R.string.library_empty_items),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
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
                bottomContentPadding = bottomContentPadding,
            )
        }

        LibraryImportProgressOverlay(isImporting = isImporting)
    }
}

@Composable
private fun LibraryImportProgressOverlay(
    isImporting: Boolean,
) {
    if (isImporting) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(150.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(
                        16.dp, Alignment.CenterVertically,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.library_importing),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
