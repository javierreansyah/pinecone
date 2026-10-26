package com.javierreansyah.pinecone.ui.features.library.unsorted

import androidx.activity.compose.BackHandler
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Upload_file
import com.javierreansyah.pinecone.R

@Composable
internal fun UnsortedImportFabMenu(onImportFiles: () -> Unit, onScanFolder: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    val description = stringResource(if (expanded) R.string.action_close else R.string.action_add)
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
                containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                    initialColor = MaterialTheme.colorScheme.secondaryContainer,
                    finalColor = MaterialTheme.colorScheme.secondary,
                ),
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Icon(
                    imageVector = if (checkedProgress > 0.5f) MaterialSymbols.Outlined.Close else MaterialSymbols.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.animateIcon(
                        checkedProgress = { checkedProgress },
                        color = ToggleFloatingActionButtonDefaults.iconColor(
                            initialColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            finalColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onImportFiles()
            },
            icon = { Icon(MaterialSymbols.Outlined.Upload_file, contentDescription = null) },
            text = { Text(stringResource(R.string.nav_import_files)) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onScanFolder()
            },
            icon = { Icon(MaterialSymbols.Outlined.Folder, contentDescription = null) },
            text = { Text(stringResource(R.string.nav_scan_folder)) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    }
}
