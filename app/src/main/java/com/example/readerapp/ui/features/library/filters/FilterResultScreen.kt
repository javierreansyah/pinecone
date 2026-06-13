package com.example.readerapp.ui.features.library.filters

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.R
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.components.LibraryTopAppBar
import com.example.readerapp.ui.features.library.components.FilterResultBottomSheet
import com.example.readerapp.ui.features.library.components.book.BookCollection
import com.example.readerapp.ui.features.library.components.book.BookContextMenu

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterResultScreen(
    filterType: String, // "author" or "tag"
    filterValue: String,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToMerged: (String) -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: FilterResultViewModel = viewModel(factory = object :
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application) {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FilterResultViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return FilterResultViewModel(
                    context.applicationContext as Application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    })

    val uiState by viewModel.uiState.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()

    val allAuthors by viewModel.allAuthors.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    val suggestionList = remember(filterType, allAuthors, allTags) {
        if (filterType == "author") allAuthors.map { it.name }
        else allTags.map { it.name }
    }

    val baseBooksFlow = remember(filterType, filterValue) {
        if (filterType == "author") viewModel.getBooksByAuthor(filterValue)
        else viewModel.getBooksByTag(filterValue)
    }
    val books by viewModel.getFilteredAndSortedBooks(baseBooksFlow)
        .collectAsState(initial = emptyList())

    FilterResultContent(
        filterType = filterType,
        filterValue = filterValue,
        uiState = uiState,
        allBooks = allBooks,
        books = books,
        suggestionList = suggestionList,
        onNavigateBack = onNavigateBack,
        onNavigateToReader = onNavigateToReader,
        onNavigateToBookInfo = onNavigateToBookInfo,
        onLayoutModeChange = { mode -> viewModel.onLayoutModeChange(mode) },
        onSortTypeChange = { sort -> viewModel.onSortTypeChange(sort) },
        onStatusToggle = { status -> viewModel.toggleStatusFilter(status) },
        onDeleteFilterItem = {
            viewModel.deleteFilterItem(filterType, filterValue) {
                onNavigateBack()
            }
        },
        onRenameFilterItem = { newName ->
            viewModel.renameFilterItem(filterType, filterValue, newName) { confirmedName ->
                onNavigateToMerged(confirmedName)
            }
        },
        onToggleArchive = { bookId -> viewModel.toggleArchive(bookId) },
        onToggleReadStatus = { bookId -> viewModel.toggleReadStatus(bookId) },
        onRemoveFromShelf = { shelfId, bookId -> viewModel.removeBookFromShelf(shelfId, bookId) },
        onNavigateToAddToShelf = onNavigateToAddToShelf,
        onDeleteBook = { bookId -> viewModel.deleteBook(bookId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterResultContent(
    filterType: String,
    filterValue: String,
    uiState: FilterResultUiState,
    allBooks: List<Book>,
    books: List<Book>,
    suggestionList: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onLayoutModeChange: (com.example.readerapp.ui.features.library.LayoutMode) -> Unit,
    onSortTypeChange: (com.example.readerapp.ui.features.library.SortType) -> Unit,
    onStatusToggle: (com.example.readerapp.ui.features.library.StatusFilter) -> Unit,
    onDeleteFilterItem: () -> Unit,
    onRenameFilterItem: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onToggleReadStatus: (String) -> Unit,
    onRemoveFromShelf: (String, String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf(TextFieldValue("")) }
    var showMergeWarningDialog by remember { mutableStateOf(false) }
    var pendingRenameName by remember { mutableStateOf("") }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FilterResultTopAppBar(
                filterValue = filterValue,
                showMenu = showMenu,
                isRenaming = isRenaming,
                renameName = renameName,
                suggestions = suggestionList,
                onRenameNameChange = { renameName = it },
                onCancelRename = { isRenaming = false },
                onSaveRename = {
                    val newName = renameName.text.trim()
                    if (newName.isNotBlank()) {
                        if (suggestionList.contains(newName) && newName != filterValue) {
                            pendingRenameName = newName
                            showMergeWarningDialog = true
                        } else {
                            onRenameFilterItem(newName)
                            isRenaming = false
                        }
                    } else {
                        isRenaming = false
                    }
                },
                onNavigateBack = onNavigateBack,
                onFilterClick = { showFilterSheet = true },
                onMenuToggle = { showMenu = !showMenu },
                onMenuDismiss = { showMenu = false },
                onRenameClick = {
                    showMenu = false
                    renameName = TextFieldValue(
                        text = filterValue,
                        selection = TextRange(filterValue.length)
                    )
                    isRenaming = true
                },
                onDeleteClick = {
                    showMenu = false
                    showDeleteConfirm = true
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        FilterResultBookContent(
            books = books,
            layoutMode = uiState.bookPreferences.layoutMode,
            innerPadding = innerPadding,
            onNavigateToReader = onNavigateToReader,
            onBookLongClick = { selectedBookContext = it to null },
            scrollKey = Triple(
                uiState.bookPreferences.sortType,
                uiState.bookPreferences.isAscending,
                uiState.bookPreferences.selectedStatus
            )
        )
    }

    FilterResultDialogsAndSheets(
        filterType = filterType,
        filterValue = filterValue,
        uiState = uiState,
        allBooks = allBooks,
        selectedBookContext = selectedBookContext,
        showFilterSheet = showFilterSheet,
        showDeleteConfirm = showDeleteConfirm,
        onFilterDismiss = { showFilterSheet = false },
        onLayoutModeChange = onLayoutModeChange,
        onSortTypeChange = onSortTypeChange,
        onStatusToggle = onStatusToggle,
        onDeleteDismiss = { showDeleteConfirm = false },
        onDeleteConfirm = {
            showDeleteConfirm = false
            onDeleteFilterItem()
        },
        onBookMenuDismiss = { selectedBookContext = null },
        onNavigateToBookInfo = onNavigateToBookInfo,
        onToggleArchive = onToggleArchive,
        onToggleReadStatus = onToggleReadStatus,
        onRemoveFromShelf = onRemoveFromShelf,
        onNavigateToAddToShelf = onNavigateToAddToShelf,
        onDeleteBook = onDeleteBook
    )

    if (showMergeWarningDialog) {
        AlertDialog(
            onDismissRequest = { showMergeWarningDialog = false },
            title = { Text(stringResource(R.string.action_rename)) },
            text = { Text(stringResource(R.string.library_warning_merge)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMergeWarningDialog = false
                        onRenameFilterItem(pendingRenameName)
                        isRenaming = false
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeWarningDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterResultTopAppBar(
    filterValue: String,
    showMenu: Boolean,
    isRenaming: Boolean,
    renameName: TextFieldValue,
    suggestions: List<String>,
    onRenameNameChange: (TextFieldValue) -> Unit,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onNavigateBack: () -> Unit,
    onFilterClick: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    var isSuggestionsExpanded by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(renameName.text, suggestions) {
        if (renameName.text.isBlank()) emptyList()
        else suggestions.filter {
            it.contains(
                renameName.text,
                ignoreCase = true
            ) && it != renameName.text
        }.take(5)
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isRenaming) {
        if (isRenaming) {
            focusRequester.requestFocus()
        }
    }

    Box(modifier = modifier) {
        LibraryTopAppBar(
            onBack = onNavigateBack,
            title = {
                if (isRenaming) {
                    BasicTextField(
                        value = renameName,
                        onValueChange = {
                            onRenameNameChange(it)
                            isSuggestionsExpanded = true
                        },
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else {
                    Text(
                        text = filterValue,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                val rotation by animateFloatAsState(
                    targetValue = if (isRenaming) 90f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "navigationIconRotation"
                )
                val navEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                FilledTonalIconButton(
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    onClick = {
                        if (isRenaming) {
                            onCancelRename()
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
                            targetState = isRenaming,
                            transitionSpec = {
                                fadeIn(animationSpec = navEffectsSpec) togetherWith
                                        fadeOut(animationSpec = navEffectsSpec)
                            },
                            label = "navigationIconContent"
                        ) { targetIsRenaming ->
                            if (targetIsRenaming) {
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
                val density = LocalDensity.current
                val slideOffsetPx = remember(density) { with(density) { 20.dp.roundToPx() } }
                val actionsSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
                val actionsEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                AnimatedContent(
                    targetState = isRenaming,
                    transitionSpec = {
                        if (targetState) {
                            // Enter Rename Mode: Checkmark fades in, normal actions fade out and slide right by 20dp
                            fadeIn(animationSpec = actionsEffectsSpec) togetherWith
                                    fadeOut(animationSpec = actionsEffectsSpec) + slideOutHorizontally(
                                targetOffsetX = { slideOffsetPx },
                                animationSpec = actionsSpatialSpec
                            )
                        } else {
                            // Exit Rename Mode: Normal actions fade in and slide in from right by 20dp, checkmark fades out
                            fadeIn(animationSpec = actionsEffectsSpec) + slideInHorizontally(
                                initialOffsetX = { slideOffsetPx },
                                animationSpec = actionsSpatialSpec
                            ) togetherWith
                                    fadeOut(animationSpec = actionsEffectsSpec)
                        }
                    },
                    label = "actionsContent"
                ) { targetIsRenaming ->
                    if (targetIsRenaming) {
                        FilledIconButton(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(
                                    IconButtonDefaults.smallContainerSize(
                                        widthOption = IconButtonDefaults.IconButtonWidthOption.Wide
                                    )
                                ),
                            shapes = IconButtonDefaults.shapes(),
                            onClick = onSaveRename
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
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = onFilterClick
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Tune,
                                    contentDescription = stringResource(R.string.action_filter)
                                )
                            }
                            Box {
                                IconButton(onClick = onMenuToggle) {
                                    Icon(
                                        MaterialSymbols.Outlined.More_vert,
                                        contentDescription = stringResource(R.string.action_more)
                                    )
                                }
                                DropdownMenuPopup(
                                    expanded = showMenu,
                                    onDismissRequest = onMenuDismiss
                                ) {
                                    val groupInteractionSource =
                                        remember { MutableInteractionSource() }
                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShape(0, 1),
                                        interactionSource = groupInteractionSource
                                    ) {
                                        DropdownMenuItem(
                                            selected = false,
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            shapes = MenuDefaults.itemShape(0, 2),
                                            leadingIcon = {
                                                Icon(
                                                    MaterialSymbols.Outlined.Edit,
                                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = onRenameClick
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
                                            onClick = onDeleteClick
                                        )
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
            scrollBehavior = scrollBehavior
        )

        if (isRenaming && filteredSuggestions.isNotEmpty() && isSuggestionsExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
            ) {
                DropdownMenuPopup(
                    expanded = true,
                    onDismissRequest = { isSuggestionsExpanded = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    val groupInteractionSource = remember { MutableInteractionSource() }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 1),
                        interactionSource = groupInteractionSource
                    ) {
                        filteredSuggestions.forEachIndexed { index, suggestion ->
                            DropdownMenuItem(
                                selected = false,
                                text = { Text(suggestion) },
                                shapes = MenuDefaults.itemShape(index, filteredSuggestions.size),
                                onClick = {
                                    onRenameNameChange(
                                        TextFieldValue(
                                            text = suggestion,
                                            selection = TextRange(suggestion.length)
                                        )
                                    )
                                    isSuggestionsExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterResultBookContent(
    books: List<Book>,
    layoutMode: com.example.readerapp.ui.features.library.LayoutMode,
    innerPadding: PaddingValues,
    onNavigateToReader: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollKey: Any? = null
) {
    Box(
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {
        if (books.isEmpty()) {
            Text(
                stringResource(R.string.library_empty_books),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
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
    }
}

@Composable
private fun FilterResultDialogsAndSheets(
    filterType: String,
    filterValue: String,
    uiState: FilterResultUiState,
    allBooks: List<Book>,
    selectedBookContext: Pair<String, String?>?,
    showFilterSheet: Boolean,
    showDeleteConfirm: Boolean,
    onFilterDismiss: () -> Unit,
    onLayoutModeChange: (com.example.readerapp.ui.features.library.LayoutMode) -> Unit,
    onSortTypeChange: (com.example.readerapp.ui.features.library.SortType) -> Unit,
    onStatusToggle: (com.example.readerapp.ui.features.library.StatusFilter) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onBookMenuDismiss: () -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onToggleReadStatus: (String) -> Unit,
    onRemoveFromShelf: (String, String) -> Unit,
    onNavigateToAddToShelf: (String) -> Unit,
    onDeleteBook: (String) -> Unit
) {
    if (showFilterSheet) {
        FilterResultBottomSheet(
            preferences = uiState.bookPreferences,
            onLayoutModeChange = onLayoutModeChange,
            onSortTypeChange = onSortTypeChange,
            onStatusToggle = onStatusToggle,
            onDismiss = onFilterDismiss
        )
    }

    selectedBookContext?.let { context ->
        val bookId = context.first
        val contextShelfId = context.second
        BookContextMenu(
            bookId = bookId,
            shelfId = contextShelfId,
            allBooks = allBooks,
            onNavigateToBookInfo = onNavigateToBookInfo,
            onToggleArchive = { onToggleArchive(bookId) },
            onToggleReadStatus = { onToggleReadStatus(bookId) },
            onRemoveFromShelf = {
                contextShelfId?.let {
                    onRemoveFromShelf(it, bookId)
                }
            },
            onAddToShelf = onNavigateToAddToShelf,
            onDeleteBook = { onDeleteBook(bookId) },
            onDismiss = onBookMenuDismiss
        )
    }

    if (showDeleteConfirm) {
        val titleType =
            if (filterType == "author") stringResource(R.string.library_sort_author) else stringResource(
                R.string.library_sort_label
            )
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.library_delete_item_title, titleType)) },
            text = { Text(stringResource(R.string.library_delete_item_message, filterValue)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            })
    }
}

