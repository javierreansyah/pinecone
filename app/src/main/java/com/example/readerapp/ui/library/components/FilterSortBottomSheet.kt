package com.example.readerapp.ui.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Arrow_drop_up
import com.composables.icons.materialsymbols.outlined.Check
import com.example.readerapp.ui.library.LayoutMode
import com.example.readerapp.ui.library.LibraryUiState
import com.example.readerapp.ui.library.SortType
import com.example.readerapp.ui.library.StatusFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSortBottomSheet(
    uiState: LibraryUiState,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onDismiss: () -> Unit
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
                .navigationBarsPadding()
        ) {
            // --- VIEW SECTION ---
            Text("View", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LayoutMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.layoutMode == mode,
                        onClick = { onLayoutModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LayoutMode.entries.size),
                        icon = {
                            if (uiState.layoutMode == mode) {
                                Icon(
                                    MaterialSymbols.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                )
                            }
                        }
                    ) {
                        Text(mode.name)
                    }
                }
            }

            // --- SORT SECTION ---
            Spacer(modifier = Modifier.height(24.dp))
            Text("Sort", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(segmentedGap)
            ) {
                val sortEntries = SortType.entries
                val totalSorts = sortEntries.size

                sortEntries.forEachIndexed { index, type ->
                    val isSelected = uiState.sortType == type
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = { onSortTypeChange(type) },
                        index = index,
                        count = totalSorts,
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    if (uiState.isAscending) MaterialSymbols.Outlined.Arrow_drop_up else MaterialSymbols.Outlined.Arrow_drop_down,
                                    contentDescription = null
                                )
                            }
                        },
                        content = { Text(sortTypeLabel(type)) }
                    )
                }
            }

            // --- STATUS SECTION ---
            Spacer(modifier = Modifier.height(24.dp))
            Text("Status", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(segmentedGap)) {
                val statusEntries = StatusFilter.entries
                val totalStatuses = statusEntries.size

                statusEntries.forEachIndexed { index, status ->
                    val isSelected = uiState.selectedStatus.contains(status)
                    SegmentedListItem(
                        checked = isSelected,
                        onCheckedChange = { onStatusToggle(status) },
                        index = index,
                        count = totalStatuses,
                        leadingContent = { Checkbox(checked = isSelected, onCheckedChange = null) },
                        content = { Text(statusLabel(status)) }
                    )
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
}

private fun statusLabel(status: StatusFilter): String = when (status) {
    StatusFilter.NotStarted -> "Not started"
    StatusFilter.Reading -> "Currently reading"
    StatusFilter.Finished -> "Finished"
}
