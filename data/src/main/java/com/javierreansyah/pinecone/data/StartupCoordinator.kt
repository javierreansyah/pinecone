package com.javierreansyah.pinecone.data

import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.backup.BackupRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

sealed interface StartupState {
    data object Loading : StartupState
    data object Ready : StartupState
    data class Failed(val cause: Exception) : StartupState
}

/** Critical recovery is shared by every entry point; failures preserve files and can be retried. */
@Singleton
class StartupCoordinator internal constructor(
    private val recover: suspend () -> Unit,
    private val loadSettings: suspend () -> Unit,
) {
    @Inject constructor(backups: BackupRepository, preferences: ReaderPreferences) : this(
        recover = { backups.completePendingSettingsRestore() },
        loadSettings = { preferences.readerSettings.first(); Unit },
    )
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<StartupState>(StartupState.Loading)
    val state = mutableState.asStateFlow()

    suspend fun initialize() = mutex.withLock {
        if (mutableState.value == StartupState.Ready) return@withLock
        mutableState.value = StartupState.Loading
        try {
            recover()
            loadSettings()
            mutableState.value = StartupState.Ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.e(error, "Could not initialize library")
            mutableState.value = StartupState.Failed(error)
            throw error
        }
    }

    suspend fun awaitReady() {
        state.first { it == StartupState.Ready }
    }
}
