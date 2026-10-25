package com.javierreansyah.pinecone.ui.features.library.filters

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Forest
import com.composables.icons.materialsymbols.outlined.Label
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Person
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.SegmentedLazyColumn
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.combinedWording
import com.javierreansyah.pinecone.ui.features.library.components.FilterItemSortBottomSheet
import com.javierreansyah.pinecone.ui.features.library.components.FilterItemSortType
import com.javierreansyah.pinecone.ui.features.library.components.FilterMultiSelectAppBar
import com.javierreansyah.pinecone.ui.features.library.components.MultiSelectTopBarTransition
import com.javierreansyah.pinecone.ui.features.library.components.RenameFilterDialog
import com.javierreansyah.pinecone.ui.features.library.deleteCollectionTitle
import com.javierreansyah.pinecone.ui.features.library.publicationCountLabel
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding

@Composable
fun AllFilterItemsScreen(
    filterType: String,
    onNavigateBack: () -> Unit, onNavigateToDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: FilterCategoryViewModel = viewModel(
        factory = object :
            ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application) {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(FilterCategoryViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST") return FilterCategoryViewModel(
                        context.applicationContext as Application,
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        },
    )

    val itemsWithCounts by when (filterType) {
        "author" -> viewModel.authorsWithCounts.collectAsState()
        "space" -> viewModel.spacesWithCounts.collectAsState()
        else -> viewModel.tagsWithCounts.collectAsState()
    }

    val allAuthors by viewModel.allAuthors.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val allSpaces by viewModel.allSpaces.collectAsState()

    val suggestionList = remember(filterType, allAuthors, allTags, allSpaces) {
        when (filterType) {
            "author" -> allAuthors.map { (_, itName) -> itName }
            "space" -> allSpaces.map { (_, itName2, _) -> itName2 }
            else -> allTags.map { (_, itName3) -> itName3 }
        }
    }

    AllFilterItemsContent(
        filterType = filterType,
        items = itemsWithCounts,
        suggestions = suggestionList,
        onNavigateBack = onNavigateBack,
        onNavigateToDetail = onNavigateToDetail,
        onDeleteConfirm = { name ->
            viewModel.deleteFilterItem(filterType, name) {}
        },
        onRenameConfirm = { oldName, newName ->
            viewModel.renameFilterItem(filterType, oldName, newName) {}
        },
        onRenameItemsConfirm = { oldNames, newName ->
            viewModel.renameFilterItems(filterType, oldNames, newName) {}
        },
    )
}

