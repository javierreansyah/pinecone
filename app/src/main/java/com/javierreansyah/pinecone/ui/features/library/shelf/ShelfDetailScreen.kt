package com.javierreansyah.pinecone.ui.features.library.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Content_copy
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Drag_handle
import com.composables.icons.materialsymbols.outlined.Drive_file_move
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Format_list_numbered
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Select
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectAppBar
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.ShelfDetailFilterBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.book.BookCollection
import com.javierreansyah.pinecone.ui.features.library.components.book.BookContextMenu
import com.javierreansyah.pinecone.ui.features.library.components.book.BookItem
import com.javierreansyah.pinecone.ui.features.library.fileFormat
import com.javierreansyah.pinecone.ui.features.library.organize.SelectSpaceForShelfDialog
import com.javierreansyah.pinecone.ui.features.library.publicationCountLabel
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding
import kotlinx.coroutines.flow.flowOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ShelfDetailScreen(
    shelfId: String,
    initialShelfName: String = "",
    initialBookCount: Int = 0,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (String) -> Unit,
    viewModel: ShelfDetailViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val spaces by viewModel.allSpaces.collectAsStateWithLifecycle()
    val spacesEnabled by viewModel.spacesEnabled.collectAsStateWithLifecycle()
    var transferCopy by remember { mutableStateOf<Boolean?>(null) }
    var showFilterSheet by remember { mutableStateOf(value = false) }
    var isReordering by remember { mutableStateOf(value = false) }
    var showDeleteDialog by remember { mutableStateOf(value = false) }
    var isRenaming by remember { mutableStateOf(value = false) }
    var renameName by remember { mutableStateOf(TextFieldValue("")) }
    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(value = false) }

    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()
    val shelfWithCovers = shelves.find { (itShelf, _) -> itShelf.id == shelfId }

    val baseBooksFlow = remember(shelfWithCovers) {
        flowOf(shelfWithCovers?.books?.map { Book.fromEntity(it) } ?: emptyList())
    }

    val books by viewModel.getFilteredAndSortedBooks(baseBooksFlow)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var reorderBooks by remember { mutableStateOf(emptyList<Book>()) }

    val displayTitle = shelfWithCovers?.shelf?.name
        ?: initialShelfName.ifEmpty { stringResource(R.string.library_tab_shelves) }
    val displayCount = shelfWithCovers?.books?.size ?: initialBookCount

    val exitMultiSelect = {
        selectedBooks = emptySet()
        isInMultiSelectMode = false
    }

    BackHandler(enabled = isInMultiSelectMode, onBack = exitMultiSelect)

    ShelfDetailContent(
        shelfId = shelfId,
        displayTitle = displayTitle,
        displayCount = publicationCountLabel(
            displayCount,
            publicationWording(shelfWithCovers?.books.orEmpty().map { it.book.fileFormat() })
        ),
        spaceName = if (spacesEnabled && shelfId != "unshelved") spaces.find { it.id == shelfWithCovers?.shelf?.spaceId }?.name else null,
        shelfWithCovers = shelfWithCovers,
        books = books,
        allBooks = allBooks,
        reorderBooks = reorderBooks,
        isReordering = isReordering,
        isRenaming = isRenaming,
        renameName = renameName,
        layoutMode = uiState.value.bookPreferences.layoutMode,
        selectedBooks = selectedBooks,
        isInMultiSelectMode = isInMultiSelectMode,
        onNavigateBack = onNavigateBack,
        onEnterMultiSelect = { bookId ->
            selectedBooks = bookId?.let(::setOf) ?: emptySet()
            isInMultiSelectMode = true
        },
        onExitMultiSelect = exitMultiSelect,
        onSelectAll = {
            selectedBooks =
                books.map { book -> book.id }
                    .toSet()
        },
        onClearSelection = { selectedBooks = emptySet() },
        onToggleBookSelection = { bookId ->
            selectedBooks = if (bookId in selectedBooks) {
                selectedBooks - bookId
            } else {
                selectedBooks + bookId
            }
        },
        onMarkAsReadUnread = { showMarkAsRead ->
            viewModel.markBooksReadStatus(selectedBooks, showMarkAsRead)
            exitMultiSelect()
        },
        onArchiveSelected = {
            viewModel.archiveBooks(selectedBooks)
            exitMultiSelect()
        },
        onDeleteSelected = {
            viewModel.deleteBooks(selectedBooks)
            exitMultiSelect()
        },
        onOrganizeSelected = {
            val ids = selectedBooks.joinToString(",")
            exitMultiSelect()
            onNavigateToOrganize(ids)
        },
        onRenameNameChange = { renameName = it },
        onStartRename = {
            val name = shelfWithCovers?.shelf?.name ?: ""
            renameName = TextFieldValue(text = name, selection = TextRange(name.length))
            isRenaming = true
        },
        onCancelRename = { isRenaming = false },
        onSaveRename = {
            if (renameName.text.isNotBlank()) {
                viewModel.renameShelf(shelfId, renameName.text.trim())
            }
            isRenaming = false
        },
        onStartReordering = {
            if (shelfWithCovers != null) {
                reorderBooks = shelfWithCovers.books.map { Book.fromEntity(it) }
                isReordering = true
            }
        },
        onCancelReorder = { isReordering = false },
        onSaveReorder = {
            viewModel.updateShelfOrder(
                shelfId,
                reorderBooks.map { book -> book.id })
            isReordering = false
        },
        onReorderBooksChange = { reorderBooks = it },
        onShowFilterSheet = { showFilterSheet = true },
        onShowDeleteDialog = { showDeleteDialog = true },
        onTransferShelf = if (spacesEnabled && shelfId != "unshelved") { copy ->
            transferCopy = copy
        } else null,
        onBookClick = onNavigateToReader,
        onBookLongClick = { selectedBookForMenu = it },
        scrollKey = Triple(
            uiState.value.bookPreferences.sortType,
            uiState.value.bookPreferences.isAscending,
            uiState.value.bookPreferences.selectedStatus,
        ),
    )

    transferCopy?.let { copy ->
        SelectSpaceForShelfDialog(
            shelfName = displayTitle,
            spaces = if (copy) spaces else spaces.filter { it.id != shelfWithCovers?.shelf?.spaceId },
            titleRes = if (copy) R.string.library_copy_shelves else R.string.library_move_shelves,
            confirmRes = if (copy) R.string.action_copy else R.string.action_move,
            singleSelect = true,
            onDismiss = { transferCopy = null },
        ) { destinations ->
            viewModel.transferShelf(shelfId, destinations.single(), copy)
            transferCopy = null
        }
    }

    if (showFilterSheet) {
        ShelfDetailFilterBottomSheet(
            shelfId = shelfId,
            preferences = uiState.value.bookPreferences,
            availableFormats = shelfWithCovers?.books.orEmpty()
                .mapTo(mutableSetOf()) { it.book.fileFormat() },
            onLayoutModeChange = viewModel::onLayoutModeChange,
            onSortTypeChange = viewModel::onSortTypeChange,
            onStatusToggle = viewModel::toggleStatusFilter,
            onFileFilterToggle = viewModel::toggleFileFilter,
        ) { showFilterSheet = false }
    }

    if (showDeleteDialog && shelfWithCovers != null) {
        DeleteShelfDialog(
            shelfName = shelfWithCovers.shelf.name,
            wording = publicationWording(shelfWithCovers.books.map { it.book.fileFormat() }),
            onConfirm = {
                viewModel.deleteShelf(shelfId)
                showDeleteDialog = false
                onNavigateBack()
            },
        ) { showDeleteDialog = false }
    }

    selectedBookForMenu?.let { bookId ->
        BookContextMenu(
            bookId = bookId,
            allBooks = allBooks,
            onNavigateToBookInfo = onNavigateToBookInfo,
            onToggleArchive = { viewModel.toggleArchive(bookId) },
            onToggleReadStatus = { viewModel.toggleReadStatus(bookId) },
            onOrganize = onNavigateToOrganize,
            onDeleteBook = { viewModel.deleteBook(bookId) },
            onEnterMultiSelect = {
                selectedBooks = setOf(bookId)
                isInMultiSelectMode = true
            },
        ) { selectedBookForMenu = null }
    }
}

