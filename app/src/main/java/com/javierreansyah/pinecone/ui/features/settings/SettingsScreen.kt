package com.javierreansyah.pinecone.ui.features.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val app = context.applicationContext as com.javierreansyah.pinecone.PineconeApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            application = app,
            readerPreferences = app.readerPreferences
        )
    )

    val settings by viewModel.settings.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    var showColorPicker by remember { mutableStateOf(false) }
    var showRestoreDefaultWarning by remember { mutableStateOf(false) }

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

    val hasPermission =
        settings.backupFolderUri.isNotEmpty() && context.contentResolver.persistedUriPermissions.any { it.uri.toString() == settings.backupFolderUri }

    var showRestoreWarning by remember { mutableStateOf(false) }
    var showRestoreBottomSheet by remember { mutableStateOf(false) }
    var selectedBackupToRestore by remember { mutableStateOf<BackupFile?>(null) }
    var selectedBackupToExport by remember { mutableStateOf<BackupFile?>(null) }
    val availableBackups by viewModel.availableBackups.collectAsState()
    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

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
                })
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
                })
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingsTopBar(
                onNavigateBack = onNavigateBack,
                onRestoreDefaultClick = { showRestoreDefaultWarning = true },
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
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                            )
                        } else {
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                androidx.core.os.LocaleListCompat.forLanguageTags(localeKey)
                            )
                        }
                    }
                }
            )

            BackupSettingsSection(
                settings = settings,
                hasPermission = hasPermission,
                isBackingUp = isBackingUp,
                isRestoring = isRestoring,
                onAutoBackupFrequencySelected = { frequency ->
                    viewModel.updateSettings(settings.copy(autoBackupFrequency = frequency))
                    WorkerUtils.scheduleBackupWork(context, frequency)
                },
                onBackupLocationClick = {
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
                },
                onBackupClick = {
                    if (!hasPermission) {
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
                    } else {
                        viewModel.performFullBackup(
                            onStart = {
                                Toast.makeText(context, navStartingBackupMsg, Toast.LENGTH_SHORT)
                                    .show()
                            },
                            onSuccess = {
                                Toast.makeText(context, navBackupSuccessMsg, Toast.LENGTH_SHORT)
                                    .show()
                            },
                            onFailure = {
                                Toast.makeText(context, navBackupFailedMsg, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        )
                    }
                },
                onRestoreClick = {
                    if (!hasPermission) {
                        Toast.makeText(context, errorSetBackupLocationMsg, Toast.LENGTH_LONG).show()
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
                    } else {
                        viewModel.loadBackups()
                        showRestoreBottomSheet = true
                    }
                },
                onImportClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/zip"
                        )
                    )
                }
            )

            AboutSettingsSection(
                onNavigateToAbout = onNavigateToAbout
            )
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

    if (showRestoreWarning) {
        AlertDialog(
            onDismissRequest = {
                showRestoreWarning = false
                selectedBackupToRestore = null
            },
            title = { Text(stringResource(R.string.nav_restore_backup)) },
            text = { Text(stringResource(R.string.settings_restore_backup_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreWarning = false
                        selectedBackupToRestore?.uri?.let { uri ->
                            viewModel.restoreFullBackup(
                                uri = uri,
                                onStart = {
                                    Toast.makeText(
                                        context,
                                        navRestoringBackupMsg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onSuccess = {
                                    Toast.makeText(
                                        context,
                                        navRestoreSuccessMsg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onWarning = {
                                    Toast.makeText(
                                        context,
                                        restorePartialMsg,
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onFailure = {
                                    Toast.makeText(context, navRestoreFailedMsg, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            )
                        }
                        selectedBackupToRestore = null
                    }
                ) {
                    Text(stringResource(R.string.action_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreWarning = false
                    selectedBackupToRestore = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showRestoreBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRestoreBottomSheet = false },
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
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                SegmentedColumn {
                                    backups.sortedByDescending { it.timestamp }.forEach { backup ->
                                        item(
                                            onClick = {
                                                selectedBackupToRestore = backup
                                                showRestoreWarning = true
                                                scope.launch { bottomSheetState.hide() }
                                                    .invokeOnCompletion {
                                                        if (!bottomSheetState.isVisible) {
                                                            showRestoreBottomSheet = false
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
                                                    IconButton(onClick = {
                                                        selectedBackupToExport = backup
                                                        exportLauncher.launch("pinecone_${backup.name}.pine")
                                                    }) {
                                                        Icon(
                                                            MaterialSymbols.Outlined.Save,
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

    if (showRestoreDefaultWarning) {
        AlertDialog(
            onDismissRequest = { showRestoreDefaultWarning = false },
            title = { Text(stringResource(R.string.settings_restore_defaults)) },
            text = { Text(stringResource(R.string.settings_restore_defaults_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
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

                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                        )

                        WorkerUtils.scheduleBackupWork(context, "12h")

                        Toast.makeText(
                            context,
                            restoreSuccessMsg,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(stringResource(R.string.action_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDefaultWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    onNavigateBack: () -> Unit,
    onRestoreDefaultClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    LibraryTopAppBar(
        modifier = modifier,
        title = { Text(stringResource(R.string.settings_title)) },
        onBack = onNavigateBack,
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = onRestoreDefaultClick) {
                Icon(
                    MaterialSymbols.Outlined.Restart_alt,
                    contentDescription = stringResource(R.string.settings_restore_defaults)
                )
            }
        }
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
    Column(modifier = modifier) {
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
            SettingsItem(
                label = themeModeLabel,
                value = themeModeOptionsMap[settings.themeMode] ?: settings.themeMode,
                options = themeModeOptionsMap.values.toList(),
                onSelected = { label ->
                    val key =
                        themeModeOptionsMap.entries.find { it.value == label }?.key ?: label
                    onThemeModeSelected(key)
                },
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Contrast,
                        contentDescription = null
                    )
                }
            )
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
                        stringResource(R.string.settings_color_palette),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
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
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
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
            SettingsItem(
                label = themeContrastLabel,
                value = themeContrastOptionsMap[settings.themeContrast]
                    ?: settings.themeContrast,
                options = themeContrastOptionsMap.values.toList(),
                onSelected = { label ->
                    val key =
                        themeContrastOptionsMap.entries.find { it.value == label }?.key ?: label
                    onThemeContrastSelected(key)
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

            SettingsItem(
                label = languageLabel,
                value = languageOptionsMap[currentTag] ?: currentTag,
                options = languageOptionsMap.values.toList(),
                onSelected = { label ->
                    val key =
                        languageOptionsMap.entries.find { it.value == label }?.key ?: label
                    onLanguageSelected(key)
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