@Composable
private fun AllFilterItemsContent(
    filterType: String,
    items: List<FilterCollectionSummary>,
    suggestions: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onDeleteConfirm: (String) -> Unit,
    onRenameConfirm: (String, String) -> Unit,
    onRenameItemsConfirm: (List<String>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedItemForMenu by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<String?>(null) }
    var itemToRename by remember { mutableStateOf<String?>(null) }
    var isMultiRename by remember { mutableStateOf(value = false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var isInMultiSelectMode by remember { mutableStateOf(value = false) }
    val wordingByName = items.associate { it.name to it.actionWording }

    BackHandler(enabled = isInMultiSelectMode) {
        selectedItems = emptySet()
        isInMultiSelectMode = false
    }

    var sortType by remember { mutableStateOf(FilterItemSortType.Label) }
    var sortAscending by remember { mutableStateOf(value = true) }
    var showSortSheet by remember { mutableStateOf(value = false) }

    val sortedItems = remember(items, sortType, sortAscending) {
        items.sortedWith { a, b ->
            when (sortType) {
                FilterItemSortType.Label -> {
                    val comp = a.name.lowercase().compareTo(b.name.lowercase())
                    if (sortAscending) comp else -comp
                }

                FilterItemSortType.Size -> {
                    val comp = a.count.compareTo(b.count)
                    if (comp != 0) {
                        if (sortAscending) comp else -comp
                    } else {
                        a.name.lowercase().compareTo(b.name.lowercase())
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isEmpty = items.isEmpty()

    Scaffold(
        modifier = if (isEmpty) modifier else modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MultiSelectTopBarTransition(
                isMultiSelect = isInMultiSelectMode,
                multiSelectBar = {
                    FilterMultiSelectAppBar(
                        wording = selectedItems.map {
                            wordingByName[it] ?: PublicationWording.Items
                        }.combinedWording(),
                        selectedCount = selectedItems.size,
                        isAllSelected = selectedItems.size == sortedItems.size,
                        onCloseMultiSelect = {
                            selectedItems = emptySet()
                            isInMultiSelectMode = false
                        },
                        onClearSelection = { selectedItems = emptySet() },
                        onSelectAll = {
                            selectedItems =
                                sortedItems.map { it.name }.toSet()
                        },
                        onRenameClick = {
                            itemToRename = selectedItems.firstOrNull() ?: ""
                            isMultiRename = selectedItems.size > 1
                        },
                        onDelete = {
                            selectedItems.forEach { name ->
                                onDeleteConfirm(name)
                            }
                            selectedItems = emptySet()
                            isInMultiSelectMode = false
                        },
                    )
                },
            ) {
                AllFilterItemsTopAppBar(
                    filterType = filterType,
                    isEmpty = isEmpty,
                    onNavigateBack = onNavigateBack,
                    onSortClick = { showSortSheet = true },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        if (isEmpty) {
            AllFilterItemsEmptyState(
                filterType = filterType,
                innerPadding = innerPadding,
            )
        } else {
            AllFilterItemsList(
                sortedItems = sortedItems,
                selectedItemForMenu = selectedItemForMenu,
                selectedItems = selectedItems,
                isInMultiSelectMode = isInMultiSelectMode,
                innerPadding = innerPadding,
                onNavigateToDetail = onNavigateToDetail,
                onMoreClick = { selectedItemForMenu = it },
                onMenuDismiss = { selectedItemForMenu = null },
                onRenameClick = { name ->
                    selectedItemForMenu = null
                    itemToRename = name
                    isMultiRename = false
                },
                onDeleteClick = { name ->
                    selectedItemForMenu = null
                    itemToDelete = name
                },
                onToggleSelect = { name ->
                    selectedItems = if (name in selectedItems) {
                        selectedItems - name
                    } else {
                        selectedItems + name
                    }
                },
                onLongClick = { name ->
                    if (!isInMultiSelectMode) {
                        selectedItems = setOf(name)
                        isInMultiSelectMode = true
                    }
                },
            )
        }

        AllFilterItemsDialogs(
            wordingByName = wordingByName,
            filterType = filterType,
            itemToDelete = itemToDelete,
            itemToRename = itemToRename,
            isMultiRename = isMultiRename,
            suggestionList = suggestions,
            showSortSheet = showSortSheet,
            sortType = sortType,
            sortAscending = sortAscending,
            onDeleteDismiss = { itemToDelete = null },
            onDeleteConfirm = { name ->
                itemToDelete = null
                onDeleteConfirm(name)
            },
            onRenameDismiss = { itemToRename = null },
            onRenameConfirm = { oldName, newName ->
                itemToRename = null
                if (isMultiRename) {
                    val items = selectedItems.toList()
                    selectedItems = emptySet()
                    isMultiRename = false
                    isInMultiSelectMode = false
                    onRenameItemsConfirm(items, newName)
                } else {
                    onRenameConfirm(oldName, newName)
                    if (oldName in selectedItems) {
                        selectedItems = emptySet()
                    }
                }
            },
            onSortTypeChange = { newType ->
                if (sortType == newType) {
                    sortAscending = !sortAscending
                } else {
                    sortType = newType
                    sortAscending = true
                }
            },
        ) { showSortSheet = false }
    }
}

@Composable
private fun AllFilterItemsTopAppBar(
    filterType: String,
    isEmpty: Boolean,
    onNavigateBack: () -> Unit,
    onSortClick: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val titleText = when (filterType) {
        "author" -> stringResource(R.string.library_authors_title)
        "space" -> stringResource(R.string.library_spaces_title)
        else -> stringResource(R.string.library_tags_title)
    }

    LibraryTopAppBar(
        title = { Text(titleText) },
        onBack = onNavigateBack,
        isEmpty = isEmpty,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        actions = {
            IconButton(onClick = onSortClick) {
                Icon(
                    MaterialSymbols.Outlined.Tune,
                    contentDescription = stringResource(R.string.action_sort),
                )
            }
        },
    )
}

@Composable
private fun AllFilterItemsEmptyState(
    filterType: String,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val icon = when (filterType) {
        "author" -> MaterialSymbols.Outlined.Person
        "space" -> MaterialSymbols.Outlined.Forest
        else -> MaterialSymbols.Outlined.Label
    }

    val text = when (filterType) {
        "author" -> stringResource(R.string.library_empty_authors)
        "space" -> stringResource(R.string.library_empty_spaces)
        else -> stringResource(R.string.library_empty_tags)
    }

    EmptyState(
        icon = icon,
        text = text,
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(16.dp),
    )
}

@Composable
private fun AllFilterItemsList(
    sortedItems: List<FilterCollectionSummary>,
    selectedItemForMenu: String?,
    selectedItems: Set<String>,
    isInMultiSelectMode: Boolean,
    innerPadding: PaddingValues,
    onNavigateToDetail: (String) -> Unit,
    onMoreClick: (String) -> Unit,
    onMenuDismiss: () -> Unit,
    onRenameClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedLazyColumn(
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 16.dp + LocalAudioPlayerBottomPadding.current),
    ) {
        sortedItems.forEach { (itemFirst, itemSecond, wording, _) ->
            val isSelected = itemFirst in selectedItems
            item(
                selected = isSelected,
                onClick = {
                    if (isInMultiSelectMode) onToggleSelect(itemFirst)
                    else onNavigateToDetail(itemFirst)
                },
                onLongClick = { onLongClick(itemFirst) },
                content = { Text(itemFirst) },
                supportingContent = {
                    Text(
                        publicationCountLabel(itemSecond, wording),
                    )
                },
                trailingContent = {
                    if (isInMultiSelectMode) {
                        Checkbox(
                            checked = itemFirst in selectedItems,
                            onCheckedChange = { onToggleSelect(itemFirst) },
                        )
                    } else {
                        AllFilterItemsItemActions(
                            isMenuExpanded = selectedItemForMenu == itemFirst,
                            onMoreClick = { onMoreClick(itemFirst) },
                            onMenuDismiss = onMenuDismiss,
                            onRenameClick = { onRenameClick(itemFirst) },
                            onDeleteClick = { onDeleteClick(itemFirst) },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AllFilterItemsItemActions(
    isMenuExpanded: Boolean,
    onMoreClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(onClick = onMoreClick) {
            Icon(
                MaterialSymbols.Outlined.More_vert,
                contentDescription = stringResource(R.string.action_more),
            )
        }
        DropdownMenuPopup(
            expanded = isMenuExpanded,
            onDismissRequest = onMenuDismiss,
        ) {
            val groupInteractionSource = remember { MutableInteractionSource() }
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
                interactionSource = groupInteractionSource,
            ) {
                SelectableDropdownMenuItem(
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
                    shapes = MenuDefaults.itemShape(1, 2),
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

@Composable
private fun AllFilterItemsDialogs(
    wordingByName: Map<String, PublicationWording>,
    filterType: String,
    itemToDelete: String?,
    itemToRename: String?,
    isMultiRename: Boolean,
    suggestionList: List<String>,
    showSortSheet: Boolean,
    sortType: FilterItemSortType,
    sortAscending: Boolean,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: (String) -> Unit,
    onRenameDismiss: () -> Unit,
    onRenameConfirm: (String, String) -> Unit,
    onSortTypeChange: (FilterItemSortType) -> Unit,
    onSortDismiss: () -> Unit,
) {
    itemToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(deleteCollectionTitle(filterType))) },
            text = {
                Text(
                    stringResource(
                        (wordingByName[name] ?: PublicationWording.Items).deleteLabelMessage, name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { onDeleteConfirm(name) }) {
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

    itemToRename?.let { oldName ->
        val dialogTitle = when (filterType) {
            "author" -> stringResource(R.string.library_rename_author_title)
            "space" -> stringResource(R.string.library_rename_space_title)
            else -> stringResource(R.string.library_rename_tag_title)
        }
        val fieldLabel = when (filterType) {
            "author" -> stringResource(R.string.library_author_name_label)
            "space" -> stringResource(R.string.library_space_name_label)
            else -> stringResource(R.string.library_tag_name_label)
        }
        RenameFilterDialog(
            wordingByName = wordingByName,
            initialName = oldName,
            suggestions = suggestionList,
            title = dialogTitle,
            label = fieldLabel,
            isMergingMultiple = isMultiRename,
            onDismiss = onRenameDismiss,
        ) { newName -> onRenameConfirm(oldName, newName) }
    }

    if (showSortSheet) {
        FilterItemSortBottomSheet(
            currentSortType = sortType,
            isAscending = sortAscending,
            onSortTypeChange = onSortTypeChange,
            onDismiss = onSortDismiss,
        )
    }
}
