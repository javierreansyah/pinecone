package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Forest
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.SegmentedColumn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrganizeScreen(
    viewModel: OrganizeViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    val isEmpty = uiState.spaces.isEmpty() && uiState.shelves.isEmpty()

    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }
    
    var showCreateSpaceDialog by remember { mutableStateOf(false) }
    var newSpaceName by remember { mutableStateOf("") }

    var fabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = if (isEmpty) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopAppBar(
                title = { Text(stringResource(R.string.action_organize)) },
                onBack = onNavigateBack,
                isEmpty = isEmpty,
                scrollBehavior = scrollBehavior,
                actions = {
                    FilledIconButton(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(
                                IconButtonDefaults.smallContainerSize(
                                    widthOption = IconButtonDefaults.IconButtonWidthOption.Wide
                                )
                            ),
                        shapes = IconButtonDefaults.shapes(),
                        onClick = { viewModel.save(onNavigateBack) }
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Check,
                            contentDescription = stringResource(R.string.action_accept)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabExpanded,
                        onCheckedChange = { fabExpanded = it }
                    ) {
                        val icon = if (fabExpanded) MaterialSymbols.Outlined.Close else MaterialSymbols.Outlined.Add
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (fabExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        showCreateSpaceDialog = true
                    },
                    icon = { Icon(MaterialSymbols.Outlined.Forest, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_create_space_title)) }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        showCreateShelfDialog = true
                    },
                    icon = { Icon(MaterialSymbols.Outlined.Folder, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_create_shelf_title)) }
                )
            }
        }
    ) { paddingValues ->
        if (!uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.spaces.isNotEmpty()) {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = stringResource(R.string.library_spaces_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SegmentedColumn {
                            uiState.spaces.forEach { spaceItem ->
                                item(
                                    key = spaceItem.space.id,
                                    onClick = { viewModel.toggleSpace(spaceItem.space.id, spaceItem.state) },
                                    content = { Text(spaceItem.space.name) },
                                    trailingContent = {
                                        TriStateCheckbox(
                                            state = spaceItem.state,
                                            onClick = { viewModel.toggleSpace(spaceItem.space.id, spaceItem.state) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (uiState.shelves.isNotEmpty()) {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = stringResource(R.string.library_tab_shelves),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SegmentedColumn {
                            uiState.shelves.forEach { shelfItem ->
                                item(
                                    key = shelfItem.shelf.id,
                                    onClick = { viewModel.toggleShelf(shelfItem.shelf.id, shelfItem.state) },
                                    content = { Text(shelfItem.shelf.name) },
                                    trailingContent = {
                                        TriStateCheckbox(
                                            state = shelfItem.state,
                                            onClick = { viewModel.toggleShelf(shelfItem.shelf.id, shelfItem.state) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
                            viewModel.createShelf(newShelfName)
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

    if (showCreateSpaceDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSpaceDialog = false },
            title = {
                Text(
                    stringResource(R.string.library_create_space_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                OutlinedTextField(
                    value = newSpaceName,
                    onValueChange = { newSpaceName = it },
                    label = {
                        Text(
                            stringResource(R.string.library_space_name_label),
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
                        if (newSpaceName.isNotBlank()) {
                            viewModel.createSpace(newSpaceName)
                            newSpaceName = ""
                            showCreateSpaceDialog = false
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
                    onClick = { showCreateSpaceDialog = false }
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
