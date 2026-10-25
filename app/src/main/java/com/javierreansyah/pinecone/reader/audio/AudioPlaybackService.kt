@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.reader.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {
    companion object {
        internal const val ACTION_OPEN = "com.javierreansyah.pinecone.audio.OPEN"
        internal const val ACTION_STOP = "com.javierreansyah.pinecone.audio.STOP"
        internal const val EXTRA_RETRY = "retry"
        internal const val EXTRA_BOOK_ID = "bookId"
        internal const val EXTRA_LOCATOR = "locator"
        internal const val EXTRA_LINK = "link"
        internal const val EXTRA_RECEIVER = "receiver"
        internal const val COMMAND_GO = "com.javierreansyah.pinecone.audio.GO"
        internal const val COMMAND_LINK = "com.javierreansyah.pinecone.audio.LINK"
    }

    @Inject lateinit var engine: AudioPlaybackEngine
    @Inject lateinit var store: AudioPlaybackStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, error ->
            timber.log.Timber.e(error, "Audiobook background operation failed")
            releaseSession()
            engine.releasePlayer()
            store.update { it.copy(loading = false, error = getString(com.javierreansyah.pinecone.R.string.reader_audio_playback_failed)) }
        })
    private var session: MediaSession? = null
    private var transition: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OPEN || intent?.action == ACTION_STOP) {
            val previous = transition
            previous?.cancel()
            transition = scope.launch {
                val receiver = intent.getParcelableExtra(EXTRA_RECEIVER, ResultReceiver::class.java)
                var completed = false
                try {
                    previous?.join()
                    val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
                    if (intent.action == ACTION_OPEN && bookId != null && store.state.value.bookId == bookId && session != null && store.state.value.error == null && !intent.getBooleanExtra(EXTRA_RETRY, false)) {
                        intent.getStringExtra(EXTRA_LOCATOR)?.let { Locator.fromJSON(JSONObject(it)) }?.let(engine::go)
                        return@launch
                    }
                    releaseSession()
                    engine.close()
                    currentCoroutineContext().ensureActive()
                    if (intent.action == ACTION_STOP) {
                        store.publish(AudioPlaybackState())
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        completed = true
                        stopSelfResult(startId)
                    } else if (bookId != null) {
                        val initial = intent.getStringExtra(EXTRA_LOCATOR)?.let { Locator.fromJSON(JSONObject(it)) }
                        engine.open(bookId, initial, scope)
                        engine.player?.let { player ->
                            val activity = PendingIntent.getActivity(this@AudioPlaybackService, 0,
                                com.javierreansyah.pinecone.ui.root.audioPlayerIntent(this@AudioPlaybackService, bookId),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            session = MediaSession.Builder(this@AudioPlaybackService, player)
                                .setSessionActivity(activity).setCallback(callback).build().also(::addSession)
                            store.update { it.copy(ready = true, generation = UUID.randomUUID().toString()) }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    timber.log.Timber.e(error, "Could not open audiobook session")
                    releaseSession()
                    engine.releasePlayer()
                    store.publish(AudioPlaybackState(bookId = intent.getStringExtra(EXTRA_BOOK_ID),
                        error = getString(com.javierreansyah.pinecone.R.string.reader_audio_open_failed)))
                } finally {
                    receiver?.send(if (completed) 0 else 1, Bundle.EMPTY)
                }
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(COMMAND_GO, Bundle.EMPTY)).add(SessionCommand(COMMAND_LINK, Bundle.EMPTY)).build())
                .build()

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            return try {
                when (customCommand.customAction) {
                    COMMAND_GO -> engine.go(requireNotNull(Locator.fromJSON(JSONObject(requireNotNull(args.getString(EXTRA_LOCATOR))))))
                    COMMAND_LINK -> engine.go(requireNotNull(Link.fromJSON(JSONObject(requireNotNull(args.getString(EXTRA_LINK))))))
                    else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } catch (_: IllegalArgumentException) {
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private fun releaseSession() {
        store.update { it.copy(ready = false, generation = null) }
        session?.let { removeSession(it); it.release() }
        session = null
    }

    override fun onDestroy() {
        scope.cancel()
        releaseSession()
        engine.releasePlayer()
        store.publish(AudioPlaybackState())
        super.onDestroy()
    }
}
