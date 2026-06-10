package com.example.readerapp.ui.root

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Drive_folder_upload
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Upload
import com.example.readerapp.R
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.repository.backup.LibraryBackupRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

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
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.library_archives_title)) },
                icon = { Icon(MaterialSymbols.Outlined.Archive, contentDescription = null) },
                selected = false,
                onClick = { onNavigateToArchives() },
                shape = RectangleShape
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.dictionaries_title)) },
                icon = { Icon(MaterialSymbols.Outlined.Book, contentDescription = null) },
                selected = false,
                onClick = { onNavigateToDictionaries() },
                shape = RectangleShape
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                text = stringResource(R.string.nav_book_import),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.primary,
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_import_files)) },
                icon = { Icon(MaterialSymbols.Outlined.Upload, contentDescription = null) },
                selected = false,
                onClick = {
                    onImportFilesClick()
                    scope.launch { drawerState.close() }
                },
                shape = RectangleShape
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_scan_folder)) },
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
                text = stringResource(R.string.nav_local_backup),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.primary,
            )

            val lastBackupTimeText = if (settings.lastBackupTime > 0) {
                val formatter =
                    SimpleDateFormat("MMM dd, HH:mm", LocalLocale.current.platformLocale)
                "Last: ${formatter.format(Date(settings.lastBackupTime))}"
            } else {
                "Never"
            }

            val hasPermission =
                settings.backupFolderUri.isNotEmpty() && context.contentResolver.persistedUriPermissions.any { it.uri.toString() == settings.backupFolderUri }
            val startingMsg = stringResource(R.string.nav_starting_backup)
            val successMsg = stringResource(R.string.nav_backup_success)
            val failedMsg = stringResource(R.string.nav_backup_failed)

            NavigationDrawerItem(
                label = {
                    Column {
                        if (hasPermission) {
                            Text(stringResource(R.string.nav_backup_now))
                            Text(
                                lastBackupTimeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(stringResource(R.string.nav_backup_not_setup))
                            Text(
                                stringResource(R.string.nav_setup_now),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                icon = {
                    Icon(
                        if (hasPermission) MaterialSymbols.Outlined.Drive_folder_upload else MaterialSymbols.Outlined.Folder,
                        contentDescription = null
                    )
                },
                selected = false,
                onClick = {
                    scope.launch {
                        drawerState.close()
                        if (!hasPermission) {
                            onBackupFolderSetupClick()
                        } else {
                            Toast.makeText(context, startingMsg, Toast.LENGTH_SHORT).show()
                            val success =
                                LibraryBackupRepository(context).performBackup(force = true)
                            if (success) {
                                Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                shape = RectangleShape
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_restore_backup)) },
                icon = { Icon(MaterialSymbols.Outlined.History, contentDescription = null) },
                selected = false,
                onClick = {
                    onRestoreBackupClick()
                },
                shape = RectangleShape
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings_title)) },
                icon = { Icon(MaterialSymbols.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = { onNavigateToSettings() },
                shape = RectangleShape
            )
        }
    }
}



