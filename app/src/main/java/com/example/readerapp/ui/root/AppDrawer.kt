package com.example.readerapp.ui.root

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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Upload
import com.example.readerapp.R
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
    onNavigateToDictionaries: () -> Unit
) {
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



