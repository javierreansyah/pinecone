package com.javierreansyah.pinecone.ui.root

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation3.runtime.NavKey
import com.javierreansyah.pinecone.MainActivity

/** The player key is an overlay request, not a destination that replaces app content. */
fun MutableList<NavKey>.openAudioPlayer(bookId: String, initialLocatorJson: String? = null) {
    if (lastOrNull() == Screen.AudioPlayer(bookId, initialLocatorJson)) return
    collapseAudioPlayer()
    add(Screen.AudioPlayer(bookId, initialLocatorJson))
}

fun MutableList<NavKey>.collapseAudioPlayer() {
    removeAll { it is Screen.AudioPlayer }
}

fun audioPlayerIntent(context: Context, bookId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.Builder().scheme("pinecone").authority("audio_player").appendPath(bookId).build())
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
