package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Arrow_drop_up
import com.composables.icons.materialsymbols.outlined.Border_all
import com.composables.icons.materialsymbols.outlined.Calendar_view_month
import com.composables.icons.materialsymbols.outlined.View_carousel
import com.composables.icons.materialsymbols.outlined.View_list
import com.example.readerapp.ui.components.SegmentedColumn
import com.example.readerapp.ui.features.library.FilterSortPreferences
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.SortType
import com.example.readerapp.ui.features.library.StatusFilter
import com.example.readerapp.ui.features.library.ShelfFilter
import com.example.readerapp.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.space24)
        ) {
            content()
        }
    }
}

@Composable
fun OptionsBottomSheetSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space8)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
fun <T> SortRadioGroup(
    options: List<T>,
    selectedOption: T,
    isAscending: Boolean,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    isAscendingVisualModifier: ((T, Boolean) -> Boolean) = { _, asc -> asc }
) {
    SegmentedColumn(
        modifier = modifier.selectableGroup()
    ) {
        options.forEach { type ->
            val isSelected = selectedOption == type
            item(
                selected = isSelected,
                onClick = { onOptionSelected(type) },
                leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                trailingContent = {
                    if (isSelected) {
                        val visualAscending = isAscendingVisualModifier(type, isAscending)
                        Icon(
                            if (visualAscending) MaterialSymbols.Outlined.Arrow_drop_up else MaterialSymbols.Outlined.Arrow_drop_down,
                            contentDescription = null
                        )
                    }
                },
                content = { Text(optionLabel(type), style = MaterialTheme.typography.bodyLarge) }
            )
        }
    }
}

