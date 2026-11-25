package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.MenuItemShapes
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SelectableMenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppDropdownMenuDefaults {
    /**
     * Compose Material 3 1.5.0 relies on `Surface`'s `minimumInteractiveComponentSize()` (`48.dp`)
     * around a `44.dp` row to inject `2.dp` top/bottom margin outside the item surface. That breaks
     * whenever an ancestor overrides [LocalMinimumInteractiveComponentSize] to `0.dp` (such as
     * [ConnectedButtonGroup]) or when font scaling makes the row taller than `44.dp`, collapsing
     * top/bottom inset to `2.dp` and item spacing to `0.dp`. Conversely, leaving it enabled adds
     * `2.dp` on top of explicit vertical padding (making it `6.dp`).
     *
     * Explicitly providing `0.dp` inside [AppDropdownMenuGroup] and pairing `3.dp` group vertical
     * padding with `1.dp` item vertical padding makes every menu deterministic across all screens
     * and font scales: `4.dp` concentric outer inset on all four sides and `2.dp` between items.
     */
    val GroupContentPadding: PaddingValues = PaddingValues(vertical = 3.dp)
    val ItemVerticalPadding: Dp = 1.dp

    @Composable
    fun itemShape(index: Int, count: Int): MenuItemShapes =
        if (count <= 1) {
            MenuDefaults.itemShapes(shape = MenuDefaults.selectedItemShape)
        } else {
            MenuDefaults.itemShape(index, count)
        }

    @Composable
    fun destructiveItemColors(): MenuItemColors = MenuDefaults.itemColors(
        textColor = MaterialTheme.colorScheme.error,
        leadingIconColor = MaterialTheme.colorScheme.error,
    )
}

@Composable
fun AppDropdownMenuGroup(
    modifier: Modifier = Modifier,
    shapes: MenuGroupShapes = MenuDefaults.groupShape(0, 1),
    containerColor: Color = MenuDefaults.groupStandardContainerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = AppDropdownMenuDefaults.GroupContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        DropdownMenuGroup(
            shapes = shapes,
            modifier = modifier,
            containerColor = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun AppDropdownMenuItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    horizontalArrangement: Arrangement.Horizontal =
        MenuDefaults.DropdownMenuItemHorizontalArrangement,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuSelectableItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = text,
        shape = shape,
        modifier = modifier.menuItemVerticalPadding(hasSupportingText = supportingText != null),
        leadingIcon = leadingIcon,
        trailingContent = trailingContent,
        supportingText = supportingText,
        enabled = enabled,
        colors = colors,
        horizontalArrangement = horizontalArrangement,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    )
}

@Composable
fun AppSelectableDropdownMenuItem(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    shapes: MenuItemShapes,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    selectedLeadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: SelectableMenuItemColors = MenuDefaults.selectableItemColors(),
    horizontalArrangement: Arrangement.Horizontal =
        MenuDefaults.DropdownMenuItemHorizontalArrangement,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuSelectableItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    SelectableDropdownMenuItem(
        selected = selected,
        onClick = onClick,
        text = text,
        shapes = shapes,
        modifier = modifier.menuItemVerticalPadding(hasSupportingText = supportingText != null),
        leadingIcon = leadingIcon,
        selectedLeadingIcon = selectedLeadingIcon,
        trailingContent = trailingContent,
        supportingText = supportingText,
        enabled = enabled,
        colors = colors,
        horizontalArrangement = horizontalArrangement,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    )
}

private fun Modifier.menuItemVerticalPadding(hasSupportingText: Boolean): Modifier =
    if (hasSupportingText) this else padding(vertical = AppDropdownMenuDefaults.ItemVerticalPadding)
