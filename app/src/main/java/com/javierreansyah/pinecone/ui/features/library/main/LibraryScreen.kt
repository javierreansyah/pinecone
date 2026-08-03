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
    onNavigateToCollection: (String) -> Unit = {},
    onNavigateToAllAuthors: () -> Unit = {},
    onNavigateToAllTags: () -> Unit = {},
    onNavigateToAllCollections: () -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit,
    onNavigateToAddToCollection: (String) -> Unit,
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
        setGlobalCollection = viewModel::setGlobalCollection,
        removeBookFromShelf = viewModel::removeBookFromShelf,
        removeBookFromCollection = viewModel::removeBookFromCollection,
        deleteBook = viewModel::deleteBook,
        renameShelf = viewModel::renameShelf,
        deleteShelf = viewModel::deleteShelf,
        onNavigateToReader = onNavigateToReader,
        onNavigateToShelf = onNavigateToShelf,
        onNavigateToAuthor = onNavigateToAuthor,
        onNavigateToTag = onNavigateToTag,
        onNavigateToCollection = onNavigateToCollection,
        onNavigateToAllAuthors = onNavigateToAllAuthors,
        onNavigateToAllTags = onNavigateToAllTags,
        onNavigateToAllCollections = onNavigateToAllCollections,
        onNavigateToBookInfo = onNavigateToBookInfo,
        onNavigateToAddToShelf = onNavigateToAddToShelf,
        onNavigateToAddToCollection = onNavigateToAddToCollection,
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
    setGlobalCollection: (String?) -> Unit,
    removeBookFromShelf: (String, String) -> Unit,
    removeBookFromCollection: (String) -> Unit,
    deleteBook: (String) -> Unit,
    renameShelf: (String, String) -> Unit,
    deleteShelf: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToTag: (String) -> Unit = {},
    onNavigateToCollection: (String) -> Unit = {},
    onNavigateToAllAuthors: () -> Unit = {},
    onNavigateToAllTags: () -> Unit = {},
    onNavigateToAllCollections: () -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit,
    onNavigateToAddToCollection: (String) -> Unit,
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

    // Context Menu & Selection State
    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var selectedShelves by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 2 })

    // Derived States for Performance optimization
    val showEmptyState by remember(uiState.filteredBooks, uiState.isBooksLoading) {
        derivedStateOf { uiState.filteredBooks.isEmpty() && !uiState.isBooksLoading }
    }

    val isShelvesTab by remember(pagerState.currentPage) {
        derivedStateOf { pagerState.currentPage == 1 }
    }

    val isMultiSelect = isInMultiSelectMode && !isShelvesTab
    val isShelvesMultiSelect = isInMultiSelectMode && isShelvesTab

    BackHandler(enabled = isMultiSelect || isShelvesMultiSelect) {
        if (isShelvesTab) {
            selectedShelves = emptySet()
        } else {
            selectedBooks = emptySet()
        }
        isInMultiSelectMode = false
    }

    val prefs by remember(isShelvesTab, uiState.shelvesPreferences, uiState.bookPreferences) {
        derivedStateOf {
            if (isShelvesTab) uiState.shelvesPreferences else uiState.bookPreferences
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppDrawer(
                drawerState = drawerState,
                allCollections = uiState.allCollections,
                selectedCollectionId = uiState.globalCollectionId,
                onCollectionSelected = { setGlobalCollection(it) },
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
                        MultiSelectTopBarTransition(
                            isMultiSelect = isInMultiSelectMode,
                            multiSelectBar = {
                                if (isShelvesTab) {
                                    val selectedShelfName =
                                        uiState.shelves.find { it.shelf.id == selectedShelves.firstOrNull() }?.shelf?.name
                                            ?: ""
                                    ShelvesMultiSelectAppBar(
                                        selectedCount = selectedShelves.size,
                                        isAllSelected = selectedShelves.isNotEmpty() && selectedShelves.size == uiState.shelves.count { it.shelf.id != "unshelved" },
                                        selectedShelfName = selectedShelfName,
                                        onCloseMultiSelect = {
                                            selectedShelves = emptySet()
                                            isInMultiSelectMode = false
                                        },
                                        onClearSelection = { selectedShelves = emptySet() },
                                        onSelectAll = {
                                            selectedShelves =
                                                uiState.shelves.filter { it.shelf.id != "unshelved" }
                                                    .map { it.shelf.id }.toSet()
                                        },
                                        onRename = { newName ->
                                            val shelfId = selectedShelves.firstOrNull()
                                            if (shelfId != null) {
                                                renameShelf(shelfId, newName)
                                            }
                                            selectedShelves = emptySet()
                                            isInMultiSelectMode = false
                                        },
                                        onDelete = {
                                            val shelvesToProcess = selectedShelves.toList()
                                            selectedShelves = emptySet()
                                            isInMultiSelectMode = false
                                            shelvesToProcess.forEach { deleteShelf(it) }
                                        }
                                    )
                                } else {
                                    val showMarkAsRead = uiState.allBooks
                                        .filter { it.id in selectedBooks }
                                        .any { !it.isRead }
                                    MultiSelectAppBar(
                                        selectedCount = selectedBooks.size,
                                        isAllSelected = selectedBooks.size == uiState.filteredBooks.size,
                                        showMarkAsRead = showMarkAsRead,
                                        onCloseMultiSelect = {
                                            selectedBooks = emptySet()
                                            isInMultiSelectMode = false
                                        },
                                        onClearSelection = { selectedBooks = emptySet() },
                                        onSelectAll = {
                                            selectedBooks =
                                                uiState.filteredBooks.map { it.id }.toSet()
                                        },
                                        onMarkAsReadUnread = {
                                            val booksToProcess = selectedBooks.toList()
                                            selectedBooks = emptySet()
                                            isInMultiSelectMode = false
                                            booksToProcess.forEach { bookId ->
                                                val book = uiState.allBooks.find { it.id == bookId }
                                                if (book != null && book.isRead != showMarkAsRead) {
                                                    toggleReadStatus(bookId)
                                                }
                                            }
                                        },
                                        onAddToShelf = {
                                            val ids = selectedBooks.joinToString(",")
                                            selectedBooks = emptySet()
                                            isInMultiSelectMode = false
                                            onNavigateToAddToShelf(ids)
                                        },
                                        onAddToCollection = {
                                            val ids = selectedBooks.joinToString(",")
                                            selectedBooks = emptySet()
                                            isInMultiSelectMode = false
                                            onNavigateToAddToCollection(ids)
                                        },
                                        onArchive = {
                                            val booksToProcess = selectedBooks.toList()
                                            selectedBooks = emptySet()
                                            isInMultiSelectMode = false
                                            booksToProcess.forEach { bookId ->
                                                val book = uiState.allBooks.find { it.id == bookId }
                                                if (book != null && !book.isArchived) {
                                                    toggleArchive(bookId)
                                                }
                                            }
                                        },
                                        onDelete = {
                                            val booksToProcess = selectedBooks.toList()
                                            selectedBooks = emptySet()
                                            isInMultiSelectMode = false
                                            booksToProcess.forEach { deleteBook(it) }
                                        }
                                    )
                                }
                            },
                            defaultBar = {
                                LibrarySearchTopBar(
                                    searchQuery = uiState.searchQuery,
                                    searchCategory = uiState.searchCategory,
                                    searchResults = uiState.searchResults,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onSearchCategoryChange = onSearchCategoryChange,
                                    onOpenDrawerClick = {
                                        scope.launch { drawerState.expand() }
                                    },
                                    onFilterClick = { showFilterSheet.value = true },
                                    onNavigateToReader = onNavigateToReader,
                                    onNavigateToShelf = onNavigateToShelf,
                                    onNavigateToAuthor = onNavigateToAuthor,
                                    onNavigateToTag = onNavigateToTag,
                                    onNavigateToCollection = onNavigateToCollection,
                                    onAuthorsHeaderClick = onNavigateToAllAuthors,
                                    onTagsHeaderClick = onNavigateToAllTags,
                                    onCollectionsHeaderClick = onNavigateToAllCollections,
                                    scrollBehavior = scrollBehavior
                                )
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
                    }) { innerPadding ->
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
                                // Shelves Page
                                if (uiState.shelves.isEmpty() && uiState.isShelvesLoading) {
                                    // Display nothing while fetching
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
                            onDismiss = { showFilterSheet.value = false })
                    }
                }

                // Context Menu
                selectedBookContext?.let { context ->
                    val bookId = context.first
                    val contextShelfId = context.second
                    BookContextMenu(
                        bookId = bookId,
                        shelfId = contextShelfId,
                        allBooks = uiState.allBooks,
                        showSelectMultiple = !isShelvesTab,
                        onNavigateToBookInfo = onNavigateToBookInfo,
                        onToggleArchive = { toggleArchive(bookId) },
                        onToggleReadStatus = { toggleReadStatus(bookId) },
                        onRemoveFromShelf = {
                            contextShelfId?.let {
                                removeBookFromShelf(
                                    it, bookId
                                )
                            }
                        },
                        onAddToShelf = onNavigateToAddToShelf,
                        onAddToCollection = onNavigateToAddToCollection,
                        onRemoveFromCollection = { removeBookFromCollection(bookId) },
                        onDeleteBook = { deleteBook(bookId) },
                        onEnterMultiSelect = {
                            selectedBooks = setOf(bookId)
                            isInMultiSelectMode = true
                        },
                        onDismiss = { selectedBookContext = null })
                }
            }
        }
    }
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
    books: List<com.javierreansyah.pinecone.data.model.Book>,
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
