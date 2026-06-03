package com.example.readerapp.ui.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.components.BookGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfDetailScreen(
    shelfId: String,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit
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

    val shelves by viewModel.shelves.collectAsState()
    val shelfWithCovers = shelves.find { it.shelf.id == shelfId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shelfWithCovers?.shelf?.name ?: "Shelf", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (shelfWithCovers != null) {
                val books = shelfWithCovers.books.map { Book.fromEntity(it) }
                if (books.isEmpty()) {
                    Text("No books in this shelf.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                } else {
                    BookGrid(
                        books = books,
                        onBookClick = onNavigateToReader
                    )
                }
            } else {
                Text("Shelf not found.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
