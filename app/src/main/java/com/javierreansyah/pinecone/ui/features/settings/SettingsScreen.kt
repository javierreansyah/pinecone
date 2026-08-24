package com.javierreansyah.pinecone.ui.features.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Contrast
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.composables.icons.materialsymbols.outlined.Palette
import com.composables.icons.materialsymbols.outlined.Restart_alt
import com.composables.icons.materialsymbols.outlined.Save
import com.composables.icons.materialsymbols.outlined.Translate
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.SegmentedColumn
import com.javierreansyah.pinecone.ui.features.settings.components.ColorSchemePickerDialog
import com.javierreansyah.pinecone.ui.features.settings.components.SettingsItem
import com.javierreansyah.pinecone.worker.WorkerUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val navRestoringBackupMsg = stringResource(R.string.nav_restoring_backup)
    val navRestoreSuccessMsg = stringResource(R.string.nav_restore_success)
    val navRestoreFailedMsg = stringResource(R.string.nav_restore_failed)
    val restorePartialMsg = stringResource(R.string.settings_restore_partial)
    val navStartingBackupMsg = stringResource(R.string.nav_starting_backup)
    val navBackupSuccessMsg = stringResource(R.string.nav_backup_success)
    val navBackupFailedMsg = stringResource(R.string.nav_backup_failed)
    val restoreSuccessMsg = stringResource(R.string.settings_restore_defaults_success)
    val errorSetBackupLocationMsg = stringResource(R.string.settings_error_set_backup_location)

    val scope = rememberCoroutineScope()
    val app = context.applicationContext as PineconeApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            application = app,
            readerPreferences = app.readerPreferences
        )
    )

    val settings by viewModel.settings.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val availableBackups by viewModel.availableBackups.collectAsState()

    var showColorPicker by remember { mutableStateOf(false) }
    var showRestoreDefaultWarning by remember { mutableStateOf(false) }
    var showRestoreWarning by remember { mutableStateOf(false) }
    var showRestoreBottomSheet by remember { mutableStateOf(false) }
    var selectedBackupToRestore by remember { mutableStateOf<BackupFile?>(null) }
    var selectedBackupToExport by remember { mutableStateOf<BackupFile?>(null) }

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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destination ->
        val selected = selectedBackupToExport
        if (destination != null && selected != null) {
            viewModel.exportBackup(
                selected.uri, destination,
                onSuccess = {
                    Toast.makeText(context, navBackupSuccessMsg, Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(context, navBackupFailedMsg, Toast.LENGTH_SHORT).show()
                }
            )
        }
        selectedBackupToExport = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { source ->
        source?.let {
            viewModel.importBackup(
                it,
                onSuccess = {
                    Toast.makeText(context, navBackupSuccessMsg, Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(context, navBackupFailedMsg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val hasPermission = settings.backupFolderUri.isNotEmpty() &&
            context.contentResolver.persistedUriPermissions.any { it.uri.toString() == settings.backupFolderUri }

    val launchFolderPicker = {
        val pineconeDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Pinecone"
        )
        if (!pineconeDir.exists()) {
            pineconeDir.mkdirs()
        }
        val initialUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents/Pinecone"
        )
        folderPickerLauncher.launch(initialUri)
    }

    SettingsContent(
        settings = settings,
        hasPermission = hasPermission,
        isBackingUp = isBackingUp,
        isRestoring = isRestoring,
        onNavigateBack = onNavigateBack,
        onNavigateToAbout = onNavigateToAbout,
        onRestoreDefaultClick = { showRestoreDefaultWarning = true },
        onThemeModeSelected = { themeMode ->
            viewModel.updateSettings(settings.copy(themeMode = themeMode))
        },
        onColorPaletteClick = { showColorPicker = true },
        onThemeContrastSelected = { themeContrast ->
            viewModel.updateSettings(settings.copy(themeContrast = themeContrast))
        },
        onLanguageSelected = { localeKey ->
            scope.launch {
                viewModel.updateSettingsSuspended(settings.copy(locale = localeKey))
                if (localeKey == "System") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeKey))
                }
            }
        },
        onAutoBackupFrequencySelected = { frequency ->
            viewModel.updateSettings(settings.copy(autoBackupFrequency = frequency))
            WorkerUtils.scheduleBackupWork(context, frequency)
        },
        onBackupLocationClick = launchFolderPicker,
        onBackupClick = {
            if (!hasPermission) {
                launchFolderPicker()
            } else {
                viewModel.performFullBackup(
                    onStart = {
                        Toast.makeText(context, navStartingBackupMsg, Toast.LENGTH_SHORT).show()
                    },
                    onSuccess = {
                        Toast.makeText(context, navBackupSuccessMsg, Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(context, navBackupFailedMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        },
        onRestoreClick = {
            if (!hasPermission) {
                Toast.makeText(context, errorSetBackupLocationMsg, Toast.LENGTH_LONG).show()
                launchFolderPicker()
            } else {
                viewModel.loadBackups()
                showRestoreBottomSheet = true
            }
        },
        onImportClick = {
            importLauncher.launch(arrayOf("application/octet-stream", "application/zip"))
        }
    )

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

    if (showRestoreWarning) {
        RestoreBackupWarningDialog(
            onDismiss = {
                showRestoreWarning = false
                selectedBackupToRestore = null
            },
            onConfirm = {
                showRestoreWarning = false
                selectedBackupToRestore?.uri?.let { uri ->
                    viewModel.restoreFullBackup(
                        uri = uri,
                        onStart = {
                            Toast.makeText(context, navRestoringBackupMsg, Toast.LENGTH_SHORT).show()
                        },
                        onSuccess = {
                            Toast.makeText(context, navRestoreSuccessMsg, Toast.LENGTH_SHORT).show()
                        },
                        onWarning = {
                            Toast.makeText(context, restorePartialMsg, Toast.LENGTH_LONG).show()
                        },
                        onFailure = {
                            Toast.makeText(context, navRestoreFailedMsg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                selectedBackupToRestore = null
            }
        )
    }

    if (showRestoreDefaultWarning) {
        RestoreDefaultWarningDialog(
            onDismiss = { showRestoreDefaultWarning = false },
            onConfirm = {
                showRestoreDefaultWarning = false
                val defaultSettings = settings.copy(
                    themeMode = "System",
                    colorPalette = "Dynamic",
                    themeContrast = "Standard",
                    locale = "System",
                    autoBackupFrequency = "12h",
                    backupFolderUri = ""
                )
                viewModel.updateSettings(defaultSettings)

                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.getEmptyLocaleList()
                )

                WorkerUtils.scheduleBackupWork(context, "12h")

                Toast.makeText(
                    context,
                    restoreSuccessMsg,
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showRestoreBottomSheet) {
        RestoreBottomSheet(
            availableBackups = availableBackups,
            onDismiss = { showRestoreBottomSheet = false },
            onSelectBackup = { backup ->
                selectedBackupToRestore = backup
                showRestoreBottomSheet = false
                showRestoreWarning = true
            },
            onExportBackup = { backup ->
                selectedBackupToExport = backup
                exportLauncher.launch("pinecone_${backup.name}.pine")
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    settings: ReaderSettings,
    hasPermission: Boolean,
    isBackingUp: Boolean,
    isRestoring: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onRestoreDefaultClick: () -> Unit,
    onThemeModeSelected: (String) -> Unit,
    onColorPaletteClick: () -> Unit,
    onThemeContrastSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onAutoBackupFrequencySelected: (String) -> Unit,
    onBackupLocationClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingsTopBar(
                onNavigateBack = onNavigateBack,
                onRestoreDefaultClick = onRestoreDefaultClick,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GeneralSettingsSection(
                settings = settings,
                onThemeModeSelected = onThemeModeSelected,
                onColorPaletteClick = onColorPaletteClick,
                onThemeContrastSelected = onThemeContrastSelected,
                onLanguageSelected = onLanguageSelected
            )

            BackupSettingsSection(
                settings = settings,
                hasPermission = hasPermission,
                isBackingUp = isBackingUp,
                isRestoring = isRestoring,
                onAutoBackupFrequencySelected = onAutoBackupFrequencySelected,
                onBackupLocationClick = onBackupLocationClick,
                onBackupClick = onBackupClick,
                onRestoreClick = onRestoreClick,
                onImportClick = onImportClick
            )

            AboutSettingsSection(
                onNavigateToAbout = onNavigateToAbout
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsTopBar(
    onNavigateBack: () -> Unit,
    onRestoreDefaultClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    LibraryTopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        onBack = onNavigateBack,
        actions = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = onRestoreDefaultClick
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Restart_alt,
                    contentDescription = stringResource(R.string.settings_restore_defaults)
                )
            }
        },
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}

@Composable
private fun GeneralSettingsSection(
    settings: ReaderSettings,
    onThemeModeSelected: (String) -> Unit,
    onColorPaletteClick: () -> Unit,
    onThemeContrastSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_general),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SegmentedColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                val languagesMap = mapOf(
                    "System" to stringResource(R.string.settings_option_system),
                    "en" to "English",
                    "id" to "Bahasa Indonesia",
                    "de" to "Deutsch",
                    "zh" to "中文 (简体)"
                )

                val languageLabel = stringResource(R.string.settings_language)
                val currentLanguageName = languagesMap[settings.locale] ?: settings.locale

                SettingsItem(
                    label = languageLabel,
                    value = currentLanguageName,
                    options = languagesMap.values.toList(),
                    onSelected = { selectedName ->
                        val selectedKey =
                            languagesMap.entries.find { it.value == selectedName }?.key
                                ?: selectedName
                        onLanguageSelected(selectedKey)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Translate,
                            contentDescription = null
                        )
                    }
                )
            }
        }

        Column {
            Text(
                text = stringResource(R.string.reader_settings_theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SegmentedColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                val themeOptionsMap = mapOf(
                    "System" to stringResource(R.string.settings_option_system),
                    "Light" to stringResource(R.string.settings_option_light),
                    "Dark" to stringResource(R.string.settings_option_dark)
                )

                val themeLabel = stringResource(R.string.settings_theme_mode)
                SettingsItem(
                    label = themeLabel,
                    value = themeOptionsMap[settings.themeMode] ?: settings.themeMode,
                    options = themeOptionsMap.values.toList(),
                    onSelected = { label ->
                        val key = themeOptionsMap.entries.find { it.value == label }?.key ?: label
                        onThemeModeSelected(key)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Contrast,
                            contentDescription = null
                        )
                    }
                )

                val colorPaletteLabel = stringResource(R.string.settings_color_palette)
                val colorPaletteValue =
                    if (settings.colorPalette == "Dynamic") stringResource(R.string.settings_option_dynamic) else settings.colorPalette
                val isDynamic = settings.colorPalette == "Dynamic"

                val parsedColor = remember(settings.colorPalette) {
                    if (isDynamic) null
                    else {
                        try {
                            Color(settings.colorPalette.toColorInt())
                        } catch (_: Exception) {
                            null
                        }
                    }
                }

                item(
                    onClick = onColorPaletteClick,
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Palette,
                            contentDescription = null
                        )
                    },
                    content = {
                        Text(
                            colorPaletteLabel,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            colorPaletteValue,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isDynamic && parsedColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(parsedColor)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                                contentDescription = null
                            )
                        }
                    }
                )

                val themeContrastMap = mapOf(
                    "Standard" to stringResource(R.string.settings_option_standard),
                    "Medium" to stringResource(R.string.settings_option_medium),
                    "High" to stringResource(R.string.settings_option_high)
                )

                val themeContrastLabel = stringResource(R.string.settings_theme_contrast)
                SettingsItem(
                    label = themeContrastLabel,
                    value = themeContrastMap[settings.themeContrast] ?: settings.themeContrast,
                    options = themeContrastMap.values.toList(),
                    onSelected = { label ->
                        val key =
                            themeContrastMap.entries.find { it.value == label }?.key ?: label
                        onThemeContrastSelected(key)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Contrast,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BackupSettingsSection(
    settings: ReaderSettings,
    hasPermission: Boolean,
    isBackingUp: Boolean,
    isRestoring: Boolean,
    onAutoBackupFrequencySelected: (String) -> Unit,
    onBackupLocationClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_backup_preferences),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SegmentedColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                val backupLocationLabel = stringResource(R.string.settings_backup_location)
                item(
                    onClick = onBackupLocationClick,
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Folder,
                            contentDescription = null
                        )
                    },
                    content = {
                        Text(
                            backupLocationLabel,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            if (hasPermission) stringResource(R.string.settings_option_selected)
                            else stringResource(R.string.settings_option_not_selected),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null
                        )
                    }
                )

                val frequencyOptionsMap = mapOf(
                    "6h" to stringResource(R.string.settings_backup_freq_6h),
                    "12h" to stringResource(R.string.settings_backup_freq_12h),
                    "1d" to stringResource(R.string.settings_backup_freq_1d),
                    "3d" to stringResource(R.string.settings_backup_freq_3d),
                    "1w" to stringResource(R.string.settings_backup_freq_1w),
                    "never" to stringResource(R.string.settings_option_never)
                )

                val autoBackupFrequencyLabel = stringResource(R.string.settings_auto_backup_freq)
                SettingsItem(
                    label = autoBackupFrequencyLabel,
                    value = frequencyOptionsMap[settings.autoBackupFrequency]
                        ?: settings.autoBackupFrequency,
                    options = frequencyOptionsMap.values.toList(),
                    onSelected = { label ->
                        val key =
                            frequencyOptionsMap.entries.find { it.value == label }?.key ?: label
                        onAutoBackupFrequencySelected(key)
                    },
                    enabled = hasPermission,
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Tune,
                            contentDescription = null
                        )
                    }
                )

                val lastBackupTimeText = if (settings.lastBackupTime > 0) {
                    val formatter =
                        SimpleDateFormat("MMM dd, HH:mm", LocalLocale.current.platformLocale)
                    stringResource(
                        R.string.settings_last_backup_format,
                        formatter.format(Date(settings.lastBackupTime))
                    )
                } else {
                    stringResource(R.string.settings_option_never)
                }

                item(
                    enabled = hasPermission,
                    onClick = onBackupClick,
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Save,
                            contentDescription = null
                        )
                    },
                    content = {
                        Text(
                            stringResource(R.string.settings_backup_now),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                if (hasPermission) {
                                    stringResource(
                                        R.string.settings_backup_now_summary,
                                        lastBackupTimeText
                                    ).replace(" (", "\n(")
                                } else {
                                    stringResource(R.string.nav_setup_now)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            AnimatedVisibility(visible = isBackingUp) {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null
                        )
                    }
                )

                item(
                    enabled = hasPermission,
                    onClick = onRestoreClick,
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.History,
                            contentDescription = null
                        )
                    },
                    content = {
                        Text(
                            stringResource(R.string.settings_restore),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                stringResource(R.string.settings_restore_summary),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            AnimatedVisibility(visible = isRestoring) {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null
                        )
                    }
                )

                item(
                    enabled = hasPermission,
                    onClick = onImportClick,
                    leadingContent = {
                        Icon(MaterialSymbols.Outlined.Folder, contentDescription = null)
                    },
                    content = {
                        Text(
                            stringResource(R.string.settings_import_backup),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_import_backup_summary),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingContent = {
                        Icon(
                            MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AboutSettingsSection(
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_about_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SegmentedColumn(modifier = Modifier.padding(bottom = 16.dp)) {
            item(
                onClick = onNavigateToAbout,
                leadingContent = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Info,
                        contentDescription = null
                    )
                },
                content = {
                    Text(
                        stringResource(R.string.settings_about_app),
                        style = MaterialTheme.typography.titleMedium
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

@Composable
private fun RestoreDefaultWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_restore_defaults)) },
        text = { Text(stringResource(R.string.settings_restore_defaults_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_proceed),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RestoreBackupWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nav_restore_backup)) },
        text = { Text(stringResource(R.string.settings_restore_backup_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_proceed),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreBottomSheet(
    availableBackups: List<BackupFile>,
    onDismiss: () -> Unit,
    onSelectBackup: (BackupFile) -> Unit,
    onExportBackup: (BackupFile) -> Unit
) {
    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_restore_backup_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (availableBackups.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_backups_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                val groupedBackups = availableBackups.groupBy { it.isManual }
                val groups = groupedBackups.entries.sortedByDescending { entry ->
                    entry.value.maxOfOrNull { it.timestamp } ?: 0L
                }

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groups.forEach { (isManual, backups) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isManual) stringResource(R.string.settings_backup_manual_label)
                                else stringResource(R.string.settings_backup_auto_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SegmentedColumn {
                                backups.sortedByDescending { it.timestamp }.forEach { backup ->
                                    item(
                                        onClick = {
                                            onSelectBackup(backup)
                                            scope.launch { bottomSheetState.hide() }
                                                .invokeOnCompletion {
                                                    if (!bottomSheetState.isVisible) {
                                                        onDismiss()
                                                    }
                                                }
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = MaterialSymbols.Outlined.History,
                                                contentDescription = null
                                            )
                                        },
                                        content = {
                                            Text(
                                                text = backup.formattedDate,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        },
                                        trailingContent = {
                                            Row {
                                                IconButton(onClick = { onExportBackup(backup) }) {
                                                    Icon(
                                                        imageVector = MaterialSymbols.Outlined.Save,
                                                        contentDescription = stringResource(R.string.settings_export_backup)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
