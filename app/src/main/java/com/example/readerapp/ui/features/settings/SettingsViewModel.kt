package com.example.readerapp.ui.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val readerPreferences: ReaderPreferences
) : ViewModel() {

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            readerPreferences.readerSettings.collect {
                _settings.value = it
            }
        }
    }

    fun updateSettings(newSettings: ReaderSettings) {
        viewModelScope.launch {
            readerPreferences.updateAllSettings(newSettings)
        }
    }

    suspend fun updateSettingsSuspended(newSettings: ReaderSettings) {
        readerPreferences.updateAllSettings(newSettings)
    }
}
