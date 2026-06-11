package com.example.readerapp.ui.features.library.filters

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.R
import com.example.readerapp.ui.components.SegmentedLazyColumn
import com.example.readerapp.ui.features.library.components.FilterItemSortBottomSheet
import com.example.readerapp.ui.features.library.components.FilterItemSortType
import com.example.readerapp.ui.features.library.components.RenameFilterDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllFilterItemsScreen(
    filterType: String, // "author" or "tag"
    onNavigateBack: () -> Unit, onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: FilterCategoryViewModel = viewModel(factory = object :
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application) {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FilterCategoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return FilterCategoryViewModel(
                    context.applicationContext as Application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    })

    val itemsWithCounts by if (filterType == "author") {
        viewModel.authorsWithCounts.collectAsState()
    } else {
        viewModel.tagsWithCounts.collectAsState()
    }

    val allAuthors by viewModel.allAuthors.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    val suggestionList = remember(filterType, allAuthors, allTags) {
        if (filterType == "author") allAuthors.map { it.name }
        else allTags.map { it.name }
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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AllFilterItemsContent(
    filterType: String,
    items: List<Pair<String, Int>>,
    suggestions: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onDeleteConfirm: (String) -> Unit,
    onRenameConfirm: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedItemForMenu by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<String?>(null) }
    var itemToRename by remember { mutableStateOf<String?>(null) }

    var sortType by remember { mutableStateOf(FilterItemSortType.Label) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortSheet by remember { mutableStateOf(false) }

    val sortedItems = remember(items, sortType, sortAscending) {
        items.sortedWith { a, b ->
            when (sortType) {
                FilterItemSortType.Label -> {
                    val comp = a.first.lowercase().compareTo(b.first.lowercase())
                    if (sortAscending) comp else -comp
                }

                FilterItemSortType.Size -> {
                    val comp = a.second.compareTo(b.second)
                    if (comp != 0) {
                        if (sortAscending) comp else -comp
                    } else {
                        a.first.lowercase().compareTo(b.first.lowercase())
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AllFilterItemsTopAppBar(
                filterType = filterType,
                onNavigateBack = onNavigateBack,
                onSortClick = { showSortSheet = true },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            AllFilterItemsEmptyState(innerPadding = innerPadding)
        } else {
            AllFilterItemsList(
                sortedItems = sortedItems,
                selectedItemForMenu = selectedItemForMenu,
                innerPadding = innerPadding,
                onNavigateToDetail = onNavigateToDetail,
                onMoreClick = { selectedItemForMenu = it },
                onMenuDismiss = { selectedItemForMenu = null },
                onRenameClick = { name ->
                    selectedItemForMenu = null
                    itemToRename = name
                },
                onDeleteClick = { name ->
                    selectedItemForMenu = null
                    itemToDelete = name
                }
            )
        }

        AllFilterItemsDialogs(
            filterType = filterType,
            itemToDelete = itemToDelete,
            itemToRename = itemToRename,
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
                onRenameConfirm(oldName, newName)
            },
            onSortTypeChange = { newType ->
                if (sortType == newType) {
                    sortAscending = !sortAscending
                } else {
                    sortType = newType
                    sortAscending = true
                }
            },
            onSortDismiss = { showSortSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AllFilterItemsTopAppBar(
    filterType: String,
    onNavigateBack: () -> Unit,
    onSortClick: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    LargeFlexibleTopAppBar(
        title = {
            Text(
                if (filterType == "author") stringResource(R.string.library_authors_title) else stringResource(
                    R.string.library_tags_title
                )
            )
        },
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
        actions = {
            IconButton(onClick = onSortClick) {
                Icon(
                    MaterialSymbols.Outlined.Tune,
                    contentDescription = stringResource(R.string.action_sort)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}

@Composable
private fun AllFilterItemsEmptyState(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {
        Text(
            stringResource(R.string.library_empty_items),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun AllFilterItemsList(
    sortedItems: List<Pair<String, Int>>,
    selectedItemForMenu: String?,
    innerPadding: PaddingValues,
    onNavigateToDetail: (String) -> Unit,
    onMoreClick: (String) -> Unit,
    onMenuDismiss: () -> Unit,
    onRenameClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedLazyColumn(
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        sortedItems.forEach { item ->
            item(
                onClick = { onNavigateToDetail(item.first) },
                content = { Text(item.first) },
                supportingContent = {
                    Text(
                        pluralStringResource(
                            R.plurals.library_books_count, item.second, item.second
                        )
                    )
                },
                trailingContent = {
                    AllFilterItemsItemActions(
                        isMenuExpanded = selectedItemForMenu == item.first,
                        onMoreClick = { onMoreClick(item.first) },
                        onMenuDismiss = onMenuDismiss,
                        onRenameClick = { onRenameClick(item.first) },
                        onDeleteClick = { onDeleteClick(item.first) }
                    )
                }
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
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(onClick = onMoreClick) {
            Icon(
                MaterialSymbols.Outlined.More_vert,
                contentDescription = stringResource(R.string.action_more)
            )
        }
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = onMenuDismiss
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = onRenameClick
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = onDeleteClick
            )
        }
    }
}

@Composable
private fun AllFilterItemsDialogs(
    filterType: String,
    itemToDelete: String?,
    itemToRename: String?,
    suggestionList: List<String>,
    showSortSheet: Boolean,
    sortType: FilterItemSortType,
    sortAscending: Boolean,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: (String) -> Unit,
    onRenameDismiss: () -> Unit,
    onRenameConfirm: (String, String) -> Unit,
    onSortTypeChange: (FilterItemSortType) -> Unit,
    onSortDismiss: () -> Unit
) {
    itemToDelete?.let { name ->
        val titleType =
            if (filterType == "author") stringResource(R.string.library_sort_author) else stringResource(
                R.string.library_sort_label
            )
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.library_delete_item_title, titleType)) },
            text = { Text(stringResource(R.string.library_delete_item_message, name)) },
            confirmButton = {
                TextButton(onClick = { onDeleteConfirm(name) }) {
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

    itemToRename?.let { oldName ->
        RenameFilterDialog(
            initialName = oldName,
            suggestions = suggestionList,
            onDismiss = onRenameDismiss,
            onConfirm = { newName -> onRenameConfirm(oldName, newName) }
        )
    }

    if (showSortSheet) {
        FilterItemSortBottomSheet(
            currentSortType = sortType,
            isAscending = sortAscending,
            onSortTypeChange = onSortTypeChange,
            onDismiss = onSortDismiss
        )
    }
}