@Composable
private fun ShelfDetailContent(
    modifier: Modifier = Modifier,
    shelfId: String,
    displayTitle: String,
    displayCount: String,
    spaceName: String?,
    shelfWithCovers: ShelfWithCovers?,
    books: List<Book>,
    allBooks: List<Book>,
    reorderBooks: List<Book>,
    isReordering: Boolean,
    isRenaming: Boolean,
    renameName: TextFieldValue,
    layoutMode: LayoutMode,
    selectedBooks: Set<String>,
    isInMultiSelectMode: Boolean,
    onNavigateBack: () -> Unit,
    onEnterMultiSelect: (String?) -> Unit,
    onExitMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleBookSelection: (String) -> Unit,
    onMarkAsReadUnread: (Boolean) -> Unit,
    onArchiveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOrganizeSelected: () -> Unit,
    onRenameNameChange: (TextFieldValue) -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onStartReordering: () -> Unit,
    onCancelReorder: () -> Unit,
    onSaveReorder: () -> Unit,
    onReorderBooksChange: (List<Book>) -> Unit,
    onShowFilterSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onTransferShelf: ((Boolean) -> Unit)?,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    scrollKey: Any? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = if (isInMultiSelectMode) modifier else modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ShelfDetailTopBar(
                shelfId = shelfId,
                displayTitle = displayTitle,
                displayCount = displayCount,
                spaceName = spaceName,
                books = books,
                allBooks = allBooks,
                selectedBooks = selectedBooks,
                isInMultiSelectMode = isInMultiSelectMode,
                isReordering = isReordering,
                isRenaming = isRenaming,
                renameName = renameName,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onEnterMultiSelect = { onEnterMultiSelect(null) },
                onExitMultiSelect = onExitMultiSelect,
                onSelectAll = onSelectAll,
                onClearSelection = onClearSelection,
                onMarkAsReadUnread = onMarkAsReadUnread,
                onArchiveSelected = onArchiveSelected,
                onDeleteSelected = onDeleteSelected,
                onOrganizeSelected = onOrganizeSelected,
                onRenameNameChange = onRenameNameChange,
                onStartRename = onStartRename,
                onCancelRename = onCancelRename,
                onSaveRename = onSaveRename,
                onStartReordering = onStartReordering,
                onCancelReorder = onCancelReorder,
                onSaveReorder = onSaveReorder,
                onShowFilterSheet = onShowFilterSheet,
                onShowDeleteDialog = onShowDeleteDialog,
                onTransferShelf = onTransferShelf,
            )
        },
    ) { innerPadding ->
        ShelfDetailBookListContent(
            shelfWithCovers = shelfWithCovers,
            books = books,
            reorderBooks = reorderBooks,
            isReordering = isReordering,
            layoutMode = layoutMode,
            selectedBooks = selectedBooks,
            isInMultiSelectMode = isInMultiSelectMode,
            onReorderBooksChange = onReorderBooksChange,
            onBookClick = { bookId ->
                if (isInMultiSelectMode) {
                    onToggleBookSelection(bookId)
                } else {
                    onBookClick(bookId)
                }
            },
            onBookLongClick = onBookLongClick,
            modifier = Modifier.padding(innerPadding),
            scrollKey = scrollKey,
        )
    }
}

