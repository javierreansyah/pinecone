package com.javierreansyah.pinecone.ui.features.reader.components.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Remove
import com.javierreansyah.pinecone.R

@Composable
fun IncrementDecrementControl(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onStep: (Int) -> Unit,
    enabled: Boolean = true,
    inline: Boolean = false,
) {
    if (inline) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IncrementDecrementButtons(value, onStep, enabled)
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.38f
                ),
            )
            IncrementDecrementButtons(value, onStep, enabled)
        }
    }
}

@Composable
private fun IncrementDecrementButtons(
    value: String,
    onStep: (Int) -> Unit,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        IconButton(
            onClick = { onStep(-1) },
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Remove,
                contentDescription = stringResource(R.string.action_decrease),
                modifier = Modifier.size(18.dp),
            )
        }

        val actionsSpatialSpec = motionScheme.fastSpatialSpec<IntOffset>()
        val actionsEffectsSpec = motionScheme.fastEffectsSpec<Float>()

        val density = LocalDensity.current
        val offsetPx = with(density) { 40.dp.roundToPx() }

        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val targetNum = targetState.replace(Regex("[^\\d.-]"), "").toFloatOrNull() ?: 0f
                val initialNum =
                    initialState.replace(Regex("[^\\d.-]"), "").toFloatOrNull() ?: 0f
                val isIncrement = targetNum > initialNum

                if (isIncrement) {
                    (slideInVertically(
                        animationSpec = actionsSpatialSpec,
                    ) { offsetPx } + fadeIn(animationSpec = actionsEffectsSpec)).togetherWith(
                        slideOutVertically(
                            animationSpec = actionsSpatialSpec,
                        ) { -offsetPx } + fadeOut(animationSpec = actionsEffectsSpec),
                    )
                } else {
                    (slideInVertically(
                        animationSpec = actionsSpatialSpec,
                    ) { -offsetPx } + fadeIn(animationSpec = actionsEffectsSpec)).togetherWith(
                        slideOutVertically(
                            animationSpec = actionsSpatialSpec,
                        ) { offsetPx } + fadeOut(animationSpec = actionsEffectsSpec),
                    )
                }
            },
            label = "ValueAnimation",
        ) { targetValue ->
            Text(
                text = targetValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.38f,
                ),
                modifier = Modifier.widthIn(min = 60.dp),
            )
        }

        IconButton(
            onClick = { onStep(1) },
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Add,
                contentDescription = stringResource(R.string.action_increase),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
