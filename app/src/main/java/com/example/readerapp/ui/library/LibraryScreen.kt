package com.example.readerapp.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerapp.ui.library.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToReader: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = object : ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application) {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return LibraryViewModel(context.applicationContext as android.app.Application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsState()
    val filteredBooks by viewModel.filteredBooks.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LibraryTopBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSettingsClick = onNavigateToSettings,
                onTuneClick = { showFilterSheet = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (uiState.layoutMode == LayoutMode.Grid) {
                BookGrid(
                    books = filteredBooks,
                    onBookClick = onNavigateToReader
                )
            } else {
                BookList(
                    books = filteredBooks,
                    onBookClick = onNavigateToReader
                )
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
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Importing...")
                        }
                    }
                }
            }
        }

        if (showFilterSheet) {
            FilterSortBottomSheet(
                uiState = uiState,
                onLayoutModeChange = viewModel::onLayoutModeChange,
                onSortTypeChange = viewModel::onSortTypeChange,
                onStatusToggle = viewModel::toggleStatusFilter,
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}


