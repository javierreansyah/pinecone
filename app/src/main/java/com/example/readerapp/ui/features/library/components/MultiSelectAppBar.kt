package com.example.readerapp.ui.features.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Circle
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Select_all
import com.example.readerapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectAppBar(
    selectedCount: Int,
    showMarkAsRead: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkAsReadUnread: () -> Unit,
    onAddToShelf: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(text = "$selectedCount")
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel)
                )
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Select_all,
                    contentDescription = stringResource(R.string.action_select_all)
                )
            }
            IconButton(onClick = onMarkAsReadUnread) {
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
            IconButton(onClick = onAddToShelf) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Folder,
                    contentDescription = stringResource(R.string.library_label_add_to_shelf)
                )
            }
            IconButton(onClick = onArchive) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Archive,
                    contentDescription = stringResource(R.string.book_archive)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MultiSelectTopBarTransition(
    isMultiSelect: Boolean,
    multiSelectBar: @Composable () -> Unit,
    defaultBar: @Composable () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val actionsSpatialSpec = motionScheme.fastSpatialSpec<IntOffset>()
    val actionsEffectsSpec = motionScheme.fastEffectsSpec<Float>()
    val density = LocalDensity.current
    val offset = remember(density) { with(density) { (-30).dp.roundToPx() } }

    AnimatedContent(
        targetState = isMultiSelect,
        transitionSpec = {
            if (targetState) {
                (slideInVertically(
                    initialOffsetY = { offset },
                    animationSpec = actionsSpatialSpec
                ) + fadeIn(animationSpec = actionsEffectsSpec)).togetherWith(
                    fadeOut(animationSpec = actionsEffectsSpec)
                )
            } else {
                fadeIn(animationSpec = actionsEffectsSpec).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { offset },
                        animationSpec = actionsSpatialSpec
                    ) + fadeOut(animationSpec = actionsEffectsSpec)
                )
            }
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
