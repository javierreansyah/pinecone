package com.javierreansyah.pinecone.ui.root

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.navigation3.runtime.NavKey

internal fun importDestinationStack(unsortedEnabled: Boolean): List<NavKey> =
    if (unsortedEnabled) listOf(Screen.Library, Screen.Unsorted) else listOf(Screen.Library)

fun getInitialBackStackFromIntent(intent: Intent, unsortedEnabled: Boolean = true): List<NavKey> {
    val uri = intent.data
    if (intent.action == Intent.ACTION_VIEW && uri != null) {
        if (uri.scheme == "pinecone" && uri.host == "audio_player") {
            uri.lastPathSegment?.let { return listOf(Screen.Library, Screen.AudioPlayer(it)) }
        } else if (uri.scheme == "pinecone" && uri.host == "book_info") {
            val bookId = uri.lastPathSegment
            if (bookId != null) {
                return if (intent.getBooleanExtra("from_reader", false)) {
                    listOf(Screen.BookInfo(bookId))
                } else {
                    listOf(Screen.Library, Screen.BookInfo(bookId))
                }
            }
        } else if (uri.scheme == "file" || uri.scheme == "content") {
            return importDestinationStack(unsortedEnabled)
        }
    }
    return listOf(Screen.Library)
}

@Composable
fun HandleDeepLinks(
    backStack: MutableList<NavKey>,
    addOnNewIntentListener: (Consumer<Intent>) -> Unit,
    unsortedEnabled: Boolean = true,
    removeOnNewIntentListener: (Consumer<Intent>) -> Unit,
) {
    var currentIntent by remember { mutableStateOf<Intent?>(null) }
    val currentUnsortedEnabled by rememberUpdatedState(unsortedEnabled)

    DisposableEffect(Unit) {
        val listener = Consumer<Intent> { newIntent ->
            currentIntent = newIntent
        }
        addOnNewIntentListener(listener)
        onDispose { removeOnNewIntentListener(listener) }
    }

    LaunchedEffect(currentIntent) {
        val intentToProcess = currentIntent ?: return@LaunchedEffect
        val uri = intentToProcess.data
        if (intentToProcess.action == Intent.ACTION_VIEW && uri != null) {
            if (uri.scheme == "pinecone" && uri.host == "audio_player") {
                uri.lastPathSegment?.let { backStack.openAudioPlayer(it) }
            } else if (uri.scheme == "pinecone" && uri.host == "book_info") {
                val bookId = uri.lastPathSegment
                bookId?.let { backStack.add(Screen.BookInfo(it)) }
            } else if (uri.scheme == "file" || uri.scheme == "content") {
                val destination = if (currentUnsortedEnabled) Screen.Unsorted else Screen.Library
                if (backStack.lastOrNull() != destination) {
                    backStack.clear()
                    backStack.addAll(importDestinationStack(currentUnsortedEnabled))
                }
            }
        }
    }
}
