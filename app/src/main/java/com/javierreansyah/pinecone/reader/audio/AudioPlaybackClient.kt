package com.javierreansyah.pinecone.reader.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.di.ApplicationScope
import com.javierreansyah.pinecone.di.MainDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import timber.log.Timber

/** Media3 client; never owns a publication, navigator, or native player. */
@Singleton
class AudioPlaybackClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AudioPlaybackStore,
    @ApplicationScope private val scope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    private val connectionError = MutableStateFlow<String?>(null)
    val state = combine(store.state, connectionError) { snapshot, error ->
        if (error == null) snapshot else snapshot.copy(error = error)
    }.stateIn(scope, SharingStarted.Eagerly, store.state.value)
    private data class Connection(val generation: String, val controller: MediaController)
    private val controller = MutableStateFlow<Connection?>(null)
    private val maintenance = Mutex()
    private var requestedBookId: String? = null

    init {
        scope.launch {
            store.state.map { it.generation }.distinctUntilChanged().collectLatest { generation ->
                connectionError.value = null
                controller.value?.controller?.release()
                controller.value = null
                if (generation == null) return@collectLatest
                val future = MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, AudioPlaybackService::class.java)))
                    .setListener(object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            if (store.state.value.generation == generation) {
                                this@AudioPlaybackClient.controller.value = null
                                connectionError.value = context.getString(R.string.reader_audio_open_failed)
                            }
                        }
                    }).buildAsync()
                try {
                    val connected = withTimeout(15_000) {
                        suspendCancellableCoroutine { continuation ->
                            future.addListener({
                                try { continuation.resume(future.get()) }
                                catch (error: Exception) { continuation.resumeWithException(error) }
                            }, ContextCompat.getMainExecutor(context))
                            continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
                        }
                    }
                    if (store.state.value.generation == generation) controller.value = Connection(generation, connected)
                    else connected.release()
                } catch (timeout: TimeoutCancellationException) {
                    MediaController.releaseFuture(future)
                    connectionError.value = context.getString(R.string.reader_audio_open_failed)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    MediaController.releaseFuture(future)
                    Timber.e(error, "Could not connect to audiobook service")
                    if (store.state.value.generation == generation) {
                        connectionError.value = context.getString(R.string.reader_audio_open_failed)
                    }
                }
            }
        }
    }

    suspend fun open(bookId: String, initialLocator: Locator? = null, retry: Boolean = false) = maintenance.withLock {
        withContext(mainDispatcher) {
            requestedBookId = bookId
            connectionError.value = null
            context.startService(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_OPEN)
                .putExtra(AudioPlaybackService.EXTRA_BOOK_ID, bookId)
                .putExtra(AudioPlaybackService.EXTRA_RETRY, retry)
                .putExtra(AudioPlaybackService.EXTRA_LOCATOR, initialLocator?.toJSON()?.toString()))
        }
    }

    fun togglePlayback() = command { if (it.playWhenReady) it.pause() else it.play() }
    fun skip(seconds: Int) = command { it.seekTo((it.currentPosition + seconds * 1000L).coerceAtLeast(0)) }
    fun seek(seconds: Double) = command { it.seekTo((seconds * 1000).toLong().coerceAtLeast(0)) }
    fun track(offset: Int) = command {
        if (it.mediaItemCount > 0) it.seekTo((it.currentMediaItemIndex + offset).coerceIn(0, it.mediaItemCount - 1), 0)
    }
    fun go(locator: Locator) = command {
        it.sendCustomCommand(SessionCommand(AudioPlaybackService.COMMAND_GO, Bundle.EMPTY), Bundle().apply {
            putString(AudioPlaybackService.EXTRA_LOCATOR, locator.toJSON().toString())
        })
    }
    fun go(link: Link) = command {
        it.sendCustomCommand(SessionCommand(AudioPlaybackService.COMMAND_LINK, Bundle.EMPTY), Bundle().apply {
            putString(AudioPlaybackService.EXTRA_LINK, link.toJSON().toString())
        })
    }

    private fun command(action: (MediaController) -> Unit) {
        val snapshot = store.state.value
        if (requestedBookId != null && snapshot.bookId != requestedBookId) return
        val bookId = snapshot.bookId ?: return
        scope.launch {
            try {
                withTimeout(15_000) {
                    val ready = if (snapshot.generation != null) snapshot else store.state.first {
                        it.bookId != bookId || it.generation != null || it.error != null
                    }
                    val generation = ready.generation ?: return@withTimeout
                    if (ready.bookId != bookId) return@withTimeout
                    val connected = controller.filterNotNull().first { it.generation == generation }
                    if (store.state.value.generation == generation) action(connected.controller)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Timber.e(error, "Audiobook command failed") }
        }
    }

    suspend fun stop() = withPlaybackStopped { }

    /** Keep new opens outside the interval between acknowledged stop and asset mutation. */
    suspend fun <T> withPlaybackStopped(bookIds: Collection<String>? = null, block: suspend () -> T): T =
        maintenance.withLock {
            withContext(mainDispatcher) {
                val affected = bookIds == null || state.value.bookId in bookIds || requestedBookId in bookIds
                if (affected && (state.value.bookId != null || requestedBookId != null)) {
                    withTimeout(15_000) {
                        suspendCancellableCoroutine { continuation ->
                            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                                    if (!continuation.isActive) return
                                    if (resultCode == 0) continuation.resume(Unit)
                                    else continuation.resumeWithException(IllegalStateException("Playback stop was superseded"))
                                }
                            }
                            context.startService(Intent(context, AudioPlaybackService::class.java)
                                .setAction(AudioPlaybackService.ACTION_STOP)
                                .putExtra(AudioPlaybackService.EXTRA_RECEIVER, receiver))
                        }
                    }
                    requestedBookId = null
                }
            }
            block()
        }
}
