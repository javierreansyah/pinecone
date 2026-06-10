package com.example.readerapp.ui.features.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Contrast
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.composables.icons.materialsymbols.outlined.Palette
import com.composables.icons.materialsymbols.outlined.Sync
import com.composables.icons.materialsymbols.outlined.Translate
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.R
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.ui.components.SegmentedColumn
import com.example.readerapp.ui.features.settings.components.settingsItem
import com.example.readerapp.ui.features.settings.components.ColorSchemePickerDialog
import com.example.readerapp.worker.WorkerUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch
import java.io.File

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
    var showColorPicker by remember { mutableStateOf(false) }

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
                val takeFlags: Int =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                viewModel.updateSettings(settings.copy(backupFolderUri = it.toString()))
            }
        }
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            MaterialSymbols.Outlined.Arrow_back,
                            contentDescription = stringResource(R.string.action_back)
                        )
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
                        val key =
                            themeModeOptionsMap.entries.find { it.value == label }?.key ?: label
                        viewModel.updateSettings(settings.copy(themeMode = key))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Contrast,
                            contentDescription = null
                        )
                    }
                )
                item(
                    onClick = { showColorPicker = true },
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Palette,
                            contentDescription = null
                        )
                    },
                    content = { Text(stringResource(R.string.settings_color_palette), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = {
                        Text(
                            if (settings.colorPalette == "Dynamic") stringResource(R.string.settings_option_dynamic)
                            else settings.colorPalette,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (settings.colorPalette != "Dynamic") {
                                val resolvedColor = try {
                                    Color(settings.colorPalette.toColorInt())
                                } catch (_: Exception) {
                                    null
                                }
                                if (resolvedColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(resolvedColor)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                                contentDescription = null
                            )
                        }
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
                    value = themeContrastOptionsMap[settings.themeContrast]
                        ?: settings.themeContrast,
                    options = themeContrastOptionsMap.values.toList(),
                    onSelected = { label ->
                        val key =
                            themeContrastOptionsMap.entries.find { it.value == label }?.key ?: label
                        viewModel.updateSettings(settings.copy(themeContrast = key))
                    },
                    enabled = settings.colorPalette != "Dynamic",
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Tune,
                            contentDescription = null
                        )
                    }
                )
                val languageLabel = stringResource(R.string.settings_language)
                val languageOptionsMap = mapOf(
                    "System" to stringResource(R.string.settings_option_system),
                    "en" to "English",
                    "id" to "Indonesian"
                )

                val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                var currentTag =
                    if (appLocales.isEmpty) "System" else appLocales.get(0)?.language ?: "System"
                if (currentTag == "in") currentTag = "id"

                settingsItem(
                    label = languageLabel,
                    value = languageOptionsMap[currentTag] ?: currentTag,
                    options = languageOptionsMap.values.toList(),
                    onSelected = { label ->
                        val key =
                            languageOptionsMap.entries.find { it.value == label }?.key ?: label
                        scope.launch {
                            viewModel.updateSettingsSuspended(settings.copy(locale = key))
                            if (key == "System") {
                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                    androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                                )
                            } else {
                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                    androidx.core.os.LocaleListCompat.forLanguageTags(key)
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Translate,
                            contentDescription = null
                        )
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
                val autoBackupOptionsMap = mapOf(
                    "3h" to stringResource(R.string.settings_backup_freq_3h),
                    "6h" to stringResource(R.string.settings_backup_freq_6h),
                    "12h" to stringResource(R.string.settings_backup_freq_12h),
                    "1d" to stringResource(R.string.settings_backup_freq_1d),
                    "2d" to stringResource(R.string.settings_backup_freq_2d),
                    "3d" to stringResource(R.string.settings_backup_freq_3d),
                    "1w" to stringResource(R.string.settings_backup_freq_1w),
                    "never" to stringResource(R.string.settings_option_never)
                )
                settingsItem(
                    label = autoBackupLabel,
                    value = autoBackupOptionsMap[settings.autoBackupFrequency]
                        ?: settings.autoBackupFrequency,
                    options = autoBackupOptionsMap.values.toList(),
                    onSelected = { label ->
                        val key =
                            autoBackupOptionsMap.entries.find { it.value == label }?.key ?: label
                        viewModel.updateSettings(settings.copy(autoBackupFrequency = key))
                        WorkerUtils.scheduleBackupWork(context, key)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Sync,
                            contentDescription = null
                        )
                    }
                )

                // Backup Location Button
                item(
                    onClick = {
                        val pineconeDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                            "Pinecone"
                        )
                        if (!pineconeDir.exists()) {
                            pineconeDir.mkdirs()
                        }
                        val initialUri =
                            DocumentsContract.buildDocumentUri(
                                "com.android.externalstorage.documents",
                                "primary:Documents/Pinecone"
                            )
                        folderPickerLauncher.launch(initialUri)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Folder,
                            contentDescription = null
                        )
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

    if (showColorPicker) {
        val currentPaletteColor = remember(settings.colorPalette) {
            if (settings.colorPalette == "Dynamic") Color.White
            else {
                try {
                    Color(settings.colorPalette.toColorInt())
                } catch (_: Exception) {
                    Color.White
                }
            }
        }
        ColorSchemePickerDialog(
            currentColor = currentPaletteColor,
            setShowDialog = { showColorPicker = it },
            onColorChange = { color ->
                val newPalette = if (color == Color.White) {
                    "Dynamic"
                } else {
                    String.format("#%06X", 0xFFFFFF and color.toArgb())
                }
                viewModel.updateSettings(settings.copy(colorPalette = newPalette))
            }
        )
    }
}
