package com.javierreansyah.pinecone.ui.features.reader

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.local.preferences.encodeReadingOverrides
import com.javierreansyah.pinecone.data.local.preferences.withReadingPreferencesFrom
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackClient
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@HiltViewModel(assistedFactory = AudioPlayerViewModel.Factory::class)
class AudioPlayerViewModel @AssistedInject constructor(
    @Assisted private val bookId: String,
    private val playback: AudioPlaybackClient,
    private val application: Application,
    private val repository: LibraryRepository,
    private val reading: ReadingRepository,
    private val preferences: ReaderPreferences,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val openingError = MutableStateFlow<String?>(null)
    private val settingsError = MutableStateFlow<String?>(null)
    val state = combine(playback.state, openingError, settingsError) { current, openError, localError ->
        val error = openError ?: localError
        val matching = if (current.bookId == bookId) current else AudioPlaybackState(bookId = bookId, loading = true)
        if (error == null) matching else matching.copy(loading = false, error = error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AudioPlaybackState(bookId = bookId, loading = true))
    private var opened = false
    private val settingsMutex = Mutex()
    private val localSettings = reading.renditionFlow(bookId, "original").map { it?.preferencesJson }.distinctUntilChanged().map {
        try {
            val local = decodeLocalSettings(it)
            settingsError.value = null
            local
        } catch (error: IllegalArgumentException) {
            timber.log.Timber.e(error, "Could not decode audiobook settings")
            settingsError.value = application.getString(R.string.reader_audio_open_failed)
            null
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    val useBookSettings = localSettings.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val settings = combine(preferences.readerSettings, localSettings) { global, local ->
        if (local == null) global else global.withReadingPreferencesFrom(local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())
    val jumpOrigin = repository.getBookFlow(bookId).map {
        it?.book?.jumpOriginLocatorJson?.let { encoded -> runCatching { Locator.fromJSON(JSONObject(encoded)) }.getOrNull() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            settings.map { it.jumpHistoryMode }.distinctUntilChanged().collect { mode ->
                if (mode == "disabled") reading.clearJumpOrigin(bookId)
            }
        }
    }

    fun getPositionLabel(locator: Locator): String = locator.locations.totalProgression?.let {
        application.getString(R.string.reader_position_at, "${(it * 100).toInt()}%")
    }.orEmpty()

    fun open(initialLocatorJson: String? = null, retry: Boolean = false) {
        if (opened && !retry) return
        opened = true
        viewModelScope.launch {
            openingError.value = null
            try {
                val initial = if (savedState.get<Boolean>("initialLocatorConsumed") == true) null
                    else initialLocatorJson?.let { Locator.fromJSON(JSONObject(it)) }
                playback.open(bookId, initial, retry)
                savedState["initialLocatorConsumed"] = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                timber.log.Timber.e(error, "Could not request audiobook")
                openingError.value = application.getString(R.string.reader_audio_open_failed)
            }
        }
    }
    private fun forCurrentBook(action: () -> Unit) {
        if (playback.state.value.bookId == bookId) action()
    }

    fun togglePlayback() = forCurrentBook { playback.togglePlayback() }
    fun skip(seconds: Int) = forCurrentBook { playback.skip(seconds) }
    fun seek(seconds: Double) = forCurrentBook { playback.seek(seconds) }
    fun track(offset: Int) = forCurrentBook { playback.track(offset) }
    fun go(locator: Locator) = forCurrentBook { recordJumpOrigin(); playback.go(locator) }
    fun go(link: Link) = forCurrentBook { recordJumpOrigin(); playback.go(link) }
    private fun recordJumpOrigin() {
        val locator = state.value.locator ?: return
        viewModelScope.launch {
            if (settings.first().jumpHistoryMode != "disabled") reading.saveJumpOrigin(bookId, locator)
        }
    }
    fun returnToOrigin() = forCurrentBook {
        jumpOrigin.value?.let(playback::go)
        clearJumpOrigin()
    }
    fun clearJumpOrigin() { viewModelScope.launch { reading.clearJumpOrigin(bookId) } }

    private fun decodeLocalSettings(encoded: String?): ReaderSettings? =
        encoded?.takeUnless { it == "{}" }?.let { Json.decodeFromString<ReaderSettings>(it) }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        if (settingsError.value != null) return
        viewModelScope.launch {
            settingsMutex.withLock {
                val encoded = reading.rendition(bookId, "original")?.preferencesJson
                val local = decodeLocalSettings(encoded)
                if (local == null) preferences.updateSettings(transform)
                else {
                    val current = preferences.readerSettings.first().withReadingPreferencesFrom(local)
                    reading.setReadingOverrides(bookId, "original", ReaderSettings().withReadingPreferencesFrom(transform(current)).encodeReadingOverrides())
                }
            }
        }
    }
    fun setUseBookSettings(enabled: Boolean) {
        viewModelScope.launch {
            settingsMutex.withLock {
                if (!enabled) {
                    reading.setReadingOverrides(bookId, "original", "{}")
                    return@withLock
                }
                val encoded = reading.rendition(bookId, "original")?.preferencesJson
                val local = decodeLocalSettings(encoded)
                val global = preferences.readerSettings.first()
                val current = if (local == null) global else global.withReadingPreferencesFrom(local)
                reading.setReadingOverrides(
                    bookId, "original", ReaderSettings().withReadingPreferencesFrom(current).encodeReadingOverrides(),
                )
            }
        }
    }
    @AssistedFactory interface Factory { fun create(bookId: String): AudioPlayerViewModel }
}
