package com.javierreansyah.pinecone.ui.features.library.filters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDropdownMenuItem
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Select
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconButton
import com.javierreansyah.pinecone.ui.components.EditingActions
import com.javierreansyah.pinecone.ui.components.EditingNavigationIcon
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBarTitle
import com.javierreansyah.pinecone.ui.components.LocalTopAppBarCollapsedRow
import com.javierreansyah.pinecone.ui.components.TopAppBarActionGroup
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.LibrarySelection
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.components.FilterResultBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.SelectedBooksBar
import com.javierreansyah.pinecone.ui.features.library.components.book.BookCollection
import com.javierreansyah.pinecone.ui.features.library.components.book.BookContextMenu
import com.javierreansyah.pinecone.ui.features.library.deleteCollectionTitle
import com.javierreansyah.pinecone.ui.features.library.fileFormat
import com.javierreansyah.pinecone.ui.features.library.inNamedCollection
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding

@Composable
fun FilterResultScreen(
    filterType: String,
    filterValue: String,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToMerged: (String) -> Unit = {},
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    viewModel: FilterResultViewModel = hiltViewModel(),
) {
    val preferences by viewModel.filters.preferences.collectAsStateWithLifecycle()
    com.javierreansyah.pinecone.ui.components.MutationErrorDialog(viewModel.mutationErrors)
    val spaceScopedBooks by viewModel.allBooks.collectAsStateWithLifecycle()
    val allBooksAcrossSpaces by viewModel.allBooksAcrossSpaces.collectAsStateWithLifecycle()
    val actionBooks by viewModel.actionBooks.collectAsStateWithLifecycle()
    val allBooks = if (filterType == "space") allBooksAcrossSpaces else spaceScopedBooks

    val allAuthors by viewModel.allAuthors.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val allSpaces by viewModel.allSpaces.collectAsStateWithLifecycle()

    val suggestionList = remember(filterType, allAuthors, allTags, allSpaces) {
        when (filterType) {
            "author" -> allAuthors.map { (_, itName) -> itName }
            "space" -> allSpaces.map { (_, itName2, _) -> itName2 }
            else -> allTags.map { (_, itName3) -> itName3 }
        }
    }

    androidx.compose.runtime.LaunchedEffect(filterType, filterValue) {
        viewModel.selectCollection(
            filterType,
            filterValue
        )
    }
    val unfilteredBooks by viewModel.unfilteredBooks.collectAsStateWithLifecycle()
    val books by viewModel.filteredBooks.collectAsStateWithLifecycle()

    FilterResultContent(
        filterType = filterType,
        filterValue = filterValue,
        preferences = preferences,
        allBooks = allBooks,
        globalBooks = actionBooks,
        availableFormats = unfilteredBooks.mapTo(mutableSetOf()) { it.fileFormat() },
        books = books,
        suggestionList = suggestionList,
        onNavigateBack = onNavigateBack,
        onNavigateToReader = onNavigateToReader,
        onNavigateToBookInfo = onNavigateToBookInfo,
        onLayoutModeChange = viewModel.filters::onLayoutModeChange,
        onSortTypeChange = viewModel.filters::onSortTypeChange,
        onStatusToggle = viewModel.filters::toggleStatusFilter,
        onFileFilterToggle = viewModel.filters::toggleFileFilter,
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
        onToggleArchive = viewModel.bookActions::toggleArchive,
        onToggleReadStatus = viewModel.bookActions::toggleReadStatus,
        onMarkBooksReadStatus = viewModel.bookActions::markBooksReadStatus,
        onArchiveBooks = viewModel.bookActions::archiveBooks,
        onDeleteBooks = viewModel.bookActions::deleteBooks,
        onNavigateToOrganize = onNavigateToOrganize,
        onDeleteBook = viewModel.bookActions::deleteBook,
    )
}

