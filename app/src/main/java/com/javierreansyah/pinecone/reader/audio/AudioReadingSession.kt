@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.reader.audio

import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.withReadingPreferencesFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.readium.adapter.exoplayer.audio.ExoPlayerEngineProvider
import org.readium.adapter.exoplayer.audio.ExoPlayerPreferences
import org.readium.adapter.exoplayer.audio.ExoPlayerSettings
import org.readium.navigator.media.audio.AudioNavigator
import org.readium.navigator.media.audio.AudioNavigatorFactory
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import kotlin.time.Duration.Companion.seconds

data class AudioPlaybackState(
    val bookId: String? = null,
    val title: String = "",
    val chapter: String = "",
    val playing: Boolean = false,
    val loading: Boolean = false,
    val index: Int = 0,
    val elapsedSeconds: Long = 0,
    val durationSeconds: Long? = null,
    val error: String? = null,
)

/** Application-scoped state; the MediaSessionService owns/relinquishes the native player. */
class AudioReadingSession(private val app: PineconeApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()
    val navigator: MutableStateFlow<AudioNavigator<ExoPlayerSettings, ExoPlayerPreferences>?> =
        MutableStateFlow(null)
    val locator: MutableStateFlow<Locator?> = MutableStateFlow(null)
    private var publication: Publication? = null
    private var jobs = emptyList<Job>()

    suspend fun open(bookId: String): Unit = mutex.withLock {
        if (_state.value.bookId == bookId && navigator.value != null) return@withLock
        releasePlayer()
        _state.value = AudioPlaybackState(bookId = bookId, loading = true)
        try {
            val book = requireNotNull(app.libraryRepository.getBook(bookId))
            val pub = requireNotNull(app.libraryRepository.openPublication(book))
            publication = pub
            val originalRendition = app.libraryRepository.rendition(bookId, "original")
            val override = originalRendition?.preferencesJson?.takeUnless { it == "{}" }?.let {
                kotlinx.serialization.json.Json.decodeFromString<com.javierreansyah.pinecone.data.local.preferences.ReaderSettings>(
                    it
                )
            }
            val settings =
                (override ?: app.readerPreferences.readerSettings.first()).formatPreferences
            val factory = AudioNavigatorFactory(pub, ExoPlayerEngineProvider(app))
                ?: error("Publication is not supported by the audio navigator")
            val initial =
                originalRendition?.lastLocatorJson?.let { Locator.fromJSON(JSONObject(it)) }
            val nav = factory.createNavigator(
                initialLocator = initial,
                initialPreferences = ExoPlayerPreferences(
                    speed = settings.audioSpeed,
                    pitch = settings.audioPitch
                )
            ).getOrNull()
                ?: error("Audio engine could not open this publication")
            navigator.value = nav
            _state.value = AudioPlaybackState(bookId = bookId, title = book.book.title)
            jobs = listOf(
                scope.launch {
                    nav.playback.collect { playback ->
                        val duration = nav.readingOrder.items.getOrNull(playback.index)?.duration
                        _state.value = _state.value.copy(
                            chapter = pub.readingOrder.getOrNull(playback.index)?.title
                                ?: app.getString(R.string.reader_track_label, playback.index + 1),
                            playing = playback.playWhenReady,
                            loading = playback.state is AudioNavigator.State.Buffering,
                            index = playback.index, elapsedSeconds = playback.offset.inWholeSeconds,
                            durationSeconds = duration?.inWholeSeconds,
                            error = if (playback.state is AudioNavigator.State.Failure<*>) app.getString(
                                R.string.reader_audio_playback_failed
                            ) else null,
                        )
                    }
                },
                scope.launch {
                    nav.currentLocator.collect {
                        locator.value = it
                        app.libraryRepository.saveReadingPosition(bookId, it)
                    }
                },
                scope.launch {
                    combine(
                        app.readerPreferences.readerSettings,
                        app.libraryRepository.renditionFlow(bookId, "original")
                    ) { global, rendition ->
                        val local = rendition?.preferencesJson?.takeUnless { it == "{}" }?.let {
                            kotlinx.serialization.json.Json.decodeFromString<com.javierreansyah.pinecone.data.local.preferences.ReaderSettings>(
                                it
                            )
                        }
                        if (local == null) global else global.withReadingPreferencesFrom(local)
                    }.collect {
                        nav.submitPreferences(
                            ExoPlayerPreferences(
                                speed = it.formatPreferences.audioSpeed,
                                pitch = it.formatPreferences.audioPitch
                            )
                        )
                    }
                },
            )
        } catch (cancelled: CancellationException) {
            releasePlayer(); throw cancelled
        } catch (error: Exception) {
            releasePlayer()
            timber.log.Timber.e(error, "Could not open audiobook")
            _state.value = AudioPlaybackState(
                bookId = bookId,
                error = app.getString(R.string.reader_audio_open_failed)
            )
        }
    }

    fun togglePlayback() {
        navigator.value?.let { if (state.value.playing) it.pause() else it.play() }
    }

    fun skip(seconds: Int) {
        navigator.value?.skip(seconds.seconds)
    }

    fun seek(seconds: Double) {
        navigator.value?.skipTo(state.value.index, seconds.seconds)
    }

    fun track(offset: Int) {
        navigator.value?.let {
            it.skipTo(
                (state.value.index + offset).coerceIn(
                    0,
                    it.readingOrder.items.lastIndex
                ), 0.seconds
            )
        }
    }

    fun go(locator: Locator) {
        navigator.value?.go(locator)
    }

    fun go(link: Link) {
        navigator.value?.go(link)
    }

    fun releasePlayer() {
        jobs.forEach(Job::cancel); jobs = emptyList()
        navigator.value?.close(); navigator.value = null
        publication?.close(); publication = null; locator.value = null
        _state.value = _state.value.copy(playing = false, loading = false)
    }
}
