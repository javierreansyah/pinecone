package com.example.readerapp.ui.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val readerPreferences: ReaderPreferences
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = readerPreferences.readerSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderSettings()
        )

    fun updateSettings(newSettings: ReaderSettings) {
        viewModelScope.launch {
            readerPreferences.updateAllSettings(newSettings)
        }
    }

    suspend fun updateSettingsSuspended(newSettings: ReaderSettings) {
        readerPreferences.updateAllSettings(newSettings)
    }
}
