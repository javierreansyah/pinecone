package com.javierreansyah.pinecone.ui.features.reader.components.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversionProgressAction(page: Int, total: Int, color: Color, onCancel: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(R.string.reader_preparing_read_mode)
    Box {
        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { contentDescription = title }) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = color, strokeWidth = 2.dp)
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
                interactionSource = remember { MutableInteractionSource() },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_conversion_progress, page, total)) },
                    onClick = {},
                    enabled = false,
                    shape = MenuDefaults.leadingItemShape,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_cancel)) },
                    onClick = { expanded = false; onCancel() },
                    shape = MenuDefaults.trailingItemShape,
                )
            }
        }
    }
}
