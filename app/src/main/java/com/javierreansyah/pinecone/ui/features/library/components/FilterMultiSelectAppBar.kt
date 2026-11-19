package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconButton
import com.javierreansyah.pinecone.ui.features.library.PublicationWording

@Composable
fun FilterMultiSelectAppBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onCloseMultiSelect: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRenameClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    wording: PublicationWording = PublicationWording.Items,
) {
    var showDeleteConfirmation by remember { mutableStateOf(value = false) }

    SelectionTopAppBar(
        selectedCount = selectedCount,
        isAllSelected = isAllSelected,
        onCloseMultiSelect = onCloseMultiSelect,
        onClearSelection = onClearSelection,
        onSelectAll = onSelectAll,
        modifier = modifier,
        content = {
            ConnectedGroupIconButton(
                onClick = onRenameClick,
                enabled = selectedCount > 0,
                buttonSize = 40.dp,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Edit,
                    contentDescription = stringResource(R.string.action_rename),
                )
            }
            ConnectedGroupIconButton(
                onClick = { showDeleteConfirmation = true },
                enabled = selectedCount > 0,
                buttonSize = 40.dp,
                contentColor = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        },
    )

    if (showDeleteConfirmation) {
        DeleteSelectionDialog(
            title = stringResource(R.string.action_delete),
            message = pluralStringResource(
                wording.deleteLabelsMessage,
                selectedCount,
                selectedCount
            ),
            onDismissRequest = { showDeleteConfirmation = false },
            onConfirm = {
                onDelete()
                showDeleteConfirmation = false
            },
        )
    }
}
