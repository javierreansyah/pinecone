package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Deselect
import com.composables.icons.materialsymbols.outlined.Select_all
import com.javierreansyah.pinecone.R

@Composable
internal fun SelectionTopAppBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onCloseMultiSelect: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        title = {
            Text(text = selectedCount.toString())
        },
        navigationIcon = {
            IconButton(onClick = onCloseMultiSelect) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                )
            }
        },
        actions = {
            IconButton(
                onClick = {
                    if (isAllSelected) {
                        onClearSelection()
                    } else {
                        onSelectAll()
                    }
                },
            ) {
                Icon(
                    imageVector = if (isAllSelected) MaterialSymbols.Outlined.Deselect else MaterialSymbols.Outlined.Select_all,
                    contentDescription = stringResource(
                        if (isAllSelected) R.string.action_deselect_all else R.string.action_select_all,
                    ),
                )
            }
            content()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    )
}

@Composable
internal fun DeleteSelectionDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
