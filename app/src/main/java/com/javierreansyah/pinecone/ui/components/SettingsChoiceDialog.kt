package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R

/** Single-choice settings use the same selected rows and connected shapes as space selection. */
@Composable
fun <T : Any> SettingsChoiceDialog(
    options: List<T>,
    isSelected: (T) -> Boolean,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    emptyText: String? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = content,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (options.isEmpty() && emptyText != null) {
                    Text(emptyText, style = MaterialTheme.typography.bodyLarge)
                }
                options.forEachIndexed { index, option ->
                    val optionSelected = isSelected(option)
                    key(option) {
                        SegmentedListItem(
                            modifier = Modifier.semantics {
                                role = Role.RadioButton
                                selected = optionSelected
                            },
                            selected = optionSelected,
                            onClick = {
                                onSelected(option)
                                onDismiss()
                            },
                            index = index,
                            count = options.size,
                        ) {
                            Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
