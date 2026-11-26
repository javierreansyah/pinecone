package com.javierreansyah.pinecone.ui.features.settings

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Contrast
import com.composables.icons.materialsymbols.outlined.Dark_mode
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Forest
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.Image
import com.composables.icons.materialsymbols.outlined.Inbox
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.composables.icons.materialsymbols.outlined.Palette
import com.composables.icons.materialsymbols.outlined.Restart_alt
import com.composables.icons.materialsymbols.outlined.Save
import com.composables.icons.materialsymbols.outlined.Swap_horiz
import com.composables.icons.materialsymbols.outlined.Translate
import com.composables.icons.materialsymbols.outlined.Tune
import com.composables.icons.materialsymbols.outlined.Visibility_off
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.CoverDisplayMode
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.backup.BackupProgress
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconButton
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBarTitle
import com.javierreansyah.pinecone.ui.components.ListItemAccessory
import com.javierreansyah.pinecone.ui.components.SegmentedListDefaults
import com.javierreansyah.pinecone.ui.components.TopAppBarActionGroup
import com.javierreansyah.pinecone.ui.features.settings.components.ColorSchemePickerDialog
import com.javierreansyah.pinecone.ui.features.settings.components.SettingsItem
import com.javierreansyah.pinecone.ui.features.settings.components.SettingsSwitchItem
import com.javierreansyah.pinecone.ui.features.settings.components.formatBackupFolderLabel
import com.javierreansyah.pinecone.ui.features.settings.components.rememberBackupFolderPickerLauncher
import com.javierreansyah.pinecone.ui.features.settings.components.rememberRestoreBackupAction
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val navStartingBackupMsg = stringResource(R.string.nav_starting_backup)
    val navBackupSuccessMsg = stringResource(R.string.nav_backup_success)
    val navBackupFailedMsg = stringResource(R.string.nav_backup_failed)
    val restoreSuccessMsg = stringResource(R.string.settings_restore_defaults_success)

    val scope = rememberCoroutineScope()
    val viewModel: SettingsViewModel = hiltViewModel()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBackingUp by viewModel.isBackingUp.collectAsStateWithLifecycle()
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val restoreProgress by viewModel.restoreProgress.collectAsStateWithLifecycle()

    var showColorPicker by remember { mutableStateOf(value = false) }
    var showRestoreDefaultWarning by remember { mutableStateOf(value = false) }

    val launchFolderPicker = rememberBackupFolderPickerLauncher { uri ->
        viewModel.updateSettings { current -> current.copy(backupFolderUri = uri.toString()) }
    }
    val onRestoreClick = rememberRestoreBackupAction(
        viewModel = viewModel,
        showProgressDialog = false,
    )

    val hasPermission = settings.backupFolderUri.isNotEmpty() &&
            context.contentResolver.persistedUriPermissions.any { it.uri.toString() == settings.backupFolderUri }

    SettingsContent(
        settings = settings,
        hasPermission = hasPermission,
        isBackingUp = isBackingUp,
        backupProgress = backupProgress,
        isRestoring = isRestoring,
        restoreProgress = restoreProgress,
        onNavigateBack = onNavigateBack,
        onNavigateToAbout = onNavigateToAbout,
        onRestoreDefaultClick = { showRestoreDefaultWarning = true },
        onThemeModeSelected = { themeMode ->
            viewModel.updateSettings { current -> current.copy(themeMode = themeMode) }
        },
        onColorPaletteClick = { showColorPicker = true },
        onAmoledDarkModeChanged = { enabled ->
            viewModel.updateSettings { current -> current.copy(amoledDarkMode = enabled) }
        },
        onThemeContrastSelected = { themeContrast ->
            viewModel.updateSettings { current -> current.copy(themeContrast = themeContrast) }
        },
        onSpacesEnabledChanged = { enabled ->
            viewModel.updateSettings { current ->
                current.copy(
                    spacesEnabled = enabled
                )
            }
        },
        onShowAllSpacesChanged = { show ->
            viewModel.updateSettings { current ->
                current.copy(
                    showAllSpaces = show
                )
            }
        },
        onUnsortedEnabledChanged = { enabled ->
            viewModel.updateSettings { current ->
                current.copy(
                    unsortedEnabled = enabled
                )
            }
        },
        onLibraryBottomNavigationChanged = { enabled ->
            viewModel.updateSettings { current -> current.copy(showLibraryBottomNavigation = enabled) }
        },
        onCoverDisplayModeSelected = { mode ->
            viewModel.updateSettings { current -> current.copy(coverDisplayMode = mode) }
        },
        onLanguageSelected = { localeKey ->
            scope.launch {
                viewModel.updateSettingsSuspended { current -> current.copy(locale = localeKey) }
                if (localeKey == "System") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                } else {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(
                            localeKey,
                        ),
                    )
                }
            }
        },
        onAutoBackupFrequencySelected = { frequency ->
            viewModel.updateSettings { current -> current.copy(autoBackupFrequency = frequency) }
        },
        onBackupLocationClick = launchFolderPicker,
        onBackupClick = {
            if (!hasPermission) {
                launchFolderPicker()
            } else {
                val appContext = context.applicationContext
                viewModel.performFullBackup(
                    onStart = {
                        Toast.makeText(appContext, navStartingBackupMsg, Toast.LENGTH_SHORT).show()
                    },
                    onSuccess = {
                        Toast.makeText(appContext, navBackupSuccessMsg, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Toast.makeText(appContext, navBackupFailedMsg, Toast.LENGTH_SHORT).show()
                }
            }
        },
        onRestoreClick = onRestoreClick,
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
        ) { color ->
            val newPalette = if (color == Color.White) {
                "Dynamic"
            } else {
                String.format("#%06X", 0xFFFFFF and color.toArgb())
            }
            viewModel.updateSettings { current -> current.copy(colorPalette = newPalette) }
        }
    }

    if (showRestoreDefaultWarning) {
        RestoreDefaultWarningDialog(
            onDismiss = { showRestoreDefaultWarning = false },
        ) {
            showRestoreDefaultWarning = false
            viewModel.updateSettings { current ->
                current.copy(
                    themeMode = "System",
                    colorPalette = "Dynamic",
                    amoledDarkMode = true,
                    themeContrast = "Standard",
                    locale = "System",
                    autoBackupFrequency = "12h",
                    backupFolderUri = "",
                    showAllSpaces = true,
                    spacesEnabled = true,
                    unsortedEnabled = true,
                    showLibraryBottomNavigation = true,
                    coverDisplayMode = CoverDisplayMode.Contain,
                )
            }

            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.getEmptyLocaleList(),
            )

            Toast.makeText(
                context,
                restoreSuccessMsg,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

@Composable
private fun SettingsContent(
    settings: ReaderSettings,
    hasPermission: Boolean,
    isBackingUp: Boolean,
    backupProgress: BackupProgress,
    isRestoring: Boolean,
    restoreProgress: BackupProgress,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onRestoreDefaultClick: () -> Unit,
    onThemeModeSelected: (String) -> Unit,
    onColorPaletteClick: () -> Unit,
    onAmoledDarkModeChanged: (Boolean) -> Unit,
    onThemeContrastSelected: (String) -> Unit,
    onSpacesEnabledChanged: (Boolean) -> Unit,
    onShowAllSpacesChanged: (Boolean) -> Unit,
    onUnsortedEnabledChanged: (Boolean) -> Unit,
    onLibraryBottomNavigationChanged: (Boolean) -> Unit,
    onCoverDisplayModeSelected: (CoverDisplayMode) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onAutoBackupFrequencySelected: (String) -> Unit,
    onBackupLocationClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingsTopBar(
                onNavigateBack = onNavigateBack,
                onRestoreDefaultClick = onRestoreDefaultClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(bottom = LocalAudioPlayerBottomPadding.current)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LanguageSettingsSection(settings, onLanguageSelected)
            LibrarySettingsSection(
                settings = settings,
                onCoverDisplayModeSelected = onCoverDisplayModeSelected,
                onLibraryBottomNavigationChanged = onLibraryBottomNavigationChanged,
                onUnsortedEnabledChanged = onUnsortedEnabledChanged,
            )
            SpaceSettingsSection(
                settings = settings,
                onSpacesEnabledChanged = onSpacesEnabledChanged,
                onShowAllSpacesChanged = onShowAllSpacesChanged,
            )
            ThemeSettingsSection(
                settings = settings,
                onThemeModeSelected = onThemeModeSelected,
                onColorPaletteClick = onColorPaletteClick,
                onAmoledDarkModeChanged = onAmoledDarkModeChanged,
                onThemeContrastSelected = onThemeContrastSelected,
            )

            BackupSettingsSection(
                settings = settings,
                hasPermission = hasPermission,
                isBackingUp = isBackingUp,
                backupProgress = backupProgress,
                isRestoring = isRestoring,
                restoreProgress = restoreProgress,
                onAutoBackupFrequencySelected = onAutoBackupFrequencySelected,
                onBackupLocationClick = onBackupLocationClick,
                onBackupClick = onBackupClick,
                onRestoreClick = onRestoreClick,
            )

            AboutSettingsSection(
                onNavigateToAbout = onNavigateToAbout,
            )
        }
    }
}

@Composable
private fun SettingsTopBar(
    onNavigateBack: () -> Unit,
    onRestoreDefaultClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    LibraryTopAppBar(
        title = { LibraryTopAppBarTitle(stringResource(R.string.settings_title)) },
        onBack = onNavigateBack,
        actions = {
            TopAppBarActionGroup {
                ConnectedGroupIconButton(
                    onClick = onRestoreDefaultClick,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Restart_alt,
                        contentDescription = stringResource(R.string.settings_restore_defaults),
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(SegmentedListDefaults.Gap),
            modifier = Modifier.padding(bottom = 16.dp),
        ) { content() }
    }
}

@Composable
private fun LanguageSettingsSection(
    settings: ReaderSettings,
    onLanguageSelected: (String) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_language)) {
        val languagesMap = mapOf(
            "System" to stringResource(R.string.settings_option_system),
            "en" to "English",
            "id" to "Bahasa Indonesia",
        )

        val languageLabel = stringResource(R.string.settings_language)
        val localeKey = if (settings.locale == "in") "id" else settings.locale

        SettingsItem(
            shapes = SegmentedListDefaults.shapes(index = 0, count = 1),
            content = { Text(languageLabel) },
            value = localeKey.takeIf { it in languagesMap } ?: "System",
            options = languagesMap.keys.toList(),
            onSelected = onLanguageSelected,
            optionLabel = languagesMap::getValue,
            leadingIcon = {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Translate,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun LibrarySettingsSection(
    settings: ReaderSettings,
    onCoverDisplayModeSelected: (CoverDisplayMode) -> Unit,
    onLibraryBottomNavigationChanged: (Boolean) -> Unit,
    onUnsortedEnabledChanged: (Boolean) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_library)) {
        val coverModes = mapOf(
            CoverDisplayMode.Contain to stringResource(R.string.settings_cover_contain),
            CoverDisplayMode.Cover to stringResource(R.string.settings_cover_cover),
        )
        SettingsItem(
            shapes = SegmentedListDefaults.shapes(index = 0, count = 3),
            content = {
                Text(
                    stringResource(R.string.settings_cover_display),
                )
            },
            value = settings.coverDisplayMode,
            options = coverModes.keys.toList(),
            onSelected = onCoverDisplayModeSelected,
            optionLabel = coverModes::getValue,
            leadingIcon = { Icon(MaterialSymbols.Outlined.Image, contentDescription = null) },
        )
        SettingsSwitchItem(
            shapes = SegmentedListDefaults.shapes(index = 1, count = 3),
            title = stringResource(R.string.settings_library_bottom_navigation),
            summary = stringResource(R.string.settings_library_bottom_navigation_summary),
            checked = settings.showLibraryBottomNavigation,
            onChanged = onLibraryBottomNavigationChanged,
            icon = MaterialSymbols.Outlined.Swap_horiz,
        )
        SettingsSwitchItem(
            shapes = SegmentedListDefaults.shapes(index = 2, count = 3),
            title = stringResource(R.string.settings_unsorted_enabled),
            summary = stringResource(R.string.settings_unsorted_enabled_summary),
            checked = settings.unsortedEnabled,
            onChanged = onUnsortedEnabledChanged,
            icon = MaterialSymbols.Outlined.Inbox,
        )
    }
}

@Composable
private fun SpaceSettingsSection(
    settings: ReaderSettings,
    onSpacesEnabledChanged: (Boolean) -> Unit,
    onShowAllSpacesChanged: (Boolean) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_space)) {
        SettingsSwitchItem(
            shapes = SegmentedListDefaults.shapes(index = 0, count = 2),
            title = stringResource(R.string.settings_spaces_enabled),
            summary = stringResource(R.string.settings_spaces_enabled_summary),
            checked = settings.spacesEnabled,
            onChanged = onSpacesEnabledChanged,
            icon = MaterialSymbols.Outlined.Forest,
        )
        SettingsSwitchItem(
            shapes = SegmentedListDefaults.shapes(index = 1, count = 2),
            title = stringResource(R.string.settings_hide_all_space),
            summary = stringResource(R.string.settings_hide_all_space_summary),
            checked = !settings.showAllSpaces,
            onChanged = { onShowAllSpacesChanged(!it) },
            icon = MaterialSymbols.Outlined.Visibility_off,
            enabled = settings.spacesEnabled,
        )
    }
}

@Composable
private fun ThemeSettingsSection(
    settings: ReaderSettings,
    onThemeModeSelected: (String) -> Unit,
    onColorPaletteClick: () -> Unit,
    onAmoledDarkModeChanged: (Boolean) -> Unit,
    onThemeContrastSelected: (String) -> Unit,
) {
    SettingsSection(stringResource(R.string.reader_settings_theme)) {
        val themeOptionsMap = mapOf(
            "System" to stringResource(R.string.settings_option_system),
            "Light" to stringResource(R.string.settings_option_light),
            "Dark" to stringResource(R.string.settings_option_dark),
        )

        val themeLabel = stringResource(R.string.settings_theme_mode)
        SettingsItem(
            shapes = SegmentedListDefaults.shapes(index = 0, count = 4),
            content = { Text(themeLabel) },
            value = settings.themeMode,
            options = themeOptionsMap.keys.toList(),
            onSelected = onThemeModeSelected,
            optionLabel = { themeOptionsMap[it] ?: it },
            leadingIcon = {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Contrast,
                    contentDescription = null,
                )
            },
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

        SegmentedListItem(
            verticalAlignment = Alignment.Top,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            shapes = SegmentedListDefaults.shapes(index = 1, count = 4),
            onClick = onColorPaletteClick,
            leadingContent = {
                ListItemAccessory {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Palette,
                        contentDescription = null,
                    )
                }
            },
            content = {
                Text(
                    colorPaletteLabel,
                )
            },
            supportingContent = {
                Text(
                    colorPaletteValue,
                )
            },
            trailingContent = {
                ListItemAccessory {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        CircleShape,
                                    ),
                            )
                        }
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        SettingsSwitchItem(
            shapes = SegmentedListDefaults.shapes(index = 2, count = 4),
            title = stringResource(R.string.settings_amoled_dark_mode),
            summary = stringResource(R.string.settings_amoled_dark_mode_summary),
            checked = settings.amoledDarkMode,
            onChanged = onAmoledDarkModeChanged,
            icon = MaterialSymbols.Outlined.Dark_mode,
        )

        val themeContrastMap = mapOf(
            "Standard" to stringResource(R.string.settings_option_standard),
            "Medium" to stringResource(R.string.settings_option_medium),
            "High" to stringResource(R.string.settings_option_high),
        )

        val themeContrastLabel = stringResource(R.string.settings_theme_contrast)
        SettingsItem(
            shapes = SegmentedListDefaults.shapes(index = 3, count = 4),
            content = {
                Text(
                    themeContrastLabel,
                )
            },
            value = settings.themeContrast,
            options = themeContrastMap.keys.toList(),
            onSelected = onThemeContrastSelected,
            optionLabel = { themeContrastMap[it] ?: it },
            leadingIcon = {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Contrast,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun BackupSettingsSection(
    settings: ReaderSettings,
    hasPermission: Boolean,
    isBackingUp: Boolean,
    backupProgress: BackupProgress,
    isRestoring: Boolean,
    restoreProgress: BackupProgress,
    onAutoBackupFrequencySelected: (String) -> Unit,
    onBackupLocationClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_backup_preferences),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedListDefaults.Gap),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                val backupLocationLabel = stringResource(R.string.settings_backup_location)
                SegmentedListItem(
                    verticalAlignment = Alignment.Top,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shapes = SegmentedListDefaults.shapes(index = 0, count = 4),
                    onClick = onBackupLocationClick,
                    leadingContent = {
                        ListItemAccessory {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Folder,
                                contentDescription = null,
                            )
                        }
                    },
                    content = {
                        Text(
                            backupLocationLabel,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (hasPermission) {
                                formatBackupFolderLabel(settings.backupFolderUri)
                                    ?: stringResource(R.string.settings_option_selected)
                            } else {
                                stringResource(R.string.settings_option_not_selected)
                            },
                        )
                    },
                    trailingContent = {
                        ListItemAccessory {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                                contentDescription = null,
                            )
                        }
                    },
                )

                val frequencyOptionsMap = mapOf(
                    "6h" to stringResource(R.string.settings_backup_freq_6h),
                    "12h" to stringResource(R.string.settings_backup_freq_12h),
                    "1d" to stringResource(R.string.settings_backup_freq_1d),
                    "3d" to stringResource(R.string.settings_backup_freq_3d),
                    "1w" to stringResource(R.string.settings_backup_freq_1w),
                    "never" to stringResource(R.string.settings_option_never),
                )

                val autoBackupFrequencyLabel = stringResource(R.string.settings_auto_backup_freq)
                SettingsItem(
                    shapes = SegmentedListDefaults.shapes(index = 1, count = 4),
                    content = {
                        Text(
                            autoBackupFrequencyLabel,
                        )
                    },
                    value = settings.autoBackupFrequency,
                    options = frequencyOptionsMap.keys.toList(),
                    onSelected = onAutoBackupFrequencySelected,
                    optionLabel = { frequencyOptionsMap[it] ?: it },
                    enabled = hasPermission,
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Tune,
                            contentDescription = null,
                        )
                    },
                )

                val lastBackupTimeText = if (settings.lastBackupTime > 0) {
                    val formatter =
                        SimpleDateFormat("MMM dd, HH:mm", LocalLocale.current.platformLocale)
                    stringResource(
                        R.string.settings_last_backup_format,
                        formatter.format(Date(settings.lastBackupTime)),
                    )
                } else {
                    stringResource(R.string.settings_option_never)
                }

                SegmentedListItem(
                    verticalAlignment = Alignment.Top,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shapes = SegmentedListDefaults.shapes(index = 2, count = 4),
                    enabled = hasPermission && !isBackingUp && !isRestoring,
                    onClick = onBackupClick,
                    leadingContent = {
                        ListItemAccessory {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Save,
                                contentDescription = null,
                            )
                        }
                    },
                    content = {
                        Text(
                            stringResource(R.string.settings_backup_now),
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                if (hasPermission) {
                                    stringResource(
                                        R.string.settings_backup_now_summary,
                                        lastBackupTimeText,
                                    ).replace(" (", "\n(")
                                } else {
                                    stringResource(R.string.nav_setup_now)
                                },
                            )
                            val progressSpatialSpec =
                                MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()
                            val progressEffectsSpec =
                                MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                            val animatedBackupFraction by animateFloatAsState(
                                targetValue = backupProgress.fraction.coerceIn(0f, 1f),
                                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                label = "backupProgressFraction",
                            )
                            AnimatedVisibility(
                                visible = isBackingUp,
                                enter = expandVertically(animationSpec = progressSpatialSpec) +
                                        fadeIn(animationSpec = progressEffectsSpec),
                                exit = shrinkVertically(animationSpec = progressSpatialSpec) +
                                        fadeOut(animationSpec = progressEffectsSpec),
                            ) {
                                Column {
                                    Text(
                                        if (backupProgress.totalFiles == 0) {
                                            stringResource(R.string.settings_backup_preparing)
                                        } else {
                                            stringResource(
                                                R.string.settings_backup_progress,
                                                backupProgress.completedFiles,
                                                backupProgress.totalFiles,
                                            )
                                        },
                                    )
                                    LinearWavyProgressIndicator(
                                        progress = { animatedBackupFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = {
                        ListItemAccessory {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                                contentDescription = null,
                            )
                        }
                    },
                )

                SegmentedListItem(
                    verticalAlignment = Alignment.Top,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shapes = SegmentedListDefaults.shapes(index = 3, count = 4),
                    enabled = hasPermission && !isBackingUp && !isRestoring,
                    onClick = onRestoreClick,
                    leadingContent = {
                        ListItemAccessory {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.History,
                                contentDescription = null,
                            )
                        }
                    },
                    content = {
                        Text(
                            stringResource(R.string.settings_restore),
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                stringResource(R.string.settings_restore_summary),
                            )
                            val restoreSpatialSpec =
                                MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()
                            val restoreEffectsSpec =
                                MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                            val animatedRestoreFraction by animateFloatAsState(
                                targetValue = restoreProgress.fraction.coerceIn(0f, 1f),
                                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                label = "restoreProgressFraction",
                            )
                            AnimatedVisibility(
                                visible = isRestoring,
                                enter = expandVertically(animationSpec = restoreSpatialSpec) +
                                        fadeIn(animationSpec = restoreEffectsSpec),
                                exit = shrinkVertically(animationSpec = restoreSpatialSpec) +
                                        fadeOut(animationSpec = restoreEffectsSpec),
                            ) {
                                Column {
                                    Text(
                                        if (restoreProgress.totalFiles == 0) {
                                            stringResource(R.string.settings_restore_preparing)
                                        } else {
                                            stringResource(
                                                R.string.settings_backup_progress,
                                                restoreProgress.completedFiles,
                                                restoreProgress.totalFiles,
                                            )
                                        },
                                    )
                                    LinearWavyProgressIndicator(
                                        progress = { animatedRestoreFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = {
                        ListItemAccessory {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutSettingsSection(
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_about_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedListDefaults.Gap),
        ) {
            SegmentedListItem(
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shapes = SegmentedListDefaults.shapes(index = 0, count = 1),
                onClick = onNavigateToAbout,
                leadingContent = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Info,
                        contentDescription = null,
                    )
                },
                content = {
                    Text(
                        stringResource(R.string.settings_about_app),
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun RestoreDefaultWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_restore_defaults)) },
        text = { Text(stringResource(R.string.settings_restore_defaults_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_proceed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
