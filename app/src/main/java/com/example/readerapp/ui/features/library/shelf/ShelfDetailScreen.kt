package com.example.readerapp.ui.features.library.shelf

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Drag_handle
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Format_list_numbered
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.components.EmptyState
import com.example.readerapp.ui.components.LibraryTopAppBar
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
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit
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
    var isRenaming by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf(TextFieldValue("")) }
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
                isRenaming = isRenaming,
                renameName = renameName,
                onRenameNameChange = { renameName = it },
                onCancelRename = { isRenaming = false },
                onSaveRename = {
                    if (renameName.text.isNotBlank()) {
                        viewModel.renameShelf(shelfId, renameName.text)
                    }
                    isRenaming = false
                },
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
                    val name = shelfWithCovers?.shelf?.name ?: ""
                    renameName = TextFieldValue(
                        text = name,
                        selection = TextRange(name.length)
                    )
                    isRenaming = true
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
            modifier = Modifier.padding(innerPadding),
            scrollKey = Triple(
                uiState.bookPreferences.sortType,
                uiState.bookPreferences.isAscending,
                uiState.bookPreferences.selectedStatus
            )
        )
    }

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

    selectedBookForMenu?.let { bookId ->
        BookContextMenu(
            bookId = bookId,
            shelfId = shelfId,
            allBooks = allBooks,
            onNavigateToBookInfo = onNavigateToBookInfo,
            onToggleArchive = { viewModel.toggleArchive(bookId) },
            onToggleReadStatus = { viewModel.toggleReadStatus(bookId) },
            onRemoveFromShelf = { viewModel.removeBookFromShelf(shelfId, bookId) },
            onAddToShelf = onNavigateToAddToShelf,
            onDeleteBook = { viewModel.deleteBook(bookId) },
            onDismiss = { selectedBookForMenu = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfDetailTopAppBar(
    displayTitle: String,
    displayCount: Int,
    isReordering: Boolean,
    isRenaming: Boolean,
    renameName: TextFieldValue,
    onRenameNameChange: (TextFieldValue) -> Unit,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isRenaming) {
        if (isRenaming) {
            focusRequester.requestFocus()
        }
    }

    LibraryTopAppBar(
        onBack = onNavigateBack,
        modifier = modifier,
        title = {
            if (isRenaming) {
                BasicTextField(
                    value = renameName,
                    onValueChange = onRenameNameChange,
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            } else {
                Text(
                    text = displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        subtitle = {
            Text(
                pluralStringResource(
                    R.plurals.library_shelf_count, displayCount, displayCount
                )
            )
        },
        navigationIcon = {
            val isEditMode = isReordering || isRenaming
            val rotation by animateFloatAsState(
                targetValue = if (isEditMode) 90f else 0f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "navigationIconRotation"
            )
            val navEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

            FilledTonalIconButton(
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                onClick = {
                    if (isEditMode) {
                        if (isReordering) onCancelReorder() else onCancelRename()
                    } else {
                        onNavigateBack()
                    }
                }
            ) {
                Box(
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isEditMode,
                        transitionSpec = {
                            fadeIn(animationSpec = navEffectsSpec) togetherWith
                                    fadeOut(animationSpec = navEffectsSpec)
                        },
                        label = "navigationIconContent"
                    ) { targetIsEditMode ->
                        if (targetIsEditMode) {
                            Icon(
                                MaterialSymbols.Outlined.Close,
                                contentDescription = stringResource(R.string.action_cancel)
                            )
                        } else {
                            Icon(
                                MaterialSymbols.Outlined.Arrow_back,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                }
            }
        },
        actions = {
            val isEditMode = isReordering || isRenaming
            val density = LocalDensity.current
            val slideOffsetPx = remember(density) { with(density) { 20.dp.roundToPx() } }
            val actionsSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
            val actionsEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

            AnimatedContent(
                targetState = isEditMode,
                transitionSpec = {
                    if (targetState) {
                        // Enter Edit Mode: Checkmark fades in, normal actions fade out and slide right by 20dp
                        fadeIn(animationSpec = actionsEffectsSpec) togetherWith
                                fadeOut(animationSpec = actionsEffectsSpec) + slideOutHorizontally(
                            targetOffsetX = { slideOffsetPx },
                            animationSpec = actionsSpatialSpec
                        )
                    } else {
                        // Exit Edit Mode: Normal actions fade in and slide in from right by 20dp, checkmark fades out
                        fadeIn(animationSpec = actionsEffectsSpec) + slideInHorizontally(
                            initialOffsetX = { slideOffsetPx },
                            animationSpec = actionsSpatialSpec
                        ) togetherWith
                                fadeOut(animationSpec = actionsEffectsSpec)
                    }
                },
                label = "actionsContent"
            ) { targetIsEditMode ->
                if (targetIsEditMode) {
                    FilledIconButton(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(
                                IconButtonDefaults.smallContainerSize(
                                    widthOption = IconButtonDefaults.IconButtonWidthOption.Wide
                                )
                            ),
                        shapes = IconButtonDefaults.shapes(),
                        onClick = if (isReordering) onSaveReorder else onSaveRename
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Check,
                            contentDescription = stringResource(R.string.action_save)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                DropdownMenuPopup(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    val groupInteractionSource =
                                        remember { MutableInteractionSource() }
                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShape(0, 1),
                                        interactionSource = groupInteractionSource,
                                    ) {
                                        DropdownMenuItem(
                                            selected = false,
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            shapes = MenuDefaults.itemShape(0, 2),
                                            leadingIcon = {
                                                Icon(
                                                    MaterialSymbols.Outlined.Edit,
                                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                onRenameClick()
                                                showMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            selected = false,
                                            text = {
                                                Text(
                                                    stringResource(R.string.action_delete),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            shapes = MenuDefaults.itemShape(1, 2),
                                            leadingIcon = {
                                                Icon(
                                                    MaterialSymbols.Outlined.Delete,
                                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
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
    modifier: Modifier = Modifier,
    scrollKey: Any? = null
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
            EmptyState(
                icon = MaterialSymbols.Outlined.Book,
                text = stringResource(R.string.library_empty_books),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        } else if (reorderBooks.isEmpty() && isReordering) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Book,
                text = stringResource(R.string.library_empty_books),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        } else {
            AnimatedContent(
                targetState = isReordering,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 100, delayMillis = 100)) +
                            scaleIn(
                                initialScale = 0.9f,
                                animationSpec = tween(durationMillis = 100, delayMillis = 100)
                            ) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 100)) +
                            scaleOut(
                                targetScale = 0.9f,
                                animationSpec = tween(durationMillis = 100)
                            )
                },
                label = "shelfDetailContentReorderTransition"
            ) { targetIsReordering ->
                if (targetIsReordering) {
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
                        onBookLongClick = onBookLongClick,
                        scrollKey = scrollKey
                    )
                }
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
