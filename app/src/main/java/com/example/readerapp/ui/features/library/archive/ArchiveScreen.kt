package com.example.readerapp.ui.features.library.archive

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Book
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.example.readerapp.ui.features.library.components.book.BookGrid
import com.example.readerapp.ui.components.EmptyState
import com.example.readerapp.ui.features.library.archive.ArchiveViewModel
import com.example.readerapp.ui.features.library.components.book.BookContextMenu

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ArchiveViewModel = viewModel(factory = object :
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application) {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArchiveViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return ArchiveViewModel(context.applicationContext as Application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    })

    val archivedBooks by viewModel.archivedBooks.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.library_archives_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            MaterialSymbols.Outlined.Arrow_back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior
            )
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (archivedBooks.isEmpty()) {
                EmptyState(
                    icon = MaterialSymbols.Outlined.Book,
                    text = stringResource(R.string.library_empty_archives),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                BookGrid(
                    books = archivedBooks,
                    onBookClick = onNavigateToReader,
                    onBookLongClick = { selectedBookForMenu = it })
            }
        }

        selectedBookForMenu?.let { bookId ->
            BookContextMenu(
                bookId = bookId,
                shelfId = null,
                shelves = shelves,
                allBooks = allBooks,
                onNavigateToBookInfo = onNavigateToBookInfo,
                onToggleArchive = { viewModel.toggleArchive(bookId) },
                onToggleReadStatus = { viewModel.toggleReadStatus(bookId) },
                onRemoveFromShelf = {},
                onAddToShelf = { shelfId -> viewModel.addBookToShelf(shelfId, bookId) },
                onDeleteBook = { viewModel.deleteBook(bookId) },
                onCreateShelfAndAdd = { name -> viewModel.createShelfAndAddBook(name, bookId) },
                onDismiss = { selectedBookForMenu = null })
        }
    }
}
