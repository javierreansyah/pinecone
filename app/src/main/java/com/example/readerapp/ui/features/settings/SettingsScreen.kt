package com.example.readerapp.ui.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.ui.features.settings.components.SettingsGroup
import com.example.readerapp.ui.features.settings.components.SettingsItem
import com.example.readerapp.worker.WorkerUtils

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            SettingsGroup {
                SettingsItem(
                    label = "Theme Mode",
                    value = settings.themeMode,
                    options = listOf("System", "Light", "Dark"),
                    onSelected = { viewModel.updateSettings(settings.copy(themeMode = it)) },
                    index = 0,
                    count = 3
                )
                SettingsItem(
                    label = "Color Palette",
                    value = settings.colorPalette,
                    options = listOf("Pine", "Dynamic"),
                    onSelected = { viewModel.updateSettings(settings.copy(colorPalette = it)) },
                    index = 1,
                    count = 3
                )
                SettingsItem(
                    label = "Language",
                    value = settings.locale,
                    options = listOf("System", "English", "Spanish", "French"),
                    onSelected = { viewModel.updateSettings(settings.copy(locale = it)) },
                    index = 2,
                    count = 4
                )
                SettingsItem(
                    label = "Auto Backup Frequency",
                    value = settings.autoBackupFrequency,
                    options = listOf("3h", "6h", "12h", "1d", "2d", "3d", "1w", "never"),
                    onSelected = { 
                        viewModel.updateSettings(settings.copy(autoBackupFrequency = it)) 
                        WorkerUtils.scheduleBackupWork(context, it)
                    },
                    index = 3,
                    count = 4
                )
            }
        }
    }
}
