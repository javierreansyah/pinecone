package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Deselect
import com.composables.icons.materialsymbols.outlined.Select_all
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconButton
import com.javierreansyah.pinecone.ui.components.TopAppBarActionGroup
import com.javierreansyah.pinecone.ui.components.TopAppBarCircleIconButton
import com.javierreansyah.pinecone.ui.components.topBarFadeBackground

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
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val slideOffsetPx = with(LocalDensity.current) { 18.dp.roundToPx() }

    TopAppBar(
        title = {
            AnimatedContent(
                targetState = selectedCount,
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    val isIncrement = targetState > initialState
                    val transform = if (isIncrement) {
                        (slideInVertically(animationSpec = spatialSpec) { slideOffsetPx } +
                                fadeIn(animationSpec = effectsSpec)).togetherWith(
                            slideOutVertically(animationSpec = spatialSpec) { -slideOffsetPx } +
                                    fadeOut(animationSpec = effectsSpec),
                        )
                    } else {
                        (slideInVertically(animationSpec = spatialSpec) { -slideOffsetPx } +
                                fadeIn(animationSpec = effectsSpec)).togetherWith(
                            slideOutVertically(animationSpec = spatialSpec) { slideOffsetPx } +
                                    fadeOut(animationSpec = effectsSpec),
                        )
                    }
                    transform.using(null)
                },
                label = "selectionCountRolling",
            ) { count ->
                Text(
                    text = count.toString(),
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                )
            }
        },
        navigationIcon = {
            TopAppBarCircleIconButton(onClick = onCloseMultiSelect) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                )
            }
        },
        actions = {
            TopAppBarActionGroup {
                ConnectedGroupIconButton(
                    onClick = {
                        if (isAllSelected) {
                            onClearSelection()
                        } else {
                            onSelectAll()
                        }
                    },
                ) {
                    Crossfade(
                        targetState = isAllSelected,
                        animationSpec = effectsSpec,
                        label = "selectAllIcon",
                    ) { allSelected ->
                        Icon(
                            imageVector = if (allSelected) MaterialSymbols.Outlined.Deselect else MaterialSymbols.Outlined.Select_all,
                            contentDescription = stringResource(
                                if (allSelected) R.string.action_deselect_all else R.string.action_select_all,
                            ),
                        )
                    }
                }
                content()
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = modifier.topBarFadeBackground(),
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
            ) {
                Text(
                    stringResource(R.string.action_delete),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
    )
}
