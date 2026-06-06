package com.example.readerapp.ui.features.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Arrow_drop_up

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
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val totalSorts = options.size

        options.forEachIndexed { index, type ->
            val isSelected = selectedOption == type
            SegmentedListItem(
                selected = isSelected,
                onClick = { onOptionSelected(type) },
                index = index,
                count = totalSorts,
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
