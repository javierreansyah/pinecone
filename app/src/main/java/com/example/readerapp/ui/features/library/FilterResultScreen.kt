package com.example.readerapp.ui.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.ui.features.library.components.BookGrid
import com.example.readerapp.ui.features.library.components.BookList
import com.example.readerapp.ui.features.library.components.FilterSortBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterResultScreen(
    filterType: String, // "author" or "tag"
    filterValue: String,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = object : ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application) {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return LibraryViewModel(context.applicationContext as android.app.Application, "filter_result") as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    val baseBooksFlow = remember(filterType, filterValue) {
        if (filterType == "author") viewModel.getBooksByAuthor(filterValue)
        else viewModel.getBooksByTag(filterValue)
    }
    val books by viewModel.getFilteredAndSortedBooks(baseBooksFlow)
        .collectAsState(initial = emptyList())

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(filterValue) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(MaterialSymbols.Outlined.Tune, contentDescription = "Filter")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (books.isEmpty()) {
                Text("No books found.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            } else {
                if (uiState.bookPreferences.layoutMode == LayoutMode.Grid) {
                    BookGrid(
                        books = books,
                        onBookClick = onNavigateToReader
                    )
                } else {
                    BookList(
                        books = books,
                        onBookClick = onNavigateToReader
                    )
                }
            }
        }

        if (showFilterSheet) {
            FilterSortBottomSheet(
                preferences = uiState.bookPreferences,
                onLayoutModeChange = viewModel::onLayoutModeChange,
                onSortTypeChange = viewModel::onSortTypeChange,
                onStatusToggle = viewModel::toggleStatusFilter,
                onDismiss = { showFilterSheet = false },
                showViewPicker = true,
                showStatusFilter = true,
                availableSortTypes = listOf(SortType.Title, SortType.Author, SortType.LastRead, SortType.Added, SortType.Progress)
            )
        }
    }
}
