package com.example.readerapp.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedButtonGroup(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(option) },
                enabled = enabled
            )
        }
    }
}
