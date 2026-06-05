package com.example.readerapp.ui.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.ui.features.settings.components.SettingsItem
import com.example.readerapp.worker.WorkerUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.DocumentsContract
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val readerPreferences = remember { ReaderPreferences(context) }
    val viewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(readerPreferences) as T
            }
        }
    )

    val settings by viewModel.settings.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: android.content.Context, input: Uri?): Intent {
                val intent = super.createIntent(context, input)
                if (input != null) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
                }
                return intent
            }
        },
        onResult = { uri ->
            uri?.let {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                viewModel.updateSettings(settings.copy(backupFolderUri = it.toString()))
            }
        }
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings")},
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onNavigateBack
                    ) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "General",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
            )

            SettingsItem(
                label = "Theme Mode",
                value = settings.themeMode,
                options = listOf("System", "Light", "Dark"),
                onSelected = { viewModel.updateSettings(settings.copy(themeMode = it)) }
            )
            SettingsItem(
                label = "Color Palette",
                value = settings.colorPalette,
                options = listOf("Pine", "Dynamic"),
                onSelected = { viewModel.updateSettings(settings.copy(colorPalette = it)) }
            )
            SettingsItem(
                label = "Language",
                value = settings.locale,
                options = listOf("System", "English", "Spanish", "French"),
                onSelected = { viewModel.updateSettings(settings.copy(locale = it)) }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Backup",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
            )

            SettingsItem(
                label = "Auto Backup Frequency",
                value = settings.autoBackupFrequency,
                options = listOf("3h", "6h", "12h", "1d", "2d", "3d", "1w", "never"),
                onSelected = { 
                    viewModel.updateSettings(settings.copy(autoBackupFrequency = it)) 
                    WorkerUtils.scheduleBackupWork(context, it)
                }
            )
            
            // Backup Location Button
            ListItem(
                headlineContent = { Text("Backup Location") },
                supportingContent = { 
                    Text(
                        if (settings.backupFolderUri.isNotEmpty()) "Selected" 
                        else "Not selected (Auto-backup disabled)"
                    ) 
                },
                trailingContent = {
                    TextButton(onClick = { 
                        val pineconeDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Pinecone")
                        if (!pineconeDir.exists()) {
                            pineconeDir.mkdirs()
                        }
                        val initialUri =
                            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/Pinecone")
                        folderPickerLauncher.launch(initialUri)
                    }) {
                        Text("Select")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