@Composable
private fun ShelfDetailTopBar(
    shelfId: String,
    displayTitle: String,
    displayCount: String,
    spaceName: String?,
    books: List<Book>,
    allBooks: List<Book>,
    selectedBooks: Set<String>,
    isInMultiSelectMode: Boolean,
    isReordering: Boolean,
    isRenaming: Boolean,
    renameName: TextFieldValue,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMarkAsReadUnread: (Boolean) -> Unit,
    onArchiveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOrganizeSelected: () -> Unit,
    onRenameNameChange: (TextFieldValue) -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onStartReordering: () -> Unit,
    onCancelReorder: () -> Unit,
    onSaveReorder: () -> Unit,
    onShowFilterSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onTransferShelf: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val showMarkAsRead = remember(allBooks, selectedBooks) {
        allBooks.filter { book -> book.id in selectedBooks }
            .any { book -> !book.isRead }
    }

    MultiSelectTopBarTransition(
        isMultiSelect = isInMultiSelectMode,
        multiSelectBar = {
            MultiSelectAppBar(
                wording = allBooks.filter { it.id in selectedBooks }.publicationWording(),
                selectedCount = selectedBooks.size,
                isAllSelected = books.isNotEmpty() && selectedBooks.size == books.size,
                showMarkAsRead = showMarkAsRead,
                onCloseMultiSelect = onExitMultiSelect,
                onClearSelection = onClearSelection,
                onSelectAll = onSelectAll,
                onMarkAsReadUnread = { onMarkAsReadUnread(showMarkAsRead) },
                onOrganize = onOrganizeSelected,
                onArchive = onArchiveSelected,
                onDelete = onDeleteSelected,
            )
        },
    ) {
        ShelfDetailDefaultTopAppBar(
            shelfId = shelfId,
            displayTitle = displayTitle,
            displayCount = displayCount,
            spaceName = spaceName,
            isReordering = isReordering,
            isRenaming = isRenaming,
            renameName = renameName,
            scrollBehavior = scrollBehavior,
            onNavigateBack = onNavigateBack,
            onRenameNameChange = onRenameNameChange,
            onCancelRename = onCancelRename,
            onSaveRename = onSaveRename,
            onStartReordering = onStartReordering,
            onCancelReorder = onCancelReorder,
            onSaveReorder = onSaveReorder,
            onShowFilterSheet = onShowFilterSheet,
            onRenameClick = onStartRename,
            onDeleteClick = onShowDeleteDialog,
            onTransferShelf = onTransferShelf,
            onEnterMultiSelect = onEnterMultiSelect,
            modifier = modifier,
        )
    }
}

