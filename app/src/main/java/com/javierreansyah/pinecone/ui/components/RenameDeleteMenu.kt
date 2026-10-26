package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
