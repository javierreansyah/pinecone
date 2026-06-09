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
import com.example.readerapp.ui.features.library.components.LibraryFilterBottomSheet
import com.example.readerapp.ui.features.library.components.book.BookCollection
import com.example.readerapp.ui.features.library.components.book.BookContextMenu
import com.example.readerapp.ui.theme.spacing
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun LibraryScreen(
    onNavigateToReader: (String) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToTag: (String) -> Unit = {},
    onNavigateToAllAuthors: () -> Unit = {},
    onNavigateToAllTags: () -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit
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
    val filteredBooks by viewModel.filteredBooks.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isBooksLoading by viewModel.isBooksLoading.collectAsState()
    val isShelvesLoading by viewModel.isShelvesLoading.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    // Context Menu State
    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        LibrarySearchTopBar(
            searchQuery = uiState.searchQuery,
            searchCategory = uiState.searchCategory,
            searchResults = searchResults,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearchCategoryChange = viewModel::onSearchCategoryChange,
            onOpenDrawerClick = onOpenDrawerClick,
            onFilterClick = { showFilterSheet = true },
            onNavigateToReader = onNavigateToReader,
            onNavigateToShelf = onNavigateToShelf,
            onNavigateToAuthor = onNavigateToAuthor,
            onNavigateToTag = onNavigateToTag,
            onAuthorsHeaderClick = onNavigateToAllAuthors,
            onTagsHeaderClick = onNavigateToAllTags,
            scrollBehavior = scrollBehavior
        )
    }, bottomBar = {
        ShortNavigationBar {
            ShortNavigationBarItem(selected = pagerState.currentPage == 0, onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(0)
                }
            }, icon = {
                Icon(
                    MaterialSymbols.Outlined.Book,
                    contentDescription = stringResource(R.string.library_tab_books)
                )
            }, label = { Text(stringResource(R.string.library_tab_books)) })
            ShortNavigationBarItem(selected = pagerState.currentPage == 1, onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }, icon = {
                Icon(
                    MaterialSymbols.Outlined.Folder,
                    contentDescription = stringResource(R.string.library_tab_shelves)
                )
            }, label = { Text(stringResource(R.string.library_tab_shelves)) })
        }
    }) { innerPadding ->
        HorizontalPager(
            state = pagerState, modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    // Books Page
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (filteredBooks.isEmpty()) {
                            if (!isBooksLoading) {
                                EmptyState(
                                    icon = MaterialSymbols.Outlined.Book,
                                    text = stringResource(R.string.library_empty_books),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                )
                            }
                        } else {
                            BookCollection(
                                books = filteredBooks,
                                layoutMode = uiState.bookPreferences.layoutMode,
                                onBookClick = onNavigateToReader,
                                onBookLongClick = { selectedBookContext = Pair(it, null) })
                        }

                        if (uiState.isImporting) {
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
                                            spacing.space16, Alignment.CenterVertically
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
                }

                1 -> {
                    // Shelves Page
                    if (shelves.isEmpty() && isShelvesLoading) {
                        // Display nothing while fetching
                    } else {
                        ShelvesPage(
                            shelves = shelves,
                            onShelfClick = onNavigateToShelf,
                            onBookClick = onNavigateToReader,
                            onBookLongClick = { bookId, shelfId ->
                                selectedBookContext = Pair(bookId, shelfId)
                            },
                            layoutMode = uiState.shelvesPreferences.layoutMode
                        )
                    }
                }
            }
        }

        if (showFilterSheet) {
            val isShelvesTab = pagerState.currentPage == 1
            val prefs = if (isShelvesTab) uiState.shelvesPreferences else uiState.bookPreferences

            LibraryFilterBottomSheet(
                isShelvesTab = isShelvesTab,
                preferences = prefs,
                onLayoutModeChange = { viewModel.onLayoutModeChange(it, isShelvesTab) },
                onSortTypeChange = { viewModel.onSortTypeChange(it, isShelvesTab) },
                onStatusToggle = { viewModel.toggleStatusFilter(it, isShelvesTab) },
                onShelfFilterToggle = { viewModel.toggleShelfFilter(it, isShelvesTab) },
                onDismiss = { showFilterSheet = false })
        }

        // Context Menu
        selectedBookContext?.let { context ->
            val bookId = context.first
            val contextShelfId = context.second
            BookContextMenu(
                bookId = bookId,
                shelfId = contextShelfId,
                shelves = shelves,
                allBooks = allBooks,
                onNavigateToBookInfo = onNavigateToBookInfo,
                onToggleArchive = { viewModel.toggleArchive(bookId) },
                onToggleReadStatus = { viewModel.toggleReadStatus(bookId) },
                onRemoveFromShelf = {
                    contextShelfId?.let {
                        viewModel.removeBookFromShelf(
                            it, bookId
                        )
                    }
                },
                onAddToShelf = { shelfId -> viewModel.addBookToShelf(shelfId, bookId) },
                onDeleteBook = { viewModel.deleteBook(bookId) },
                onCreateShelfAndAdd = { name -> viewModel.createShelfAndAddBook(name, bookId) },
                onDismiss = { selectedBookContext = null })
        }
    }
}




