package com.javierreansyah.pinecone.ui.features.settings.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.javierreansyah.pinecone.ui.components.SegmentedListScope
import com.javierreansyah.pinecone.ui.components.SettingsChoiceDialog

@Composable
fun SegmentedListScope.SettingsItem(
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var showDialog by remember { mutableStateOf(value = false) }

    item(
        enabled = enabled,
        onClick = { showDialog = true },
        leadingContent = leadingIcon,
        content = content,
        supportingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            Icon(
                MaterialSymbols.Outlined.Keyboard_arrow_right,
                contentDescription = null,
            )
        },
    )

    if (showDialog) {
        SettingsChoiceDialog(
            options = options,
            isSelected = { it == value },
            optionLabel = { it },
            onSelected = onSelected,
            onDismiss = { showDialog = false },
            content = content,
        )
    }
}
