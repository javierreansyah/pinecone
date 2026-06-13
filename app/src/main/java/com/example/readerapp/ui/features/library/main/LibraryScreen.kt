package com.example.readerapp.ui.features.library.main

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Folder
import com.example.readerapp.R
import com.example.readerapp.ui.components.EmptyState
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.SearchCategory
import com.example.readerapp.ui.features.library.ShelfFilter
import com.example.readerapp.ui.features.library.SortType
import com.example.readerapp.ui.features.library.StatusFilter
import com.example.readerapp.ui.features.library.components.LibraryFilterBottomSheet
import com.example.readerapp.ui.features.library.components.book.BookCollection
import com.example.readerapp.ui.features.library.components.book.BookContextMenu
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun LibraryRoute(
    onNavigateToReader: (String) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToTag: (String) -> Unit = {},
    onNavigateToAllAuthors: () -> Unit = {},
    onNavigateToAllTags: () -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(factory = object :
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application) {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return LibraryViewModel(context.applicationContext as Application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    })

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
        removeBookFromShelf = viewModel::removeBookFromShelf,
        deleteBook = viewModel::deleteBook,
        onNavigateToReader = onNavigateToReader,
        onOpenDrawerClick = onOpenDrawerClick,
        onNavigateToShelf = onNavigateToShelf,
        onNavigateToAuthor = onNavigateToAuthor,
        onNavigateToTag = onNavigateToTag,
        onNavigateToAllAuthors = onNavigateToAllAuthors,
        onNavigateToAllTags = onNavigateToAllTags,
        onNavigateToBookInfo = onNavigateToBookInfo,
        onNavigateToAddToShelf = onNavigateToAddToShelf
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
    removeBookFromShelf: (String, String) -> Unit,
    deleteBook: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToTag: (String) -> Unit = {},
    onNavigateToAllAuthors: () -> Unit = {},
    onNavigateToAllTags: () -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit
) {
    val showFilterSheet = remember { mutableStateOf(false) }

    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    // Context Menu State
    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // Derived States for Performance optimization
    val showEmptyState by remember(uiState.filteredBooks, uiState.isBooksLoading) {
        derivedStateOf { uiState.filteredBooks.isEmpty() && !uiState.isBooksLoading }
    }

    val isShelvesTab by remember(pagerState.currentPage) {
        derivedStateOf { pagerState.currentPage == 1 }
    }

    val prefs by remember(isShelvesTab, uiState.shelvesPreferences, uiState.bookPreferences) {
        derivedStateOf {
            if (isShelvesTab) uiState.shelvesPreferences else uiState.bookPreferences
        }
    }

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        LibrarySearchTopBar(
            searchQuery = uiState.searchQuery,
            searchCategory = uiState.searchCategory,
            searchResults = uiState.searchResults,
            onSearchQueryChange = onSearchQueryChange,
            onSearchCategoryChange = onSearchCategoryChange,
            onOpenDrawerClick = onOpenDrawerClick,
            onFilterClick = { showFilterSheet.value = true },
            onNavigateToReader = onNavigateToReader,
            onNavigateToShelf = onNavigateToShelf,
            onNavigateToAuthor = onNavigateToAuthor,
            onNavigateToTag = onNavigateToTag,
            onAuthorsHeaderClick = onNavigateToAllAuthors,
            onTagsHeaderClick = onNavigateToAllTags,
            scrollBehavior = scrollBehavior
        )
    }, bottomBar = {
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
            state = pagerState, modifier = Modifier
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
                        onNavigateToReader = onNavigateToReader,
                        onBookLongClick = { selectedBookContext = Pair(it, null) },
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
                            )
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
            onDeleteBook = { deleteBook(bookId) },
            onDismiss = { selectedBookContext = null })
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
    books: List<com.example.readerapp.data.model.Book>,
    layoutMode: LayoutMode,
    isImporting: Boolean,
    onNavigateToReader: (String) -> Unit,
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
                onBookClick = onNavigateToReader,
                onBookLongClick = onBookLongClick,
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
