package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Circle
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Create_new_folder
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Deselect
import com.composables.icons.materialsymbols.outlined.Select_all
import com.composables.icons.materialsymbols.outlined.Unarchive
import com.javierreansyah.pinecone.R

@OptIn(ExperimentalMaterial3Api::class)
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
    isUnarchive: Boolean = false
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(text = "$selectedCount")
        },
        navigationIcon = {
            IconButton(onClick = onCloseMultiSelect) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel)
                )
            }
        },
        actions = {
            IconButton(onClick = {
                if (isAllSelected) {
                    onClearSelection()
                } else {
                    onSelectAll()
                }
            }) {
                Icon(
                    imageVector = if (isAllSelected) MaterialSymbols.Outlined.Deselect else MaterialSymbols.Outlined.Select_all,
                    contentDescription = stringResource(
                        if (isAllSelected) R.string.action_deselect_all else R.string.action_select_all
                    )
                )
            }
            IconButton(onClick = onMarkAsReadUnread, enabled = selectedCount > 0) {
                if (showMarkAsRead) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Check_circle,
                        contentDescription = stringResource(R.string.book_mark_as_read)
                    )
                } else {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Circle,
                        contentDescription = stringResource(R.string.book_mark_as_unread)
                    )
                }
            }
            IconButton(onClick = onOrganize, enabled = selectedCount > 0) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Create_new_folder,
                    contentDescription = stringResource(R.string.action_organize)
                )
            }
            IconButton(onClick = onArchive, enabled = selectedCount > 0) {
                if (isUnarchive) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Unarchive,
                        contentDescription = stringResource(R.string.book_unarchive)
                    )
                } else {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Archive,
                        contentDescription = stringResource(R.string.book_archive)
                    )
                }
            }
            IconButton(onClick = { showDeleteConfirmation = true }, enabled = selectedCount > 0) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f
                    )
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    stringResource(R.string.book_delete_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    stringResource(R.string.book_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MultiSelectTopBarTransition(
    isMultiSelect: Boolean,
    multiSelectBar: @Composable () -> Unit,
    defaultBar: @Composable () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val actionsEffectsSpec = motionScheme.fastEffectsSpec<Float>()

    AnimatedContent(
        targetState = isMultiSelect,
        transitionSpec = {
            fadeIn(animationSpec = actionsEffectsSpec).togetherWith(
                fadeOut(animationSpec = actionsEffectsSpec)
            )
        },
        label = "topBarTransition"
    ) { showMultiSelect ->
        if (showMultiSelect) {
            multiSelectBar()
        } else {
            defaultBar()
        }
    }
}