@Composable
fun <T> SortSection(
    options: List<T>,
    selectedOption: T,
    isAscending: Boolean,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    isAscendingVisualModifier: ((T, Boolean) -> Boolean) = { _, asc -> asc }
) {
    OptionsBottomSheetSection(title = "Sort") {
        SortRadioGroup(
            options = options,
            selectedOption = selectedOption,
            isAscending = isAscending,
            onOptionSelected = onOptionSelected,
            optionLabel = optionLabel,
            isAscendingVisualModifier = isAscendingVisualModifier
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SingleToggleGroupSection(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionContent: @Composable (T) -> Unit
) {
    OptionsBottomSheetSection(title = title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = selectedOption == option
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onOptionSelected(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton }
                ) {
                    optionContent(option)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> MultiToggleGroupSection(
    title: String,
    options: List<T>,
    selectedOptions: Set<T>,
    onOptionToggled: (T) -> Unit,
    optionContent: @Composable (T) -> Unit
) {
    OptionsBottomSheetSection(title = title) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = selectedOptions.contains(option)
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onOptionToggled(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                ) {
                    optionContent(option)
                }
            }
        }
    }
}

// Screens
@Composable
fun LibraryFilterBottomSheet(
    isShelvesTab: Boolean,
    preferences: FilterSortPreferences,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onShelfFilterToggle: (ShelfFilter) -> Unit = {},
    onDismiss: () -> Unit
) {
    OptionsBottomSheet(onDismissRequest = onDismiss) {
        val layoutModes = if (isShelvesTab) listOf(LayoutMode.List, LayoutMode.BigList)
                          else listOf(LayoutMode.Grid, LayoutMode.BigGrid, LayoutMode.List)
        
        SingleToggleGroupSection(
            title = "View",
            options = layoutModes,
            selectedOption = preferences.layoutMode,
            onOptionSelected = onLayoutModeChange,
            optionContent = { mode -> Icon(layoutModeIcon(mode), contentDescription = null) }
        )

        val sortTypes = if (isShelvesTab) listOf(SortType.Title, SortType.LastRead, SortType.Added, SortType.Progress) 
                        else listOf(SortType.Title, SortType.Author, SortType.LastRead, SortType.Added, SortType.Progress)

        SortSection(
            options = sortTypes,
            selectedOption = preferences.sortType,
            isAscending = preferences.isAscending,
            onOptionSelected = onSortTypeChange,
            optionLabel = { sortTypeLabel(it) },
            isAscendingVisualModifier = { type, asc -> if (type == SortType.LastRead) !asc else asc }
        )

        if (!isShelvesTab) {
            MultiToggleGroupSection(
                title = "Status",
                options = StatusFilter.entries,
                selectedOptions = preferences.selectedStatus,
                onOptionToggled = onStatusToggle,
                optionContent = { status -> Text(statusLabel(status), style = MaterialTheme.typography.labelLarge) }
            )
        } else {
            MultiToggleGroupSection(
                title = "Filter",
                options = ShelfFilter.entries,
                selectedOptions = preferences.selectedShelfFilter,
                onOptionToggled = onShelfFilterToggle,
                optionContent = { filter -> Text(if (filter == ShelfFilter.Shelves) "Shelves" else "Unshelved", style = MaterialTheme.typography.labelLarge) }
            )
        }
    }
}

@Composable
fun ShelfDetailFilterBottomSheet(
    shelfId: String,
    preferences: FilterSortPreferences,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onDismiss: () -> Unit
) {
    OptionsBottomSheet(onDismissRequest = onDismiss) {
        val layoutModes = listOf(LayoutMode.Grid, LayoutMode.BigGrid, LayoutMode.List)
        SingleToggleGroupSection(
            title = "View",
            options = layoutModes,
            selectedOption = preferences.layoutMode,
            onOptionSelected = onLayoutModeChange,
            optionContent = { mode -> Icon(layoutModeIcon(mode), contentDescription = null) }
        )

        val sortTypes = if (shelfId == "unshelved") SortType.entries.filter { it != SortType.Custom } else SortType.entries
        SortSection(
            options = sortTypes,
            selectedOption = preferences.sortType,
            isAscending = preferences.isAscending,
            onOptionSelected = onSortTypeChange,
            optionLabel = { sortTypeLabel(it) },
            isAscendingVisualModifier = { type, asc -> if (type == SortType.LastRead) !asc else asc }
        )

        MultiToggleGroupSection(
            title = "Status",
            options = StatusFilter.entries,
            selectedOptions = preferences.selectedStatus,
            onOptionToggled = onStatusToggle,
            optionContent = { status -> Text(statusLabel(status), style = MaterialTheme.typography.labelLarge) }
        )
    }
}

@Composable
fun FilterResultBottomSheet(
    preferences: FilterSortPreferences,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onDismiss: () -> Unit
) {
    OptionsBottomSheet(onDismissRequest = onDismiss) {
        val layoutModes = listOf(LayoutMode.Grid, LayoutMode.BigGrid, LayoutMode.List)
        SingleToggleGroupSection(
            title = "View",
            options = layoutModes,
            selectedOption = preferences.layoutMode,
            onOptionSelected = onLayoutModeChange,
            optionContent = { mode -> Icon(layoutModeIcon(mode), contentDescription = null) }
        )

        val sortTypes = listOf(SortType.Title, SortType.Author, SortType.LastRead, SortType.Added, SortType.Progress)
        SortSection(
            options = sortTypes,
            selectedOption = preferences.sortType,
            isAscending = preferences.isAscending,
            onOptionSelected = onSortTypeChange,
            optionLabel = { sortTypeLabel(it) },
            isAscendingVisualModifier = { type, asc -> if (type == SortType.LastRead) !asc else asc }
        )

        MultiToggleGroupSection(
            title = "Status",
            options = StatusFilter.entries,
            selectedOptions = preferences.selectedStatus,
            onOptionToggled = onStatusToggle,
            optionContent = { status -> Text(statusLabel(status), style = MaterialTheme.typography.labelLarge) }
        )
    }
}

enum class FilterItemSortType { Label, Size }

@Composable
fun FilterItemSortBottomSheet(
    currentSortType: FilterItemSortType,
    isAscending: Boolean,
    onSortTypeChange: (FilterItemSortType) -> Unit,
    onDismiss: () -> Unit
) {
    OptionsBottomSheet(onDismissRequest = onDismiss) {
        SortSection(
            options = FilterItemSortType.entries,
            selectedOption = currentSortType,
            isAscending = isAscending,
            onOptionSelected = onSortTypeChange,
            optionLabel = { if (it == FilterItemSortType.Label) "Label" else "Size" }
        )
    }
}

private fun layoutModeIcon(mode: LayoutMode) = when (mode) {
    LayoutMode.Grid -> MaterialSymbols.Outlined.Calendar_view_month
    LayoutMode.BigGrid -> MaterialSymbols.Outlined.Border_all
    LayoutMode.List -> MaterialSymbols.Outlined.View_list
    LayoutMode.BigList -> MaterialSymbols.Outlined.View_carousel
}

private fun sortTypeLabel(type: SortType): String = when (type) {
    SortType.Title -> "Title"
    SortType.Author -> "Author"
    SortType.LastRead -> "Date last read"
    SortType.Added -> "Date added"
    SortType.Progress -> "Percent complete"
    SortType.Custom -> "Custom Order"
}

private fun statusLabel(status: StatusFilter): String = when (status) {
    StatusFilter.NotStarted -> "Not started"
    StatusFilter.Reading -> "Currently reading"
    StatusFilter.Finished -> "Finished"
}