@Composable
private fun ShelfDetailDefaultTopAppBar(
    shelfId: String,
    displayTitle: String,
    displayCount: String,
    spaceName: String?,
    isReordering: Boolean,
    isRenaming: Boolean,
    renameName: TextFieldValue,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onRenameNameChange: (TextFieldValue) -> Unit,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onStartReordering: () -> Unit,
    onCancelReorder: () -> Unit,
    onSaveReorder: () -> Unit,
    onShowFilterSheet: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTransferShelf: ((Boolean) -> Unit)?,
    onEnterMultiSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(value = false) }
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
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            } else {
                Text(
                    text = displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        subtitle = {
            Text(
                listOfNotNull(
                    spaceName,
                    displayCount,
                ).joinToString(" · "),
            )
        },
        navigationIcon = {
            val isEditMode = isReordering || isRenaming
            val rotation by animateFloatAsState(
                targetValue = if (isEditMode) 90f else 0f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "navigationIconRotation",
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
                },
            ) {
                Box(
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = isEditMode,
                        transitionSpec = {
                            fadeIn(animationSpec = navEffectsSpec) togetherWith fadeOut(
                                animationSpec = navEffectsSpec,
                            )
                        },
                        label = "navigationIconContent",
                    ) { targetIsEditMode ->
                        if (targetIsEditMode) {
                            Icon(
                                MaterialSymbols.Outlined.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                            )
                        } else {
                            Icon(
                                MaterialSymbols.Outlined.Arrow_back,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                }
            }
        },
        actions = {
            val isEditMode = isReordering || isRenaming
            val density = LocalDensity.current
            val slideOffsetPx = remember(density) { with(density) { 30.dp.roundToPx() } }
            val actionsSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
            val actionsEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

            AnimatedContent(
                targetState = isEditMode,
                transitionSpec = {
                    if (targetState) {
                        fadeIn(animationSpec = actionsEffectsSpec) togetherWith fadeOut(
                            animationSpec = actionsEffectsSpec,
                        ) + slideOutHorizontally(
                            targetOffsetX = { slideOffsetPx },
                            animationSpec = actionsSpatialSpec,
                        )
                    } else {
                        fadeIn(animationSpec = actionsEffectsSpec) + slideInHorizontally(
                            initialOffsetX = { slideOffsetPx },
                            animationSpec = actionsSpatialSpec,
                        ) togetherWith fadeOut(animationSpec = actionsEffectsSpec)
                    }
                },
                label = "actionsContent",
            ) { targetIsEditMode ->
                if (targetIsEditMode) {
                    FilledIconButton(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(
                                IconButtonDefaults.smallContainerSize(
                                    widthOption = IconButtonDefaults.IconButtonWidthOption.Wide,
                                ),
                            ),
                        shapes = IconButtonDefaults.shapes(),
                        onClick = if (isReordering) onSaveReorder else onSaveRename,
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Check,
                            contentDescription = stringResource(R.string.action_save),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (shelfId != "unshelved") {
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = onStartReordering,
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Format_list_numbered,
                                    contentDescription = stringResource(R.string.action_sort),
                                )
                            }
                        }
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = onShowFilterSheet,
                        ) {
                            Icon(
                                MaterialSymbols.Outlined.Tune,
                                contentDescription = stringResource(R.string.action_filter),
                            )
                        }
                        Box {
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = { showMoreMenu = true },
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.More_vert,
                                    contentDescription = stringResource(R.string.action_more),
                                )
                            }
                            val totalItems =
                                if (shelfId == "unshelved") 1 else if (onTransferShelf != null) 5 else 3
                            DropdownMenuPopup(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                val groupInteractionSource =
                                    remember { MutableInteractionSource() }
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShape(0, 1),
                                    interactionSource = groupInteractionSource,
                                ) {
                                    SelectableDropdownMenuItem(
                                        selected = false,
                                        text = { Text(stringResource(R.string.action_select_multiple)) },
                                        shapes = MenuDefaults.itemShape(0, totalItems),
                                        leadingIcon = {
                                            Icon(
                                                MaterialSymbols.Outlined.Select,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            onEnterMultiSelect()
                                            showMoreMenu = false
                                        },
                                    )
                                    if (shelfId != "unshelved") {
                                        SelectableDropdownMenuItem(
                                            selected = false,
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            shapes = MenuDefaults.itemShape(1, totalItems),
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
                                            },
                                        )
                                        if (onTransferShelf != null) {
                                            listOf(false, true).forEachIndexed { index, copy ->
                                                SelectableDropdownMenuItem(
                                                    selected = false,
                                                    text = { Text(stringResource(if (copy) R.string.library_copy_shelves else R.string.library_move_shelves)) },
                                                    shapes = MenuDefaults.itemShape(
                                                        index + 2,
                                                        totalItems
                                                    ),
                                                    leadingIcon = {
                                                        Icon(
                                                            if (copy) MaterialSymbols.Outlined.Content_copy else MaterialSymbols.Outlined.Drive_file_move,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false; onTransferShelf(copy)
                                                    },
                                                )
                                            }
                                        }
                                        SelectableDropdownMenuItem(
                                            selected = false,
                                            text = {
                                                Text(
                                                    stringResource(R.string.action_delete),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            shapes = MenuDefaults.itemShape(
                                                totalItems - 1,
                                                totalItems
                                            ),
                                            leadingIcon = {
                                                Icon(
                                                    MaterialSymbols.Outlined.Delete,
                                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = {
                                                onDeleteClick()
                                                showMoreMenu = false
                                            },
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
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun ShelfDetailBookListContent(
    shelfWithCovers: ShelfWithCovers?,
    books: List<Book>,
    reorderBooks: List<Book>,
    isReordering: Boolean,
    layoutMode: LayoutMode,
    selectedBooks: Set<String>,
    isInMultiSelectMode: Boolean,
    onReorderBooksChange: (List<Book>) -> Unit,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollKey: Any? = null,
) {
    val layoutEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        if (shelfWithCovers == null) {
            Text(
                stringResource(R.string.library_empty_shelves),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        } else if (books.isEmpty() && !isReordering) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Book,
                text = stringResource(R.string.library_empty_items),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
        } else if (reorderBooks.isEmpty() && isReordering) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Book,
                text = stringResource(R.string.library_empty_items),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
        } else {
            AnimatedContent(
                targetState = isReordering,
                transitionSpec = {
                    fadeIn(
                        animationSpec = layoutEffectsSpec,
                    ) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = layoutEffectsSpec,
                    ) togetherWith fadeOut(animationSpec = layoutEffectsSpec) + scaleOut(
                        targetScale = 0.9f,
                        animationSpec = layoutEffectsSpec,
                    )
                },
                label = "shelfDetailContentReorderTransition",
            ) { targetIsReordering ->
                if (targetIsReordering) {
                    val lazyListState = rememberLazyListState()
                    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        onReorderBooksChange(
                            reorderBooks.toMutableList().apply {
                                add(to.index, removeAt(from.index))
                            },
                        )
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalAudioPlayerBottomPadding.current),
                    ) {
                        items(
                            reorderBooks,
                            { book -> book.id }) { item ->
                            ReorderableItem(reorderState, key = item.id) { isDragging ->
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                    label = "Reorder elevation",
                                )
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = if (isDragging) MaterialTheme.shapes.small else RectangleShape,
                                    tonalElevation = elevation,
                                    shadowElevation = elevation,
                                ) {
                                    BookItem(
                                        book = item,
                                        onClick = {},
                                        onLongClick = { onBookLongClick(item.id) },
                                        isList = true,
                                    ) {
                                        Box(
                                            modifier = Modifier.height(100.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = MaterialSymbols.Outlined.Drag_handle,
                                                contentDescription = stringResource(R.string.action_sort),
                                                modifier = Modifier.draggableHandle(
                                                    onDragStarted = {},
                                                    onDragStopped = {},
                                                ),
                                            )
                                        }
                                    }
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
                        selectedBooks = selectedBooks,
                        isInMultiSelectMode = isInMultiSelectMode,
                        scrollKey = scrollKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteShelfDialog(
    shelfName: String,
    wording: PublicationWording,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_shelf_title)) },
        text = {
            Text(
                stringResource(
                    wording.deleteShelfMessage,
                    shelfName,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
