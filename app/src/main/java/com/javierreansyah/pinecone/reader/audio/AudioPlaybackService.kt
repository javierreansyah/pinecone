@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.reader.audio

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.javierreansyah.pinecone.PineconeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudioPlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val readingSession get() = (application as PineconeApplication).audioSession
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            readingSession.navigator.collect { navigator ->
                session?.let { removeSession(it); it.release() }
                session = navigator?.let {
                    val readerIntent = Intent(
                        this@AudioPlaybackService,
                        com.javierreansyah.pinecone.ui.features.reader.ReaderActivity::class.java
                    )
                        .putExtra("bookId", readingSession.state.value.bookId)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
        intent?.getStringExtra("bookId")
            ?.let { bookId -> scope.launch { readingSession.open(bookId) } }
        return super.onStartCommand(intent, flags, startId)
    }

    // Required MediaSessionService signature, even when the controller needs no customization.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.release(); session = null
        readingSession.releasePlayer()
        super.onDestroy()
    }
}
