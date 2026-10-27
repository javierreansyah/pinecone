package com.javierreansyah.pinecone.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Close
import com.javierreansyah.pinecone.R

@Composable
internal fun EditingNavigationIcon(editing: Boolean, onBack: () -> Unit, onCancel: () -> Unit) {
    BackHandler(enabled = editing, onBack = onCancel)

    val rotation by animateFloatAsState(
        targetValue = if (editing) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "navigationIconRotation",
    )
    val navEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    FilledTonalIconButton(
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        onClick = {
            if (editing) {
                onCancel()
            } else {
                onBack()
            }
        },
    ) {
        Box(
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = editing,
                transitionSpec = {
                    fadeIn(animationSpec = navEffectsSpec) togetherWith fadeOut(
                        animationSpec = navEffectsSpec,
                    )
                },
                label = "navigationIconContent",
            ) { targetIsEditMode ->
                if (targetIsEditMode) {
                    Icon(
                        MaterialSymbols.Outlined.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                    )
                } else {
                    Icon(
                        MaterialSymbols.Outlined.Arrow_back,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        }
    }

}

@Composable
internal fun EditingActions(
    editing: Boolean, onSave: () -> Unit, content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val slideOffsetPx = remember(density) { with(density) { 30.dp.roundToPx() } }
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = editing,
        transitionSpec = {
            if (targetState) {
                fadeIn(animationSpec = effectsSpec) togetherWith fadeOut(animationSpec = effectsSpec) +
                        slideOutHorizontally(
                            targetOffsetX = { slideOffsetPx },
                            animationSpec = spatialSpec
                        )
            } else {
                fadeIn(animationSpec = effectsSpec) +
                        slideInHorizontally(
                            initialOffsetX = { slideOffsetPx },
                            animationSpec = spatialSpec
                        ) togetherWith
                        fadeOut(animationSpec = effectsSpec)
            }
        },
        label = "actionsContent",
    ) { isEditing ->
        if (isEditing) {
            FilledIconButton(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(
                        IconButtonDefaults.smallContainerSize(
                            widthOption = IconButtonDefaults.IconButtonWidthOption.Wide,
                        ),
                    ),
                shapes = IconButtonDefaults.shapes(),
                onClick = onSave,
            ) {
                Icon(
                    MaterialSymbols.Outlined.Check,
                    contentDescription = stringResource(R.string.action_save)
                )
            }
        } else {
            content()
        }
    }
}
