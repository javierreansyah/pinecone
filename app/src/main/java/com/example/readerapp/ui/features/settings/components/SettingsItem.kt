package com.example.readerapp.ui.features.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right

@Composable
fun SettingsItem(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ListItem(
            headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
            trailingContent = { Icon(MaterialSymbols.Outlined.Keyboard_arrow_right, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
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
