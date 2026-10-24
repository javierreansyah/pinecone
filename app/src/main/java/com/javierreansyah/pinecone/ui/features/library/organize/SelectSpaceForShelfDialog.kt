package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Forest
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.ui.components.SegmentedColumn

@Composable
fun SelectSpaceForShelfDialog(
    shelfName: String,
    spaces: List<SpaceEntity>,
    onDismiss: () -> Unit,
    titleRes: Int = R.string.library_create_shelf_title,
    confirmRes: Int = R.string.action_create,
    singleSelect: Boolean = false,
    onConfirm: (spaceIds: List<String>) -> Unit,
) {
    var selectedSpaceIds by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(titleRes))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (singleSelect) stringResource(R.string.library_transfer_destination) else stringResource(
                        R.string.library_select_space_prompt,
                        shelfName
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    spaces.forEach { (spaceId, spaceName, _) ->
                        val isSelected = spaceId in selectedSpaceIds
                        item(
                            key = spaceId,
                            selected = isSelected,
                            onClick = {
                                selectedSpaceIds = if (singleSelect) {
                                    setOf(spaceId)
                                } else if (isSelected) {
                                    selectedSpaceIds - spaceId
                                } else {
                                    selectedSpaceIds + spaceId
                                }
                            },
                            leadingContent = {
                                Icon(
                                    MaterialSymbols.Outlined.Forest,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                                )
                            },
                        ) {
                            Text(
                                text = spaceName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedSpaceIds.isNotEmpty()) {
                        onConfirm(selectedSpaceIds.toList())
                    }
                },
                enabled = selectedSpaceIds.isNotEmpty(),
            ) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
