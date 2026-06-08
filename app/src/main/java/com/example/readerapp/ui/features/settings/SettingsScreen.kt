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
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.ui.features.settings.components.settingsItem
import com.example.readerapp.worker.WorkerUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.example.readerapp.ui.components.SegmentedColumn
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                title = { Text(stringResource(R.string.settings_title))},
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onNavigateBack
                    ) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_general),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val colorPaletteOptionsMap = mapOf(
                "Dynamic" to stringResource(R.string.settings_option_dynamic),
                "Pine" to stringResource(R.string.settings_theme_pine),
                "Neutral" to stringResource(R.string.settings_theme_neutral)
            )
            
            SegmentedColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                val themeModeLabel = stringResource(R.string.settings_theme_mode)
                val themeModeOptionsMap = mapOf(
                    "System" to stringResource(R.string.settings_option_system),
                    "Light" to stringResource(R.string.settings_option_light),
                    "Dark" to stringResource(R.string.settings_option_dark)
                )
                settingsItem(
                    label = themeModeLabel,
                    value = themeModeOptionsMap[settings.themeMode] ?: settings.themeMode,
                    options = themeModeOptionsMap.values.toList(),
                    onSelected = { label -> 
                        val key = themeModeOptionsMap.entries.find { it.value == label }?.key ?: label
                        viewModel.updateSettings(settings.copy(themeMode = key)) 
                    }
                )
                val colorPaletteLabel = stringResource(R.string.settings_color_palette)
                settingsItem(
                    label = colorPaletteLabel,
                    value = colorPaletteOptionsMap[settings.colorPalette] ?: settings.colorPalette,
                    options = colorPaletteOptionsMap.values.toList(),
                    onSelected = { label ->
                        val key = colorPaletteOptionsMap.entries.find { it.value == label }?.key ?: label
                        viewModel.updateSettings(settings.copy(colorPalette = key))
                    }
                )
                val themeContrastLabel = stringResource(R.string.settings_theme_contrast)
                val themeContrastOptionsMap = mapOf(
                    "Standard" to stringResource(R.string.settings_option_standard),
                    "Medium" to stringResource(R.string.settings_option_medium),
                    "High" to stringResource(R.string.settings_option_high)
                )
                settingsItem(
                    label = themeContrastLabel,
                    value = themeContrastOptionsMap[settings.themeContrast] ?: settings.themeContrast,
                    options = themeContrastOptionsMap.values.toList(),
                    onSelected = { label ->
                        val key = themeContrastOptionsMap.entries.find { it.value == label }?.key ?: label
                        viewModel.updateSettings(settings.copy(themeContrast = key))
                    },
                    enabled = settings.colorPalette != "Dynamic"
                )
                val languageLabel = stringResource(R.string.settings_language)
                val languageOptionsMap = mapOf(
                    "System" to stringResource(R.string.settings_option_system),
                    "en" to "English",
                    "id" to "Indonesian"
                )
                
                val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                var currentTag = if (appLocales.isEmpty) "System" else appLocales.get(0)?.language ?: "System"
                if (currentTag == "in") currentTag = "id"

                settingsItem(
                    label = languageLabel,
                    value = languageOptionsMap[currentTag] ?: currentTag,
                    options = languageOptionsMap.values.toList(),
                    onSelected = { label -> 
                        val key = languageOptionsMap.entries.find { it.value == label }?.key ?: label
                        scope.launch {
                            viewModel.updateSettingsSuspended(settings.copy(locale = key))
                            if (key == "System") {
                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
                            } else {
                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(key))
                            }
                        }
                    }
                )
            }

            Text(
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SegmentedColumn {
                val autoBackupLabel = stringResource(R.string.settings_auto_backup_freq)
                settingsItem(
                    label = autoBackupLabel,
                    value = settings.autoBackupFrequency,
                    options = listOf("3h", "6h", "12h", "1d", "2d", "3d", "1w", "never"),
                    onSelected = { 
                        viewModel.updateSettings(settings.copy(autoBackupFrequency = it)) 
                        WorkerUtils.scheduleBackupWork(context, it)
                    }
                )
                
                // Backup Location Button
                item(
                    onClick = { 
                        val pineconeDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Pinecone")
                        if (!pineconeDir.exists()) {
                            pineconeDir.mkdirs()
                        }
                        val initialUri =
                            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/Pinecone")
                        folderPickerLauncher.launch(initialUri)
                    },
                    content = { Text(stringResource(R.string.settings_backup_location)) },
                    supportingContent = { 
                        val selectedText = stringResource(R.string.settings_option_selected)
                        val notSelectedText = stringResource(R.string.settings_option_not_selected)
                        Text(
                            if (settings.backupFolderUri.isNotEmpty()) selectedText 
                            else notSelectedText
                        ) 
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}
