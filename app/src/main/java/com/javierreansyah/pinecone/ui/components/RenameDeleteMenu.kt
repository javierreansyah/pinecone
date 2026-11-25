package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.More_vert
import com.javierreansyah.pinecone.R

@Composable
fun RenameDeleteMenu(
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
            AppDropdownMenuGroup {
                AppDropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    shape = AppDropdownMenuDefaults.itemShape(0, 2).shape,
                    leadingIcon = {
                        Icon(
                            MaterialSymbols.Outlined.Edit,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                    },
                    onClick = onRenameClick,
                )
                AppDropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    shape = AppDropdownMenuDefaults.itemShape(1, 2).shape,
                    colors = AppDropdownMenuDefaults.destructiveItemColors(),
                    leadingIcon = {
                        Icon(
                            MaterialSymbols.Outlined.Delete,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                    },
                    onClick = onDeleteClick,
                )
            }
        }
    }
}
