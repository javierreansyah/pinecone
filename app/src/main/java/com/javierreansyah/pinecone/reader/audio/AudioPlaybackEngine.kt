@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.reader.audio

import android.app.Application
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.withReadingPreferencesFrom
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.PublicationRepository
import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import com.javierreansyah.pinecone.data.repository.library.readingSessionToken
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.adapter.exoplayer.audio.ExoPlayerEngineProvider
import org.readium.adapter.exoplayer.audio.ExoPlayerPreferences
import org.readium.adapter.exoplayer.audio.ExoPlayerSettings
import org.readium.navigator.media.audio.AudioNavigator
import org.readium.navigator.media.audio.AudioNavigatorFactory
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import javax.inject.Inject

/** Constructed in the service component; never injected into application or UI components. */
@ServiceScoped
class AudioPlaybackEngine @Inject constructor(
    private val app: Application,
    private val repository: LibraryRepository,
    private val publications: PublicationRepository,
    private val reading: ReadingRepository,
    private val preferences: ReaderPreferences,
    private val store: AudioPlaybackStore,
    private val startup: com.javierreansyah.pinecone.data.StartupCoordinator,
    private val operations: com.javierreansyah.pinecone.data.repository.library.PublicationOperations,
) {
    private val state get() = store.state
    private var navigator: AudioNavigator<ExoPlayerSettings, ExoPlayerPreferences>? = null
    val player get() = navigator?.asMedia3Player()
    private var publication: Publication? = null
    private var jobs = emptyList<Job>()

    suspend fun open(bookId: String, initialLocator: Locator?, scope: CoroutineScope) {
        releasePlayer()
        store.publish(AudioPlaybackState(bookId = bookId, loading = true))
        try {
            startup.initialize()
            val book = requireNotNull(repository.getBook(bookId))
            store.publish(
                state.value.copy(
                    title = book.book.title,
                    author = book.sortedAuthors.joinToString(", ") { it.name },
                    metadataLoaded = true,
                    coverPath = book.book.coverPath,
                )
            )
            // Retain ownership before honoring cancellation so an IO result cannot leak a publication.
            val pub =
                requireNotNull(withContext(NonCancellable) { publications.openPublication(book) })
            publication = pub
            currentCoroutineContext().ensureActive()
            val originalRendition = reading.rendition(bookId, "original")
            val override = originalRendition?.preferencesJson?.takeUnless { it == "{}" }?.let {
                kotlinx.serialization.json.Json.decodeFromString<com.javierreansyah.pinecone.data.local.preferences.ReaderSettings>(
                    it
                )
            }
            val settings =
                (override ?: preferences.readerSettings.first()).formatPreferences
            val factory = AudioNavigatorFactory(pub, ExoPlayerEngineProvider(app))
                ?: error("Publication is not supported by the audio navigator")
            val initial = initialLocator ?: originalRendition?.lastLocatorJson?.let {
                Locator.fromJSON(JSONObject(it))
            }
            val nav = withContext(NonCancellable) {
                factory.createNavigator(
                    initialLocator = initial,
                    initialPreferences = ExoPlayerPreferences(
                        speed = settings.audioSpeed,
                        pitch = settings.audioPitch
                    )
                ).getOrNull()
            }
                ?: error("Audio engine could not open this publication")
            navigator = nav
            currentCoroutineContext().ensureActive()
            store.publish(
                state.value.copy(
                    loading = false,
                    tableOfContents = pub.tableOfContents.ifEmpty {
                        pub.readingOrder.mapIndexed { index, link ->
                            link.copy(
                                title = link.title ?: app.getString(
                                    R.string.reader_track_label,
                                    index + 1
                                )
                            )
                        }
                    },
                )
            )
            jobs = listOf(
                scope.launch {
                    nav.playback.collect { playback ->
                        val duration = nav.readingOrder.items.getOrNull(playback.index)?.duration
                        store.publish(
                            state.value.copy(
                                chapter = pub.readingOrder.getOrNull(playback.index)?.title
                                    ?: app.getString(
                                        R.string.reader_track_label,
                                        playback.index + 1
                                    ),
                                playing = playback.playWhenReady,
                                loading = playback.state is AudioNavigator.State.Buffering,
                                index = playback.index,
                                elapsedSeconds = playback.offset.inWholeSeconds,
                                durationSeconds = duration?.inWholeSeconds,
                                error = if (playback.state is AudioNavigator.State.Failure<*>) app.getString(
                                    R.string.reader_audio_playback_failed
                                ) else null,
                            )
                        )
                    }
                },
                scope.launch {
                    nav.currentLocator.collect {
                        store.update { state -> state.copy(locator = it) }
                        reading.saveReadingPosition(bookId, it, session = pub.readingSessionToken())
                    }
                },
                scope.launch {
                    combine(
                        preferences.readerSettings,
                        reading.renditionFlow(bookId, "original").map { it?.preferencesJson }
                            .distinctUntilChanged()
                    ) { global, encoded ->
                        val local = encoded?.takeUnless { it == "{}" }?.let {
                            kotlinx.serialization.json.Json.decodeFromString<com.javierreansyah.pinecone.data.local.preferences.ReaderSettings>(
                                it
                            )
                        }
                        if (local == null) global else global.withReadingPreferencesFrom(local)
                    }.map { it.formatPreferences.audioSpeed to it.formatPreferences.audioPitch }
                        .distinctUntilChanged().collect { (speed, pitch) ->
                            nav.submitPreferences(
                                ExoPlayerPreferences(
                                    speed = speed,
                                    pitch = pitch
                                )
                            )
                        }
                },
            )
            val invalidation = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                combine(
                    repository.getBookFlow(bookId),
                    operations.libraryState
                ) { details, runtime ->
                    details == null || runtime.second || runtime.first != pub.readingSessionToken()?.generation
                }.first { it }
                val self = currentCoroutineContext()[Job]
                val others = jobs.filter { it != self }
                others.forEach(Job::cancel)
                others.joinAll()
                releasePlayer()
            }
            jobs = jobs + invalidation
            invalidation.start()
        } catch (cancelled: CancellationException) {
            releasePlayer(); throw cancelled
        } catch (error: Exception) {
            releasePlayer()
            timber.log.Timber.e(error, "Could not open audiobook")
            store.publish(
                state.value.copy(
                    loading = false,
                    error = app.getString(R.string.reader_audio_open_failed)
                )
            )
        }
    }

    fun go(locator: Locator) {
        navigator?.go(locator)
    }

    fun go(link: Link) {
        navigator?.go(link)
    }

    suspend fun close() = withContext(NonCancellable) {
        jobs.forEach(Job::cancel)
        jobs.joinAll()
        try {
            val bookId = state.value.bookId
            val lastLocator = state.value.locator
            if (bookId != null && lastLocator != null) reading.saveReadingPosition(
                bookId,
                lastLocator,
                session = publication?.readingSessionToken()
            )
        } finally {
            releasePlayer()
        }
    }

    fun releasePlayer() {
        jobs.forEach(Job::cancel); jobs = emptyList()
        navigator?.close(); navigator = null
        publication?.close(); publication = null
        store.update {
            it.copy(
                playing = false,
                loading = false,
                ready = false,
                generation = null,
                locator = null
            )
        }
    }

}
