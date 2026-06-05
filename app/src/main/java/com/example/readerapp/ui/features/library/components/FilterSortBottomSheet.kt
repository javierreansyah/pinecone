package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.example.readerapp.ui.theme.spacing
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Arrow_drop_up
import com.composables.icons.materialsymbols.outlined.Border_all
import com.composables.icons.materialsymbols.outlined.Calendar_view_month
import com.composables.icons.materialsymbols.outlined.View_carousel
import com.composables.icons.materialsymbols.outlined.View_list
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.SortType
import com.example.readerapp.ui.features.library.StatusFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterSortBottomSheet(
    preferences: com.example.readerapp.ui.features.library.FilterSortPreferences,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onDismiss: () -> Unit,
    showViewPicker: Boolean = true,
    showStatusFilter: Boolean = true,
    availableSortTypes: List<SortType> = SortType.entries,
    availableLayoutModes: List<LayoutMode> = listOf(LayoutMode.Grid, LayoutMode.BigGrid, LayoutMode.List)
) {
    val segmentedGap = 4.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.space24)
        ) {
            // --- VIEW SECTION ---
            if (showViewPicker) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                    Text("View", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        availableLayoutModes.forEachIndexed { index, mode ->
                            val isSelected = preferences.layoutMode == mode
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = { onLayoutModeChange(mode) },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    availableLayoutModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton }
                            ) {
                                val icon = when (mode) {
                                    LayoutMode.Grid -> MaterialSymbols.Outlined.Calendar_view_month
                                    LayoutMode.BigGrid -> MaterialSymbols.Outlined.Border_all
                                    LayoutMode.List -> MaterialSymbols.Outlined.View_list
                                    LayoutMode.BigList -> MaterialSymbols.Outlined.View_carousel
                                }
                                Icon(icon, contentDescription = null)
                            }
                        }
                    }
                }
            }

            // --- SORT SECTION ---
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                Text("Sort", style = MaterialTheme.typography.titleMedium)

                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(segmentedGap)
                ) {
                    val totalSorts = availableSortTypes.size

                    availableSortTypes.forEachIndexed { index, type ->
                        val isSelected = preferences.sortType == type
                        SegmentedListItem(
                            selected = isSelected,
                            onClick = { onSortTypeChange(type) },
                            index = index,
                            count = totalSorts,
                            leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                            trailingContent = {
                                if (isSelected) {
                                    val isAscendingVisual = if (type == SortType.LastRead) !preferences.isAscending else preferences.isAscending
                                    Icon(
                                        if (isAscendingVisual) MaterialSymbols.Outlined.Arrow_drop_up else MaterialSymbols.Outlined.Arrow_drop_down,
                                        contentDescription = null
                                    )
                                }
                            },
                            content = { Text(sortTypeLabel(type), style = MaterialTheme.typography.bodyLarge) }
                        )
                    }
                }
            }

            // --- STATUS SECTION ---
            if (showStatusFilter) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val statusEntries = StatusFilter.entries
                        statusEntries.forEachIndexed { index, status ->
                            val isSelected = preferences.selectedStatus.contains(status)
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = { onStatusToggle(status) },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    statusEntries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                            ) {
                                Text(statusLabel(status), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
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
