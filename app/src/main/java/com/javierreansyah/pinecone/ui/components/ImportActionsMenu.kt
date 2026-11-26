package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Upload
import com.javierreansyah.pinecone.R

@Composable
fun ImportActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        AppDropdownMenuGroup {
            AppDropdownMenuItem(
                text = { Text(stringResource(R.string.nav_import_files)) },
                shape = AppDropdownMenuDefaults.itemShape(0, 2).shape,
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Upload,
                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onImportFilesClick()
                },
            )
            AppDropdownMenuItem(
                text = { Text(stringResource(R.string.nav_scan_folder)) },
                shape = AppDropdownMenuDefaults.itemShape(1, 2).shape,
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Folder,
                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onScanFolderClick()
                },
            )
        }
    }
}
