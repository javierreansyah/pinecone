package com.javierreansyah.pinecone.ui.features.settings.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.javierreansyah.pinecone.ui.components.SegmentedListScope
import com.javierreansyah.pinecone.ui.components.SettingsChoiceDialog

@Composable
fun <T : Any> SegmentedListScope.SettingsItem(
    value: T,
    options: List<T>,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
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
        supportingContent = {
            Text(
                optionLabel(value),
                style = MaterialTheme.typography.bodyMedium
            )
        },
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
            optionLabel = optionLabel,
            onSelected = onSelected,
            onDismiss = { showDialog = false },
            content = content,
        )
    }
}

@Composable
internal fun SegmentedListScope.SettingsSwitchItem(
    title: String, summary: String, checked: Boolean, onChanged: (Boolean) -> Unit,
    icon: ImageVector, enabled: Boolean = true,
) {
    item(
        enabled = enabled,
        onClick = { onChanged(!checked) },
        leadingContent = { Icon(icon, contentDescription = null) },
        content = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
    )
}
