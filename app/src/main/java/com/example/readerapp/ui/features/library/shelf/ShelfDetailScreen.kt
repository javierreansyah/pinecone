package com.example.readerapp.ui.features.library.shelf

import android.app.Application
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Drag_handle
import com.composables.icons.materialsymbols.outlined.Format_list_numbered
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.SortType
import com.example.readerapp.ui.features.library.components.ShelfDetailFilterBottomSheet
import com.example.readerapp.ui.features.library.components.book.BookCollection
import com.example.readerapp.ui.features.library.components.book.BookContextMenu
import com.example.readerapp.ui.features.library.components.book.BookItem
import kotlinx.coroutines.flow.flowOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShelfDetailScreen(
    shelfId: String,
    initialShelfName: String = "",
    initialBookCount: Int = 0,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ShelfDetailViewModel = viewModel(factory = object :
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application) {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ShelfDetailViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return ShelfDetailViewModel(
                    context.applicationContext as Application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    })

    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var isReordering by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }
    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }

    val shelves by viewModel.shelves.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    val shelfWithCovers = shelves.find { it.shelf.id == shelfId }

    // Use a flow to get filtered and sorted books
    val baseBooksFlow = remember(shelfWithCovers) {
        flowOf(shelfWithCovers?.books?.map { Book.fromEntity(it) } ?: emptyList())
    }

    val books by viewModel.getFilteredAndSortedBooks(baseBooksFlow)
        .collectAsState(initial = emptyList())

    // Local mutable state for reordering
    var reorderBooks by remember { mutableStateOf(emptyList<Book>()) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val displayTitle = shelfWithCovers?.shelf?.name
        ?: initialShelfName.ifEmpty { stringResource(R.string.library_tab_shelves) }
    val displayCount = shelfWithCovers?.books?.size ?: initialBookCount

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ShelfDetailTopAppBar(
                displayTitle = displayTitle,
                displayCount = displayCount,
                isReordering = isReordering,
                shelfId = shelfId,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onCancelReorder = { isReordering = false },
                onSaveReorder = {
                    viewModel.updateShelfOrder(shelfId, reorderBooks.map { it.id })
                    if (uiState.bookPreferences.sortType != SortType.Custom) {
                        viewModel.onSortTypeChange(SortType.Custom)
                    }
                    isReordering = false
                },
                onStartReordering = {
                    if (shelfWithCovers != null) {
                        reorderBooks = shelfWithCovers.books.map { Book.fromEntity(it) }
                        isReordering = true
                    }
                },
                onShowFilterSheet = { showFilterSheet = true },
                onRenameClick = {
                    newShelfName = shelfWithCovers?.shelf?.name ?: ""
                    showRenameDialog = true
                },
                onDeleteClick = { showDeleteDialog = true }
            )
        }
    ) { innerPadding ->
        ShelfDetailContent(
            shelfWithCovers = shelfWithCovers,
            books = books,
            reorderBooks = reorderBooks,
            isReordering = isReordering,
            layoutMode = uiState.bookPreferences.layoutMode,
            onReorderBooksChange = { reorderBooks = it },
            onBookClick = onNavigateToReader,
            onBookLongClick = { selectedBookForMenu = it },
            modifier = Modifier.padding(innerPadding)
        )

        if (showFilterSheet) {
            ShelfDetailFilterBottomSheet(
                shelfId = shelfId,
                preferences = uiState.bookPreferences,
                onLayoutModeChange = viewModel::onLayoutModeChange,
                onSortTypeChange = viewModel::onSortTypeChange,
                onStatusToggle = viewModel::toggleStatusFilter,
                onDismiss = { showFilterSheet = false }
            )
        }

        if (showDeleteDialog && shelfWithCovers != null) {
            DeleteShelfDialog(
                shelfName = shelfWithCovers.shelf.name,
                onConfirm = {
                    viewModel.deleteShelf(shelfId)
                    showDeleteDialog = false
                    onNavigateBack()
                },
                onDismiss = { showDeleteDialog = false }
            )
        }

        if (showRenameDialog && shelfWithCovers != null) {
            RenameShelfDialog(
                initialName = newShelfName,
                onConfirm = { name ->
                    viewModel.renameShelf(shelfId, name)
                    showRenameDialog = false
                },
                onDismiss = { showRenameDialog = false }
            )
        }

        selectedBookForMenu?.let { bookId ->
            BookContextMenu(
                bookId = bookId,
                shelfId = shelfId,
                shelves = shelves,
                allBooks = allBooks,
                onNavigateToBookInfo = onNavigateToBookInfo,
                onToggleArchive = { viewModel.toggleArchive(bookId) },
                onToggleReadStatus = { viewModel.toggleReadStatus(bookId) },
                onRemoveFromShelf = { viewModel.removeBookFromShelf(shelfId, bookId) },
                onAddToShelf = { targetShelfId -> viewModel.addBookToShelf(targetShelfId, bookId) },
                onDeleteBook = { viewModel.deleteBook(bookId) },
                onCreateShelfAndAdd = { name -> viewModel.createShelfAndAddBook(name, bookId) },
                onDismiss = { selectedBookForMenu = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfDetailTopAppBar(
    displayTitle: String,
    displayCount: Int,
    isReordering: Boolean,
    shelfId: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onCancelReorder: () -> Unit,
    onSaveReorder: () -> Unit,
    onStartReordering: () -> Unit,
    onShowFilterSheet: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    LargeFlexibleTopAppBar(
        modifier = modifier,
        title = { Text(displayTitle) },
        subtitle = {
            Text(
                pluralStringResource(
                    R.plurals.library_shelf_count, displayCount, displayCount
                )
            )
        },
        navigationIcon = {
            if (isReordering) {
                FilledTonalIconButton(
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    onClick = onCancelReorder
                ) {
                    Icon(
                        MaterialSymbols.Outlined.Close,
                        contentDescription = stringResource(R.string.action_cancel)
                    )
                }
            } else {
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
            }
        },
        actions = {
            if (isReordering) {
                FilledIconButton(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(
                            IconButtonDefaults.smallContainerSize(
                                widthOption = IconButtonDefaults.IconButtonWidthOption.Wide
                            )
                        ),
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onSaveReorder
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Check,
                        contentDescription = stringResource(R.string.action_save)
                    )
                }
            } else {
                if (shelfId != "unshelved") {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = onStartReordering
                    ) {
                        Icon(
                            MaterialSymbols.Outlined.Format_list_numbered,
                            contentDescription = stringResource(R.string.action_sort)
                        )
                    }
                }
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onShowFilterSheet
                ) {
                    Icon(
                        MaterialSymbols.Outlined.Tune,
                        contentDescription = stringResource(R.string.action_filter)
                    )
                }
                if (shelfId != "unshelved") {
                    Box {
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = { showMoreMenu = true }
                        ) {
                            Icon(
                                MaterialSymbols.Outlined.More_vert,
                                contentDescription = stringResource(R.string.action_more)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                onClick = {
                                    onRenameClick()
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.action_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    onDeleteClick()
                                    showMoreMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun ShelfDetailContent(
    shelfWithCovers: ShelfWithCovers?,
    books: List<Book>,
    reorderBooks: List<Book>,
    isReordering: Boolean,
    layoutMode: LayoutMode,
    onReorderBooksChange: (List<Book>) -> Unit,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (shelfWithCovers == null) {
            Text(
                stringResource(R.string.library_empty_shelves),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        } else if (books.isEmpty() && !isReordering) {
            Text(
                stringResource(R.string.library_empty_books),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        } else if (reorderBooks.isEmpty() && isReordering) {
            Text(
                stringResource(R.string.library_empty_books),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            if (isReordering) {
                val lazyListState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    onReorderBooksChange(reorderBooks.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    })
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(reorderBooks, { it.id }) { item ->
                        ReorderableItem(reorderState, key = item.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = if (isDragging) MaterialTheme.shapes.small else RectangleShape,
                                tonalElevation = elevation,
                                shadowElevation = elevation
                            ) {
                                BookItem(
                                    book = item,
                                    onClick = {},
                                    onLongClick = { onBookLongClick(item.id) },
                                    isList = true,
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier.height(100.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = MaterialSymbols.Outlined.Drag_handle,
                                                contentDescription = stringResource(R.string.action_sort),
                                                modifier = Modifier.draggableHandle(
                                                    onDragStarted = {},
                                                    onDragStopped = {},
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                BookCollection(
                    books = books,
                    layoutMode = layoutMode,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick
                )
            }
        }
    }
}

@Composable
private fun DeleteShelfDialog(
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_shelf_title)) },
        text = {
            Text(
                stringResource(
                    R.string.library_delete_shelf_message, shelfName
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RenameShelfDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename_shelf_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.library_shelf_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name)
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
