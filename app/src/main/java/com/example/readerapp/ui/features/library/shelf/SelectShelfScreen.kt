package com.example.readerapp.ui.features.library.shelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Folder
import com.example.readerapp.R
import com.example.readerapp.ui.components.EmptyState
import com.example.readerapp.ui.components.LibraryTopAppBar
import com.example.readerapp.ui.features.library.components.ShelfListItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectShelfScreen(
    bookId: String,
    viewModel: SelectShelfViewModel,
    onNavigateBack: () -> Unit
) {
    val shelves by viewModel.shelves.collectAsState()
    val validShelves = remember(shelves) { shelves.filter { it.shelf.id != "unshelved" } }
    val isEmpty = validShelves.isEmpty()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }

    Scaffold(
        modifier = if (isEmpty) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopAppBar(
                title = { Text(stringResource(R.string.library_select_shelf_title)) },
                onBack = onNavigateBack,
                isEmpty = isEmpty,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateShelfDialog = true }
            ) {
                Icon(
                    MaterialSymbols.Outlined.Add,
                    contentDescription = stringResource(R.string.action_create)
                )
            }
        }
    ) { paddingValues ->
        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = MaterialSymbols.Outlined.Folder,
                    text = stringResource(R.string.library_empty_shelves)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues
            ) {
                items(validShelves) { shelfWithCovers ->
                    ShelfListItem(
                        shelfWithCovers = shelfWithCovers,
                        onClick = {
                            viewModel.addBookToShelf(
                                shelfId = shelfWithCovers.shelf.id,
                                bookId = bookId,
                                onComplete = onNavigateBack
                            )
                        }
                    )
                }
            }
        }
    }

    // Create Shelf Dialog
    if (showCreateShelfDialog) {
        AlertDialog(
            onDismissRequest = { showCreateShelfDialog = false },
            title = {
                Text(
                    stringResource(R.string.library_create_shelf_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                OutlinedTextField(
                    value = newShelfName,
                    onValueChange = { newShelfName = it },
                    label = {
                        Text(
                            stringResource(R.string.library_shelf_name_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newShelfName.isNotBlank()) {
                            viewModel.createShelfAndAddBook(
                                name = newShelfName,
                                bookId = bookId,
                                onComplete = onNavigateBack
                            )
                            newShelfName = ""
                            showCreateShelfDialog = false
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.action_create),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateShelfDialog = false }
                ) {
                    Text(
                        stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}
