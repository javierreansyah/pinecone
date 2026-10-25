@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.reader.audio

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.cancel as cancelScope

@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_STOP: String = "com.javierreansyah.pinecone.audio.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Inject
    lateinit var readingSession: AudioReadingSession
    private var session: MediaSession? = null
    private var openJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            readingSession.navigator.collect { navigator ->
                session?.let { removeSession(it); it.release() }
                session = navigator?.let {
                    val readerIntent = com.javierreansyah.pinecone.ui.root.audioPlayerIntent(
                        this@AudioPlaybackService,
                        requireNotNull(readingSession.state.value.bookId),
                    )
                    val activity = android.app.PendingIntent.getActivity(
                        this@AudioPlaybackService, 0, readerIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    MediaSession.Builder(this@AudioPlaybackService, it.asMedia3Player())
                        .setSessionActivity(activity).build()
                }
                session?.let(::addSession)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            openJob?.cancel()
            openJob = scope.launch {
                readingSession.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }
        intent?.getStringExtra("bookId")?.let { bookId ->
            openJob?.cancel()
            openJob = scope.launch { readingSession.open(bookId) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // Required MediaSessionService signature, even when the controller needs no customization.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancelScope()
        session?.release(); session = null
        readingSession.releasePlayer()
        super.onDestroy()
    }
}
