package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val MinConnectedButtonWidth = 48.dp

@Composable
fun <T> SegmentedButtonGroup(
    options: List<T>,
    icons: List<ImageVector>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String? = null,
    fillWidth: Boolean = false,
    buttonSize: ToggleButtonSize = ToggleButtonSize.ExtraSmall,
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
    border: BorderStroke? = null,
    optionLabel: (T) -> String = { it.toString() },
) {
    val buttonsContent: @Composable () -> Unit = {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val icon = icons.getOrNull(index)
            val shapes = when {
                options.size == 1 -> ToggleButtonDefaults.shapesFor(buttonSize)
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }

            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelected(option) },
                modifier = Modifier
                    .widthIn(min = MinConnectedButtonWidth)
                    .semantics { role = Role.RadioButton },
                buttonSize = buttonSize,
                enabled = enabled,
                icon = icon?.let {
                    {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                        )
                    }
                },
                shapes = shapes,
                colors = colors,
                border = border,
            ) {
                Text(
                    text = optionLabel(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    val buttonRow = @Composable {
        val rowModifier = when {
            title == null -> modifier
            fillWidth -> Modifier.fillMaxWidth()
            else -> Modifier
        }
        if (fillWidth) {
            FillWhenFitsConnectedRow(
                modifier = rowModifier,
                content = buttonsContent,
            )
        } else {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                buttonsContent()
            }
        }
    }

    if (title != null) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.38f,
                ),
            )
            buttonRow()
        }
    } else {
        buttonRow()
    }
}

@Composable
private fun FillWhenFitsConnectedRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }

        val spacingPx = ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx()
        val minItemWidthPx = MinConnectedButtonWidth.roundToPx()
        val naturalWidths = IntArray(measurables.size) { index ->
            measurables[index].maxIntrinsicWidth(constraints.maxHeight)
                .coerceAtLeast(minItemWidthPx)
        }
        val targetWidth = when {
            constraints.minWidth > 0 -> constraints.minWidth
            constraints.hasBoundedWidth -> constraints.maxWidth
            else -> 0
        }
        val finalWidths = distributeConnectedWidths(
            naturalWidths = naturalWidths,
            spacingPx = spacingPx,
            targetWidth = targetWidth,
        )
        val totalSpacing = spacingPx * (measurables.size - 1)
        val totalNatural = naturalWidths.sum() + totalSpacing
        val shouldExpand = targetWidth > totalNatural

        val placeables = measurables.mapIndexed { index, measurable ->
            val childConstraints = if (shouldExpand) {
                constraints.copy(
                    minWidth = finalWidths[index],
                    maxWidth = finalWidths[index],
                )
            } else {
                constraints.copy(minWidth = 0)
            }
            measurable.measure(childConstraints)
        }

        val height = placeables.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = (placeables.sumOf { it.width } + totalSpacing)
            .coerceIn(constraints.minWidth, constraints.maxWidth)

        layout(width, height) {
            var x = 0
            placeables.forEach { placeable ->
                val y = (height - placeable.height) / 2
                placeable.placeRelative(x, y)
                x += placeable.width + spacingPx
            }
        }
    }
}

internal fun distributeConnectedWidths(
    naturalWidths: IntArray,
    spacingPx: Int,
    targetWidth: Int,
): IntArray {
    if (naturalWidths.isEmpty()) return naturalWidths
    val totalSpacing = spacingPx * (naturalWidths.size - 1)
    val totalNatural = naturalWidths.sum() + totalSpacing
    val extraSpace = (targetWidth - totalNatural).coerceAtLeast(0)
    if (extraSpace == 0) return naturalWidths

    val baseExtra = extraSpace / naturalWidths.size
    val remainder = extraSpace % naturalWidths.size
    return IntArray(naturalWidths.size) { index ->
        naturalWidths[index] + baseExtra + if (index < remainder) 1 else 0
    }
}
