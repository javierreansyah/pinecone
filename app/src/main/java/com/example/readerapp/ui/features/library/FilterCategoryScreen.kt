package com.example.readerapp.ui.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Tune
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.More_vert
import com.example.readerapp.ui.components.SegmentedLazyColumn
import com.example.readerapp.ui.features.library.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllFilterItemsScreen(
    filterType: String, // "author" or "tag"
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = object : ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application) {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return LibraryViewModel(context.applicationContext as android.app.Application, "all_filter_items") as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )

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

    var selectedItemForMenu by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<String?>(null) }
    var itemToRename by remember { mutableStateOf<String?>(null) }

    var sortType by remember { mutableStateOf(FilterItemSortType.Label) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortSheet by remember { mutableStateOf(false) }

    val sortedItems = remember(itemsWithCounts, sortType, sortAscending) {
        itemsWithCounts.sortedWith { a, b ->
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (filterType == "author") "Authors" else "Tags") },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onNavigateBack
                    ) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(MaterialSymbols.Outlined.Tune, contentDescription = "Sort")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (itemsWithCounts.isEmpty()) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                Text("No items found.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        } else {
            SegmentedLazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                sortedItems.forEach { item ->
                    item(
                        onClick = { onNavigateToDetail(item.first) },
                        content = { Text(item.first) },
                        supportingContent = { Text("${item.second} books") },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { selectedItemForMenu = item.first }) {
                                    Icon(
                                        MaterialSymbols.Outlined.More_vert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = selectedItemForMenu == item.first,
                                    onDismissRequest = { selectedItemForMenu = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            selectedItemForMenu = null
                                            itemToRename = item.first
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            selectedItemForMenu = null
                                            itemToDelete = item.first
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        if (itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Delete $filterType") },
                text = { Text("Are you sure you want to delete '${itemToDelete}'? This will remove it from all books.") },
                confirmButton = {
                    TextButton(onClick = {
                        val nameToDelete = itemToDelete!!
                        itemToDelete = null
                        viewModel.deleteFilterItem(filterType, nameToDelete) {}
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (itemToRename != null) {
            RenameFilterDialog(
                initialName = itemToRename!!,
                suggestions = suggestionList,
                onDismiss = { itemToRename = null },
                onConfirm = { newName ->
                    val oldName = itemToRename!!
                    itemToRename = null
                    viewModel.renameFilterItem(filterType, oldName, newName) {}
                }
            )
        }

        if (showSortSheet) {
            FilterItemSortBottomSheet(
                currentSortType = sortType,
                isAscending = sortAscending,
                onSortTypeChange = { newType ->
                    if (sortType == newType) {
                        sortAscending = !sortAscending
                    } else {
                        sortType = newType
                        sortAscending = true
                    }
                },
                onDismiss = { showSortSheet = false }
            )
        }
    }
}
