package com.example.readerapp.ui.features.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.example.readerapp.ui.features.library.components.SegmentedListItem

@Composable
fun SettingsItem(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    index: Int,
    count: Int
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SegmentedListItem(
            selected = false,
            onClick = { expanded = true },
            index = index,
            count = count,
            content = { Text(label, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
            trailingContent = { Icon(MaterialSymbols.Outlined.Keyboard_arrow_right, contentDescription = null) }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.titleMedium) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == value) {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}
