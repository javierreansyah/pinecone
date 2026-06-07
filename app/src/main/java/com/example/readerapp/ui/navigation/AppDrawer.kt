package com.example.readerapp.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Upload
import com.composables.icons.materialsymbols.outlined.Book
import com.example.readerapp.R
import com.example.readerapp.data.local.ReaderSettings
import com.example.readerapp.data.repository.BackupRepository
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    settings: ReaderSettings,
    darkTheme: Boolean,
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
    onBackupFolderSetupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onNavigateToDictionaries: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ModalDrawerSheet {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    id = if (darkTheme) R.drawable.dark_mode_icon else R.drawable.light_mode_icon
                ),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
            }

            NavigationDrawerItem(
                label = { Text("Archives") },
                icon = { Icon(MaterialSymbols.Outlined.Archive, contentDescription = null) },
                selected = false,
                onClick = { onNavigateToArchives() },
                shape = RectangleShape
            )

            NavigationDrawerItem(
                label = { Text("Dictionaries") },
                icon = { Icon(MaterialSymbols.Outlined.Book, contentDescription = null) },
                selected = false,
                onClick = { onNavigateToDictionaries() },
                shape = RectangleShape
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                text = "Book Import",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.primary,
            )

            NavigationDrawerItem(
                label = { Text("Import Files") },
                icon = { Icon(MaterialSymbols.Outlined.Upload, contentDescription = null) },
                selected = false,
                onClick = {
                    onImportFilesClick()
                    scope.launch { drawerState.close() }
                },
                shape = RectangleShape
            )
            NavigationDrawerItem(
                label = { Text("Scan Folder") },
                icon = { Icon(MaterialSymbols.Outlined.Folder, contentDescription = null) },
                selected = false,
                onClick = {
                    onScanFolderClick()
                    scope.launch { drawerState.close() }
                },
                shape = RectangleShape
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                text = "Local Backup",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.primary,
            )

            val lastBackupText = if (settings.lastBackupTime > 0) {
                val formatter = java.text.SimpleDateFormat("MMM dd, HH:mm", LocalLocale.current.platformLocale)
                "Last: ${formatter.format(java.util.Date(settings.lastBackupTime))}"
            } else {
                "Never"
            }

            val hasPermission = settings.backupFolderUri.isNotEmpty() && context.contentResolver.persistedUriPermissions.any { it.uri.toString() == settings.backupFolderUri }

            NavigationDrawerItem(
                label = {
                    Column {
                        if (hasPermission) {
                            Text("Backup Now")
                            Text(lastBackupText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("Backup not setup")
                            Text("Setup now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                icon = { Icon(if (hasPermission) Icons.Outlined.DriveFolderUpload else MaterialSymbols.Outlined.Folder, contentDescription = null) },
                selected = false,
                onClick = {
                    scope.launch {
                        drawerState.close()
                        if (!hasPermission) {
                            onBackupFolderSetupClick()
                        } else {
                            Toast.makeText(context, "Starting backup...", Toast.LENGTH_SHORT).show()
                            val success = BackupRepository(context).performBackup(force = true)
                            if (success) {
                                Toast.makeText(context, "Backup successful", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                shape = RectangleShape
            )

            NavigationDrawerItem(
                label = { Text("Restore Backup") },
                icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                selected = false,
                onClick = {
                    onRestoreBackupClick()
                },
                shape = RectangleShape
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(MaterialSymbols.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = { onNavigateToSettings() },
                shape = RectangleShape
            )
        }
    }
}
