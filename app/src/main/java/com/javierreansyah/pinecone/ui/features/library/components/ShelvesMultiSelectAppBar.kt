package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Content_copy
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Drive_file_move
import com.composables.icons.materialsymbols.outlined.Edit
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.features.library.PublicationWording

@Composable
fun ShelvesMultiSelectAppBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    isAllSelected: Boolean,
    selectedShelfName: String,
    onCloseMultiSelect: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onTransfer: ((Boolean) -> Unit)? = null,
    wording: PublicationWording = PublicationWording.Items,
) {
    var showDeleteConfirmation by remember { mutableStateOf(value = false) }
    var showRenameDialog by remember { mutableStateOf(value = false) }
    var renameText by remember { mutableStateOf("") }

    SelectionTopAppBar(
        selectedCount = selectedCount,
        isAllSelected = isAllSelected,
        onCloseMultiSelect = onCloseMultiSelect,
        onClearSelection = onClearSelection,
        onSelectAll = onSelectAll,
        modifier = modifier,
        content = {
            IconButton(
                onClick = {
                    renameText = selectedShelfName
                    showRenameDialog = true
                },
                enabled = selectedCount == 1,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Edit,
                    contentDescription = stringResource(R.string.action_rename),
                    tint = if (selectedCount == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f,
                    ),
                )
            }
            if (onTransfer != null) {
                IconButton(onClick = { onTransfer(false) }, enabled = selectedCount > 0) {
                    Icon(
                        MaterialSymbols.Outlined.Drive_file_move,
                        contentDescription = stringResource(R.string.library_move_shelves)
                    )
                }
                IconButton(onClick = { onTransfer(true) }, enabled = selectedCount > 0) {
                    Icon(
                        MaterialSymbols.Outlined.Content_copy,
                        contentDescription = stringResource(R.string.library_copy_shelves)
                    )
                }
            }
            IconButton(onClick = { showDeleteConfirmation = true }, enabled = selectedCount > 0) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f,
                    ),
                )
            }
        },
    )

    if (showRenameDialog) {
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    stringResource(R.string.library_rename_shelf_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.library_shelf_name_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRename(renameText.trim())
                        }
                        showRenameDialog = false
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text(
                        stringResource(R.string.action_rename),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )
    }

    if (showDeleteConfirmation) {
        DeleteSelectionDialog(
            title = stringResource(R.string.library_delete_shelf_title),
            message = pluralStringResource(wording.deleteShelvesMessage, selectedCount, selectedCount),
            onDismissRequest = { showDeleteConfirmation = false },
            onConfirm = {
                onDelete()
                showDeleteConfirmation = false
            },
        )
    }
}
