package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.SegmentedColumn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrganizeBottomSheet(
    viewModel: OrganizeViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }
    
    var showCreateSpaceDialog by remember { mutableStateOf(false) }
    var newSpaceName by remember { mutableStateOf("") }

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val maxSheetHeight = remember(windowInfo.containerSize, density) {
        with(density) { (windowInfo.containerSize.height * 0.9f).toDp() }
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onNavigateBack,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.action_organize),
                style = MaterialTheme.typography.titleLarge
            )

            if (!uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                            item(
                                key = "new-space",
                                onClick = { showCreateSpaceDialog = true },
                                leadingContent = {
                                    Icon(MaterialSymbols.Outlined.Add, contentDescription = null)
                                },
                                content = { Text(stringResource(R.string.library_new_space)) }
                            )
                        }
                    }

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
                            item(
                                key = "new-shelf",
                                onClick = { showCreateShelfDialog = true },
                                leadingContent = {
                                    Icon(MaterialSymbols.Outlined.Add, contentDescription = null)
                                },
                                content = { Text(stringResource(R.string.library_new_shelf)) }
                            )
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
