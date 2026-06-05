package com.example.readerapp.ui.features.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerapp.ui.features.library.components.BookGrid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArchiveScreen(
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

    val archivedBooks by viewModel.archivedBooks.collectAsState()
    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Archives") },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onNavigateBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (archivedBooks.isEmpty()) {
                Text("No archived books.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            } else {
                BookGrid(
                    books = archivedBooks,
                    onBookClick = onNavigateToReader,
                    onBookLongClick = { selectedBookForMenu = it }
                )
            }
        }

        if (selectedBookForMenu != null) {
            ModalBottomSheet(onDismissRequest = { selectedBookForMenu = null }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("Options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Unarchive", style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier.clickable {
                            viewModel.toggleArchive(selectedBookForMenu!!)
                            selectedBookForMenu = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Delete", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            showDeleteConfirmation = true
                        }
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Book", style = MaterialTheme.typography.titleLarge) },
                text = { Text("Are you sure you want to delete this book? This action cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBook(selectedBookForMenu!!)
                            showDeleteConfirmation = false
                            selectedBookForMenu = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    }
}
