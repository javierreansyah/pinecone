package com.javierreansyah.pinecone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R

internal data class OperationReport(val id: String, val title: String, val message: String)

private class IdleFabMenuContent {
    var content: (@Composable () -> Unit)? = null
}

/** Running work is opt-in to inspect; results open automatically and remain until dismissed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OperationFeedback(
    operationId: String?,
    title: String,
    progressMessage: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    report: OperationReport? = null,
    onDismissReport: () -> Unit = {},
    progress: Float? = null,
    showFab: Boolean = true,
    fabContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    fabContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    content: (@Composable () -> Unit)? = null,
) {
    var openedOperation by rememberSaveable { mutableStateOf<String?>(null) }
    val edgeSpacing = 16.dp
    val edgeSpacingPx = with(LocalDensity.current) { edgeSpacing.roundToPx() }
    val fabVisible = showFab && (operationId != null || content != null)
    // Retain the outgoing mode while the host slides away after navigation or completion.
    val lastFabMenu = remember { IdleFabMenuContent() }
    val menuContent =
        if (fabVisible) content.takeIf { operationId == null } else lastFabMenu.content
    SideEffect {
        if (fabVisible) lastFabMenu.content = content.takeIf { operationId == null }
    }
    val progressSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val animatedProgress by key(operationId) {
        animateFloatAsState(
            targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
            animationSpec = progressSpec,
            label = "Operation progress",
        )
    }
    AnimatedVisibility(
        visible = fabVisible,
        // Material's FAB menu already supplies the same horizontal and bottom edge spacing.
        modifier = modifier.then(
            if (menuContent == null) Modifier.padding(
                start = edgeSpacing,
                end = edgeSpacing,
                bottom = edgeSpacing
            )
            else Modifier,
        ),
        // Own the edge spacing so the entire button clears the window in the same flight.
        enter = slideInHorizontally(
            tween(
                220,
                easing = LinearOutSlowInEasing
            )
        ) { it + edgeSpacingPx },
        exit = slideOutHorizontally(
            tween(
                200,
                easing = FastOutLinearInEasing
            )
        ) { it + edgeSpacingPx },
    ) {
        if (menuContent != null) {
            menuContent()
        } else {
            FloatingActionButton(
                onClick = { openedOperation = operationId },
                containerColor = fabContainerColor,
                contentColor = fabContentColor,
                modifier = Modifier.semantics { contentDescription = title },
            ) {
                OperationProgressIndicator(
                    progress = progress?.let { animatedProgress },
                    modifier = Modifier.size(28.dp),
                    color = fabContentColor,
                )
            }
        }
    }

    val showProgress = operationId != null && openedOperation == operationId
    if (report != null || showProgress) {
        // A queued report must receive a fresh sheet after its predecessor has been dismissed.
        key(report?.id ?: operationId) {
            val dismiss = {
                openedOperation = null
                if (report != null) onDismissReport()
            }
            ModalBottomSheet(
                onDismissRequest = dismiss,
                sheetState = rememberBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = report?.title ?: title,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = report?.message ?: progressMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (report == null) {
                        OperationProgressIndicator(
                            progress = progress?.let { animatedProgress },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(48.dp),
                        )
                    }
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(0.7f),
                        onClick = {
                            if (report == null) onCancel()
                            dismiss()
                        },
                    ) {
                        Text(stringResource(if (report == null) R.string.action_cancel else R.string.action_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationProgressIndicator(progress: Float?, color: Color, modifier: Modifier) {
    if (progress == null) {
        CircularWavyProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = color.copy(alpha = 0.18f)
        )
    } else {
        CircularWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = color,
            trackColor = color.copy(alpha = 0.18f),
        )
    }
}
