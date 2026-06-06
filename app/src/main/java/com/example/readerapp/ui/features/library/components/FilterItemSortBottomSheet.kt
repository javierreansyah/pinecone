package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readerapp.ui.theme.spacing
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Arrow_drop_up

enum class FilterItemSortType { Label, Size }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterItemSortBottomSheet(
    currentSortType: FilterItemSortType,
    isAscending: Boolean,
    onSortTypeChange: (FilterItemSortType) -> Unit,
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
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.space24)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                Text("Sort", style = MaterialTheme.typography.titleMedium)

                SortRadioGroup(
                    options = FilterItemSortType.entries,
                    selectedOption = currentSortType,
                    isAscending = isAscending,
                    onOptionSelected = onSortTypeChange,
                    optionLabel = { if (it == FilterItemSortType.Label) "Label" else "Size" }
                )
            }
        }
    }
}