@Composable
private fun FilterResultContent(
    filterType: String,
    filterValue: String,
    preferences: FilterSortPreferences,
    allBooks: List<Book>,
    globalBooks: List<Book>,
    availableFormats: Set<FileFilter>,
    books: List<Book>,
    suggestionList: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onFileFilterToggle: (FileFilter) -> Unit,
    onDeleteFilterItem: () -> Unit,
    onRenameFilterItem: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onToggleReadStatus: (String) -> Unit,
    onMarkBooksReadStatus: (Collection<String>, Boolean) -> Unit,
    onArchiveBooks: (Collection<String>) -> Unit,
    onDeleteBooks: (Collection<String>) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    onDeleteBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBookContext by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var showMenu by remember { mutableStateOf(value = false) }
    var showFilterSheet by remember { mutableStateOf(value = false) }
    var showDeleteConfirm by remember { mutableStateOf(value = false) }
    var isRenaming by remember { mutableStateOf(value = false) }
    val renameNameState = rememberTextFieldState()
    var showMergeWarningDialog by remember { mutableStateOf(value = false) }
    var pendingRenameName by remember { mutableStateOf("") }
    val selection = remember { LibrarySelection() }
    var selectedBooks by selection::ids
    var isInMultiSelectMode by selection::active

    val exitMultiSelect = selection::close

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    BackHandler(enabled = isInMultiSelectMode, onBack = exitMultiSelect)

    Scaffold(
        modifier = if (isInMultiSelectMode) modifier else modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FilterResultTopBar(
                filterValue = filterValue,
                isInMultiSelectMode = isInMultiSelectMode,
                showMenu = showMenu,
                isRenaming = isRenaming,
                renameNameState = renameNameState,
                suggestions = suggestionList,
                scrollBehavior = scrollBehavior,
                onCancelRename = { isRenaming = false },
                onSaveRename = {
                    val newName = renameNameState.text.toString().trim()
                    if (newName.isNotBlank()) {
                        if (newName in suggestionList && newName != filterValue) {
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
                    renameNameState.setTextAndPlaceCursorAtEnd(filterValue)
                    isRenaming = true
                },
                onDeleteClick = {
                    showMenu = false
                    showDeleteConfirm = true
                },
                onEnterMultiSelect = { isInMultiSelectMode = true },
                content = {
                    SelectedBooksBar(
                        selection = selection,
                        books = books,
                        allBooks = allBooks,
                        onMarkRead = onMarkBooksReadStatus,
                        onArchive = onArchiveBooks,
                        onDelete = onDeleteBooks,
                        onInfo = onNavigateToBookInfo,
                        onOrganize = onNavigateToOrganize,
                    )
                },
            )
        },
    ) { innerPadding ->
        FilterResultBookContent(
            books = books,
            layoutMode = preferences.layoutMode,
            innerPadding = innerPadding,
            selectedBooks = selectedBooks,
            isInMultiSelectMode = isInMultiSelectMode,
            onBookClick = { bookId ->
                if (isInMultiSelectMode) {
                    selection.toggle(bookId)
                } else {
                    onNavigateToReader(bookId)
                }
            },
            onBookLongClick = { selectedBookContext = it to null },
            scrollKey = Triple(
                preferences.sortType,
                preferences.isAscending,
                preferences.selectedStatus,
            ),
        )
    }

    FilterResultDialogsAndSheets(
        actionWording = globalBooks.inNamedCollection(filterType, filterValue).publicationWording(),
        filterType = filterType,
        filterValue = filterValue,
        preferences = preferences,
        allBooks = allBooks,
        availableFormats = availableFormats,
        selectedBookContext = selectedBookContext,
        showFilterSheet = showFilterSheet,
        showDeleteConfirm = showDeleteConfirm,
        onFilterDismiss = { showFilterSheet = false },
        onLayoutModeChange = onLayoutModeChange,
        onSortTypeChange = onSortTypeChange,
        onStatusToggle = onStatusToggle,
        onFileFilterToggle = onFileFilterToggle,
        onDeleteDismiss = { showDeleteConfirm = false },
        onDeleteConfirm = {
            showDeleteConfirm = false
            onDeleteFilterItem()
        },
        onBookMenuDismiss = { selectedBookContext = null },
        onNavigateToBookInfo = onNavigateToBookInfo,
        onToggleArchive = onToggleArchive,
        onToggleReadStatus = onToggleReadStatus,
        onNavigateToOrganize = onNavigateToOrganize,
        onDeleteBook = onDeleteBook,
    ) { bookId ->
        selection.enter(setOf(bookId))
    }

    if (showMergeWarningDialog) {
        AlertDialog(
            onDismissRequest = { showMergeWarningDialog = false },
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                Text(
                    stringResource(
                        (
                                globalBooks.inNamedCollection(filterType, filterValue) +
                                        globalBooks.inNamedCollection(filterType, pendingRenameName)
                                ).publicationWording().mergeWarning
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMergeWarningDialog = false
                        onRenameFilterItem(pendingRenameName)
                        isRenaming = false
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeWarningDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FilterResultTopBar(
    filterValue: String,
    isInMultiSelectMode: Boolean,
    showMenu: Boolean,
    isRenaming: Boolean,
    renameNameState: TextFieldState,
    suggestions: List<String>,
    scrollBehavior: TopAppBarScrollBehavior,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onNavigateBack: () -> Unit,
    onFilterClick: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    MultiSelectTopBarTransition(
        isMultiSelect = isInMultiSelectMode,
        multiSelectBar = content,
    ) {
        FilterResultDefaultTopAppBar(
            filterValue = filterValue,
            showMenu = showMenu,
            isRenaming = isRenaming,
            renameNameState = renameNameState,
            suggestions = suggestions,
            onCancelRename = onCancelRename,
            onSaveRename = onSaveRename,
            onNavigateBack = onNavigateBack,
            onFilterClick = onFilterClick,
            onMenuToggle = onMenuToggle,
            onMenuDismiss = onMenuDismiss,
            onRenameClick = onRenameClick,
            onDeleteClick = onDeleteClick,
            onEnterMultiSelect = onEnterMultiSelect,
            scrollBehavior = scrollBehavior,
            modifier = modifier,
        )
    }
}

@Composable
private fun FilterResultDefaultTopAppBar(
    filterValue: String,
    showMenu: Boolean,
    isRenaming: Boolean,
    renameNameState: TextFieldState,
    suggestions: List<String>,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onNavigateBack: () -> Unit,
    onFilterClick: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    var isSuggestionsExpanded by remember { mutableStateOf(value = false) }
    var ignoreNextTextChange by remember { mutableStateOf(value = false) }
    val currentRenameText = renameNameState.text.toString()
    val filteredSuggestions = remember(currentRenameText, suggestions) {
        if (currentRenameText.isBlank()) emptyList()
        else suggestions.filter {
            it.contains(
                currentRenameText,
                ignoreCase = true,
            ) && it != currentRenameText
        }.take(5)
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isRenaming) {
        if (isRenaming) {
            isSuggestionsExpanded = false
            focusRequester.requestFocus()
            var isFirst = true
            snapshotFlow { renameNameState.text.toString() }.collect {
                if (isFirst) {
                    isFirst = false
                } else if (ignoreNextTextChange) {
                    ignoreNextTextChange = false
                } else {
                    isSuggestionsExpanded = true
                }
            }
        } else {
            isSuggestionsExpanded = false
        }
    }

    Box(modifier = modifier) {
        LibraryTopAppBar(
            onBack = onNavigateBack,
            title = {
                val isCollapsed = LocalTopAppBarCollapsedRow.current
                if (isRenaming) {
                    BasicTextField(
                        state = renameNameState,
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        lineLimits = if (isCollapsed) {
                            TextFieldLineLimits.SingleLine
                        } else {
                            TextFieldLineLimits.MultiLine(maxHeightInLines = 2)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                } else {
                    LibraryTopAppBarTitle(text = filterValue)
                }
            },
            navigationIcon = {
                EditingNavigationIcon(isRenaming, onNavigateBack, onCancelRename)
            },
            actions = {
                EditingActions(isRenaming, onSaveRename) {
                    TopAppBarActionGroup {
                        ConnectedGroupIconButton(
                            onClick = onFilterClick,
                            buttonSize = 40.dp,
                        ) {
                            Icon(
                                MaterialSymbols.Outlined.Tune,
                                contentDescription = stringResource(R.string.action_filter),
                            )
                        }
                        Box {
                            ConnectedGroupIconButton(
                                onClick = onMenuToggle,
                                buttonSize = 40.dp,
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.More_vert,
                                    contentDescription = stringResource(R.string.action_more),
                                )
                            }
                            DropdownMenuPopup(
                                expanded = showMenu,
                                onDismissRequest = onMenuDismiss,
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
                                        shapes = MenuDefaults.itemShape(0, 3),
                                        leadingIcon = {
                                            Icon(
                                                MaterialSymbols.Outlined.Select,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            onEnterMultiSelect()
                                            onMenuDismiss()
                                        },
                                    )
                                    SelectableDropdownMenuItem(
                                        selected = false,
                                        text = { Text(stringResource(R.string.action_rename)) },
                                        shapes = MenuDefaults.itemShape(1, 3),
                                        leadingIcon = {
                                            Icon(
                                                MaterialSymbols.Outlined.Edit,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = onRenameClick,
                                    )
                                    SelectableDropdownMenuItem(
                                        selected = false,
                                        text = {
                                            Text(
                                                stringResource(R.string.action_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        shapes = MenuDefaults.itemShape(2, 3),
                                        leadingIcon = {
                                            Icon(
                                                MaterialSymbols.Outlined.Delete,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = onDeleteClick,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )

        if (isRenaming && filteredSuggestions.isNotEmpty() && isSuggestionsExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart),
            ) {
                DropdownMenuPopup(
                    expanded = true,
                    onDismissRequest = { isSuggestionsExpanded = false },
                    properties = PopupProperties(focusable = false),
                ) {
                    val groupInteractionSource = remember { MutableInteractionSource() }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 1),
                        interactionSource = groupInteractionSource,
                    ) {
                        filteredSuggestions.forEachIndexed { index, suggestion ->
                            SelectableDropdownMenuItem(
                                selected = false,
                                text = { Text(suggestion) },
                                shapes = MenuDefaults.itemShape(index, filteredSuggestions.size),
                                onClick = {
                                    ignoreNextTextChange = true
                                    renameNameState.setTextAndPlaceCursorAtEnd(suggestion)
                                    isSuggestionsExpanded = false
                                },
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
    layoutMode: LayoutMode,
    innerPadding: PaddingValues,
    selectedBooks: Set<String>,
    isInMultiSelectMode: Boolean,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollKey: Any? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier = modifier
            .padding(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
            )
            .fillMaxSize(),
    ) {
        if (books.isEmpty()) {
            Text(
                stringResource(R.string.library_empty_items),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding(),
                    )
                    .padding(16.dp),
            )
        } else {
            BookCollection(
                books = books,
                layoutMode = layoutMode,
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick,
                selectedBooks = selectedBooks,
                isInMultiSelectMode = isInMultiSelectMode,
                scrollKey = scrollKey,
                topContentPadding = innerPadding.calculateTopPadding(),
                bottomContentPadding = innerPadding.calculateBottomPadding() + LocalAudioPlayerBottomPadding.current,
            )
        }
    }
}

@Composable
private fun FilterResultDialogsAndSheets(
    actionWording: com.javierreansyah.pinecone.ui.features.library.PublicationWording,
    filterType: String,
    filterValue: String,
    preferences: FilterSortPreferences,
    allBooks: List<Book>,
    availableFormats: Set<FileFilter>,
    selectedBookContext: Pair<String, String?>?,
    showFilterSheet: Boolean,
    showDeleteConfirm: Boolean,
    onFilterDismiss: () -> Unit,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onFileFilterToggle: (FileFilter) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onBookMenuDismiss: () -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onToggleReadStatus: (String) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    onDeleteBook: (String) -> Unit,
    onEnterMultiSelect: (String) -> Unit,
) {
    if (showFilterSheet) {
        FilterResultBottomSheet(
            preferences = preferences,
            availableFormats = availableFormats,
            onLayoutModeChange = onLayoutModeChange,
            onSortTypeChange = onSortTypeChange,
            onStatusToggle = onStatusToggle,
            onFileFilterToggle = onFileFilterToggle,
            onDismiss = onFilterDismiss,
        )
    }

    selectedBookContext?.let { (contextFirst, _) ->
        val bookId = contextFirst
        BookContextMenu(
            bookId = bookId,
            allBooks = allBooks,
            onNavigateToBookInfo = onNavigateToBookInfo,
            onToggleArchive = { onToggleArchive(bookId) },
            onToggleReadStatus = { onToggleReadStatus(bookId) },
            onOrganize = { onNavigateToOrganize(setOf(bookId)) },
            onDeleteBook = { onDeleteBook(bookId) },
            onEnterMultiSelect = { onEnterMultiSelect(bookId) },
            onDismiss = onBookMenuDismiss,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(deleteCollectionTitle(filterType))) },
            text = { Text(stringResource(actionWording.deleteLabelMessage, filterValue)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
