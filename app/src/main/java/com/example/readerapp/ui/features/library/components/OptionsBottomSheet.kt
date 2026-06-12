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
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Arrow_drop_up
import com.composables.icons.materialsymbols.outlined.Calendar_view_month
import com.composables.icons.materialsymbols.outlined.View_carousel
import com.composables.icons.materialsymbols.outlined.View_list
import com.composables.icons.materialsymbols.outlined.Window
import com.example.readerapp.R
import com.example.readerapp.ui.components.SegmentedColumn
import com.example.readerapp.ui.features.library.FilterSortPreferences
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.ShelfFilter
import com.example.readerapp.ui.features.library.SortType
import com.example.readerapp.ui.features.library.StatusFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            content()
        }
    }
}

@Composable
fun OptionsBottomSheetSection(
    title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit
) {
    Column(
        modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)
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
    optionLabel: @Composable (T) -> String,
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
                content = { Text(optionLabel(type), style = MaterialTheme.typography.bodyLarge) })
        }
    }
}

@Composable
fun <T> SortSection(
    options: List<T>,
    selectedOption: T,
    isAscending: Boolean,
    onOptionSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    isAscendingVisualModifier: ((T, Boolean) -> Boolean) = { _, asc -> asc }
) {
    OptionsBottomSheetSection(title = stringResource(R.string.action_sort)) {
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
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton }) {
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
                    ProvideTextStyle(value = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)) {
                        optionContent(option)
                    }
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
            title = stringResource(R.string.library_label_view),
            options = layoutModes,
            selectedOption = preferences.layoutMode,
            onOptionSelected = onLayoutModeChange,
            optionContent = { mode -> Icon(layoutModeIcon(mode), contentDescription = null) })

        val sortTypes = if (isShelvesTab) listOf(
            SortType.Title, SortType.LastRead, SortType.Added, SortType.Progress
        )
        else listOf(
            SortType.Title, SortType.Author, SortType.LastRead, SortType.Added, SortType.Progress
        )

        SortSection(
            options = sortTypes,
            selectedOption = preferences.sortType,
            isAscending = preferences.isAscending,
            onOptionSelected = onSortTypeChange,
            optionLabel = { stringResource(sortTypeLabelRes(it)) },
            isAscendingVisualModifier = { type, asc -> if (type == SortType.LastRead) !asc else asc })

        if (!isShelvesTab) {
            MultiToggleGroupSection(
                title = stringResource(R.string.library_label_status),
                options = StatusFilter.entries,
                selectedOptions = preferences.selectedStatus,
                onOptionToggled = onStatusToggle,
                optionContent = { status ->
                    Text(
                        stringResource(statusLabelRes(status))
                    )
                })
        } else {
            MultiToggleGroupSection(
                title = stringResource(R.string.action_filter),
                options = ShelfFilter.entries,
                selectedOptions = preferences.selectedShelfFilter,
                onOptionToggled = onShelfFilterToggle,
                optionContent = { filter ->
                    Text(
                        if (filter == ShelfFilter.Shelves) stringResource(R.string.library_tab_shelves) else stringResource(
                            R.string.library_label_unshelved
                        )
                    )
                })
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
            title = stringResource(R.string.library_label_view),
            options = layoutModes,
            selectedOption = preferences.layoutMode,
            onOptionSelected = onLayoutModeChange,
            optionContent = { mode -> Icon(layoutModeIcon(mode), contentDescription = null) })

        val sortTypes =
            if (shelfId == "unshelved") SortType.entries.filter { it != SortType.Custom } else SortType.entries
        SortSection(
            options = sortTypes,
            selectedOption = preferences.sortType,
            isAscending = preferences.isAscending,
            onOptionSelected = onSortTypeChange,
            optionLabel = { stringResource(sortTypeLabelRes(it)) },
            isAscendingVisualModifier = { type, asc -> if (type == SortType.LastRead) !asc else asc })

        MultiToggleGroupSection(
            title = stringResource(R.string.library_label_status),
            options = StatusFilter.entries,
            selectedOptions = preferences.selectedStatus,
            onOptionToggled = onStatusToggle,
            optionContent = { status ->
                Text(
                    stringResource(statusLabelRes(status))
                )
            })
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
            title = stringResource(R.string.library_label_view),
            options = layoutModes,
            selectedOption = preferences.layoutMode,
            onOptionSelected = onLayoutModeChange,
            optionContent = { mode -> Icon(layoutModeIcon(mode), contentDescription = null) })

        val sortTypes = listOf(
            SortType.Title, SortType.Author, SortType.LastRead, SortType.Added, SortType.Progress
        )
        SortSection(
            options = sortTypes,
            selectedOption = preferences.sortType,
            isAscending = preferences.isAscending,
            onOptionSelected = onSortTypeChange,
            optionLabel = { stringResource(sortTypeLabelRes(it)) },
            isAscendingVisualModifier = { type, asc -> if (type == SortType.LastRead) !asc else asc })

        MultiToggleGroupSection(
            title = stringResource(R.string.library_label_status),
            options = StatusFilter.entries,
            selectedOptions = preferences.selectedStatus,
            onOptionToggled = onStatusToggle,
            optionContent = { status ->
                Text(
                    stringResource(statusLabelRes(status))
                )
            })
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
            optionLabel = {
                if (it == FilterItemSortType.Label) stringResource(R.string.library_sort_label) else stringResource(
                    R.string.library_sort_size
                )
            })
    }
}

private fun layoutModeIcon(mode: LayoutMode) = when (mode) {
    LayoutMode.Grid -> MaterialSymbols.Outlined.Calendar_view_month
    LayoutMode.BigGrid -> MaterialSymbols.Outlined.Window
    LayoutMode.List -> MaterialSymbols.Outlined.View_list
    LayoutMode.BigList -> MaterialSymbols.Outlined.View_carousel
}

private fun sortTypeLabelRes(type: SortType): Int = when (type) {
    SortType.Title -> R.string.library_sort_title
    SortType.Author -> R.string.library_sort_author
    SortType.LastRead -> R.string.library_sort_last_read
    SortType.Added -> R.string.library_sort_added
    SortType.Progress -> R.string.library_sort_progress
    SortType.Custom -> R.string.library_sort_custom
}

private fun statusLabelRes(status: StatusFilter): Int = when (status) {
    StatusFilter.NotStarted -> R.string.library_status_not_started
    StatusFilter.Reading -> R.string.library_status_reading
    StatusFilter.Finished -> R.string.library_status_finished
}

