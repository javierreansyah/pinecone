package com.example.readerapp.ui.features.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right

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
    val defaultRadius = 24.dp
    val innerRadius = 4.dp

    val topRadius = if (index == 0) defaultRadius else innerRadius
    val bottomRadius = if (index == count - 1) defaultRadius else innerRadius

    val shape = RoundedCornerShape(
        topStart = topRadius, topEnd = topRadius,
        bottomStart = bottomRadius, bottomEnd = bottomRadius
    )

    Box {
        ListItem(
            headlineContent = { Text(label) },
            supportingContent = { Text(value) },
            trailingContent = { Icon(MaterialSymbols.Outlined.Keyboard_arrow_right, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
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
