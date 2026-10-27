package com.javierreansyah.pinecone.ui.features.settings.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.javierreansyah.pinecone.ui.components.ListItemAccessory
import com.javierreansyah.pinecone.ui.components.SettingsChoiceDialog

@Composable
fun <T : Any> SettingsItem(
    shapes: ListItemShapes,
    value: T,
    options: List<T>,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var showDialog by remember { mutableStateOf(value = false) }

    SegmentedListItem(
        verticalAlignment = Alignment.Top,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shapes = shapes,
        enabled = enabled,
        onClick = { showDialog = true },
        leadingContent = leadingIcon?.let { icon -> { ListItemAccessory { icon() } } },
        content = content,
        supportingContent = {
            Text(
                optionLabel(value),
            )
        },
        trailingContent = {
            ListItemAccessory {
                Icon(
                    MaterialSymbols.Outlined.Keyboard_arrow_right,
                    contentDescription = null,
                )
            }
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
internal fun SettingsSwitchItem(
    shapes: ListItemShapes,
    title: String, summary: String, checked: Boolean, onChanged: (Boolean) -> Unit,
    icon: ImageVector, enabled: Boolean = true,
) {
    SegmentedListItem(
        verticalAlignment = Alignment.Top,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shapes = shapes,
        enabled = enabled,
        onClick = { onChanged(!checked) },
        leadingContent = { ListItemAccessory { Icon(icon, contentDescription = null) } },
        content = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            ListItemAccessory {
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = null
                )
            }
        },
    )
}
