package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Circle
import com.composables.icons.materialsymbols.outlined.Create_new_folder
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Unarchive
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.features.library.PublicationWording

@Composable
fun MultiSelectAppBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    showMarkAsRead: Boolean,
    onCloseMultiSelect: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkAsReadUnread: () -> Unit,
    onOrganize: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onInfo: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isUnarchive: Boolean = false,
    wording: PublicationWording = PublicationWording.Items,
) {
    var showDeleteConfirmation by remember(selectedCount) { mutableStateOf(false) }
    var menuExpanded by remember(selectedCount) { mutableStateOf(false) }

    SelectionTopAppBar(
        selectedCount = selectedCount,
        isAllSelected = isAllSelected,
        onCloseMultiSelect = onCloseMultiSelect,
        onClearSelection = onClearSelection,
        onSelectAll = onSelectAll,
        modifier = modifier,
        content = {
            IconButton(onClick = onOrganize, enabled = selectedCount > 0) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Create_new_folder,
                    contentDescription = stringResource(R.string.action_organize),
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = selectedCount > 0) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.More_vert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
                DropdownMenuPopup(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    val actions = buildList {
                        if (selectedCount == 1 && onInfo != null) {
                            add(
                                BookSelectionAction(
                                    wording.infoTitle,
                                    MaterialSymbols.Outlined.Info,
                                    onInfo
                                )
                            )
                        }
                        add(
                            BookSelectionAction(
                                if (showMarkAsRead) R.string.publication_mark_as_read else R.string.publication_mark_as_unread,
                                if (showMarkAsRead) MaterialSymbols.Outlined.Check_circle else MaterialSymbols.Outlined.Circle,
                                onMarkAsReadUnread,
                            )
                        )
                        add(
                            BookSelectionAction(
                                if (isUnarchive) R.string.publication_unarchive else R.string.publication_archive,
                                if (isUnarchive) MaterialSymbols.Outlined.Unarchive else MaterialSymbols.Outlined.Archive,
                                onArchive,
                            )
                        )
                        add(
                            BookSelectionAction(
                                R.string.action_delete,
                                MaterialSymbols.Outlined.Delete,
                                { showDeleteConfirmation = true },
                                destructive = true,
                            )
                        )
                    }
                    val interactionSource = remember { MutableInteractionSource() }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 1),
                        interactionSource = interactionSource,
                    ) {
                        actions.forEachIndexed { index, action ->
                            val color = if (action.destructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                            SelectableDropdownMenuItem(
                                selected = false,
                                text = { Text(stringResource(action.label), color = color) },
                                shapes = MenuDefaults.itemShape(index, actions.size),
                                leadingIcon = {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                        tint = color,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            }
        },
    )

    if (showDeleteConfirmation) {
        DeleteSelectionDialog(
            title = pluralStringResource(wording.deleteSelectionTitle, selectedCount),
            message = pluralStringResource(
                wording.deleteSelectionMessage,
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

private data class BookSelectionAction(
    @param:androidx.annotation.StringRes val label: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

@Composable
fun MultiSelectTopBarTransition(
    isMultiSelect: Boolean,
    multiSelectBar: @Composable () -> Unit,
    defaultBar: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val actionsEffectsSpec = motionScheme.fastEffectsSpec<Float>()

    AnimatedContent(
        targetState = isMultiSelect,
        transitionSpec = {
            fadeIn(animationSpec = actionsEffectsSpec).togetherWith(
                fadeOut(animationSpec = actionsEffectsSpec),
            )
        },
        label = "topBarTransition",
    ) { showMultiSelect ->
        if (showMultiSelect) {
            multiSelectBar()
        } else {
            defaultBar()
        }
    }
}
