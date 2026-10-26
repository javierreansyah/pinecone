package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Circle
import com.composables.icons.materialsymbols.outlined.Create_new_folder
import com.composables.icons.materialsymbols.outlined.Delete
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
    modifier: Modifier = Modifier,
    isUnarchive: Boolean = false,
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
            IconButton(onClick = onMarkAsReadUnread, enabled = selectedCount > 0) {
                if (showMarkAsRead) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Check_circle,
                        contentDescription = stringResource(R.string.publication_mark_as_read),
                    )
                } else {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Circle,
                        contentDescription = stringResource(R.string.publication_mark_as_unread),
                    )
                }
            }
            IconButton(onClick = onOrganize, enabled = selectedCount > 0) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Create_new_folder,
                    contentDescription = stringResource(R.string.action_organize),
                )
            }
            IconButton(onClick = onArchive, enabled = selectedCount > 0) {
                if (isUnarchive) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Unarchive,
                        contentDescription = stringResource(R.string.publication_unarchive),
                    )
                } else {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Archive,
                        contentDescription = stringResource(R.string.publication_archive),
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
