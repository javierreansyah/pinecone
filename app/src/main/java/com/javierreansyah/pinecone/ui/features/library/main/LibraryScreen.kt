package com.javierreansyah.pinecone.ui.features.library.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Folder
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
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
import com.javierreansyah.pinecone.ui.root.AppDrawer
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
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
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDictionaries: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.provideFactory(application)
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
        toggleArchive = viewModel::toggleArchive,
        toggleReadStatus = viewModel::toggleReadStatus,
        setGlobalSpace = viewModel::setGlobalSpace,
        deleteBook = viewModel::deleteBook,
        renameShelf = viewModel::renameShelf,
        deleteShelf = viewModel::deleteShelf,
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
        onNavigateToArchives = onNavigateToArchives,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToDictionaries = onNavigateToDictionaries,
        onImportFilesClick = onImportFilesClick,
        onScanFolderClick = onScanFolderClick
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun LibraryScreen(
    uiState: LibraryScreenUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    onLayoutModeChange: (LayoutMode, Boolean) -> Unit,
    onSortTypeChange: (SortType, Boolean) -> Unit,
    toggleStatusFilter: (StatusFilter, Boolean) -> Unit,
    toggleShelfFilter: (ShelfFilter, Boolean) -> Unit,
    toggleArchive: (String) -> Unit,
    toggleReadStatus: (String) -> Unit,
    setGlobalSpace: (String?) -> Unit,
    deleteBook: (String) -> Unit,
    renameShelf: (String, String) -> Unit,
    deleteShelf: (String) -> Unit,
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
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDictionaries: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit
) {
    val drawerState =
        rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Collapsed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.currentValue == WideNavigationRailValue.Expanded) {
        scope.launch { drawerState.collapse() }
    }

    val showFilterSheet = remember { mutableStateOf(false) }
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var selectedShelves by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 2 })

    val showEmptyState by remember(uiState.filteredBooks, uiState.isBooksLoading) {
        derivedStateOf { uiState.filteredBooks.isEmpty() && !uiState.isBooksLoading }
    }

    val isShelvesTab by remember(pagerState.currentPage) {
        derivedStateOf { pagerState.currentPage == 1 }
    }

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
                onNavigateToAllSpaces = onNavigateToAllSpaces,
                onNavigateToArchives = onNavigateToArchives,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToDictionaries = onNavigateToDictionaries,
                onImportFilesClick = onImportFilesClick,
                onScanFolderClick = onScanFolderClick
            )

            Box(modifier = Modifier.weight(1f)) {
                Scaffold(
                    modifier = if (isInMultiSelectMode) Modifier else Modifier.nestedScroll(
                        scrollBehavior.nestedScrollConnection
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
                                    .filter { it.shelf.id != "unshelved" }
                                    .map { it.shelf.id }
                                    .toSet()
                            },
                            onRenameShelf = { newName ->
                                val shelfId = selectedShelves.firstOrNull()
                                if (shelfId != null) {
                                    renameShelf(shelfId, newName)
                                }
                                exitMultiSelect()
                            },
                            onDeleteShelves = {
                                deleteShelves(selectedShelves)
                                exitMultiSelect()
                            },
                            onClearBooksSelection = { selectedBooks = emptySet() },
                            onSelectAllBooks = {
                                selectedBooks = uiState.filteredBooks.map { it.id }.toSet()
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
                            onDeleteBooks = {
                                deleteBooks(selectedBooks)
                                exitMultiSelect()
                            }
                        )
                    },
                    bottomBar = {
                        LibraryShortBottomNavigation(
                            currentPage = pagerState.currentPage,
                            onTabSelected = { page ->
                                scope.launch {
                                    pagerState.animateScrollToPage(page)
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isInMultiSelectMode,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> {
                                LibraryBooksTabContent(
                                    showEmptyState = showEmptyState,
                                    books = uiState.filteredBooks,
                                    layoutMode = uiState.bookPreferences.layoutMode,
                                    isImporting = uiState.isImporting,
                                    selectedBooks = selectedBooks,
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
                                    onBookLongClick = { selectedBookContext = Pair(it, null) },
                                    isInMultiSelectMode = isInMultiSelectMode && !isShelvesTab,
                                    scrollKey = Pair(
                                        uiState.bookPreferences.sortType,
                                        uiState.bookPreferences.isAscending
                                    )
                                )
                            }

                            1 -> {
                                if (uiState.shelves.isEmpty() && uiState.isShelvesLoading) {
                                    // Loading state handled in ShelvesPage if needed
                                } else {
                                    ShelvesPage(
                                        shelves = uiState.shelves,
                                        onShelfClick = onNavigateToShelf,
                                        onBookClick = onNavigateToReader,
                                        onBookLongClick = { bookId, shelfId ->
                                            selectedBookContext = Pair(bookId, shelfId)
                                        },
                                        layoutMode = uiState.shelvesPreferences.layoutMode,
                                        scrollKey = Pair(
                                            uiState.shelvesPreferences.sortType,
                                            uiState.shelvesPreferences.isAscending
                                        ),
                                        isInMultiSelectMode = isInMultiSelectMode,
                                        selectedShelves = selectedShelves,
                                        onShelfLongClick = { id ->
                                            selectedShelves = setOf(id)
                                            isInMultiSelectMode = true
                                        },
                                        onShelfToggleSelect = { id ->
                                            selectedShelves = if (selectedShelves.contains(id)) {
                                                selectedShelves - id
                                            } else {
                                                selectedShelves + id
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (showFilterSheet.value) {
                        LibraryFilterBottomSheet(
                            isShelvesTab = isShelvesTab,
                            preferences = prefs,
                            onLayoutModeChange = { onLayoutModeChange(it, isShelvesTab) },
                            onSortTypeChange = { onSortTypeChange(it, isShelvesTab) },
                            onStatusToggle = { toggleStatusFilter(it, isShelvesTab) },
                            onShelfFilterToggle = { toggleShelfFilter(it, isShelvesTab) },
                            onDismiss = { showFilterSheet.value = false }
                        )
                    }
                }

                selectedBookContext?.let { context ->
                    val bookId = context.first
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
                        onDismiss = { selectedBookContext = null }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onClearBooksSelection: () -> Unit,
    onSelectAllBooks: () -> Unit,
    onMarkBooksReadStatus: (Boolean) -> Unit,
    onOrganizeBooks: () -> Unit,
    onArchiveBooks: () -> Unit,
    onDeleteBooks: () -> Unit
) {
    MultiSelectTopBarTransition(
        isMultiSelect = isInMultiSelectMode,
        multiSelectBar = {
            if (isShelvesTab) {
                val selectedShelfName = shelves.find { it.shelf.id == selectedShelves.firstOrNull() }?.shelf?.name ?: ""
                val selectableShelvesCount = shelves.count { it.shelf.id != "unshelved" }
                ShelvesMultiSelectAppBar(
                    selectedCount = selectedShelves.size,
                    isAllSelected = selectedShelves.isNotEmpty() && selectedShelves.size == selectableShelvesCount,
                    selectedShelfName = selectedShelfName,
                    onCloseMultiSelect = onExitMultiSelect,
                    onClearSelection = onClearShelvesSelection,
                    onSelectAll = onSelectAllShelves,
                    onRename = onRenameShelf,
                    onDelete = onDeleteShelves
                )
            } else {
                val showMarkAsRead = allBooks
                    .filter { it.id in selectedBooks }
                    .any { !it.isRead }
                MultiSelectAppBar(
                    selectedCount = selectedBooks.size,
                    isAllSelected = filteredBooks.isNotEmpty() && selectedBooks.size == filteredBooks.size,
                    showMarkAsRead = showMarkAsRead,
                    onCloseMultiSelect = onExitMultiSelect,
                    onClearSelection = onClearBooksSelection,
                    onSelectAll = onSelectAllBooks,
                    onMarkAsReadUnread = { onMarkBooksReadStatus(showMarkAsRead) },
                    onOrganize = onOrganizeBooks,
                    onArchive = onArchiveBooks,
                    onDelete = onDeleteBooks
                )
            }
        },
        defaultBar = {
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
                scrollBehavior = scrollBehavior
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryShortBottomNavigation(
    currentPage: Int,
    onTabSelected: (Int) -> Unit
) {
    ShortNavigationBar {
        ShortNavigationBarItem(
            selected = currentPage == 0,
            onClick = { onTabSelected(0) },
            icon = {
                Icon(
                    MaterialSymbols.Outlined.Book,
                    contentDescription = stringResource(R.string.library_tab_books)
                )
            },
            label = { Text(stringResource(R.string.library_tab_books)) }
        )
        ShortNavigationBarItem(
            selected = currentPage == 1,
            onClick = { onTabSelected(1) },
            icon = {
                Icon(
                    MaterialSymbols.Outlined.Folder,
                    contentDescription = stringResource(R.string.library_tab_shelves)
                )
            },
            label = { Text(stringResource(R.string.library_tab_shelves)) }
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
    scrollKey: Any? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (showEmptyState) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Book,
                text = stringResource(R.string.library_empty_books),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        } else {
            BookCollection(
                books = books,
                layoutMode = layoutMode,
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick,
                selectedBooks = selectedBooks,
                isInMultiSelectMode = isInMultiSelectMode,
                scrollKey = scrollKey
            )
        }

        LibraryImportProgressOverlay(isImporting = isImporting)
    }
}

@Composable
private fun LibraryImportProgressOverlay(
    isImporting: Boolean
) {
    if (isImporting) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(150.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(
                        16.dp, Alignment.CenterVertically
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.library_importing),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
