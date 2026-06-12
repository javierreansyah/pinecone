package com.example.readerapp.ui.root

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.navigation3.runtime.NavKey

fun getInitialBackStackFromIntent(intent: Intent): List<NavKey> {
    val uri = intent.data
    if (intent.action == Intent.ACTION_VIEW && uri != null && uri.scheme == "pinecone" && uri.host == "book_info") {
        val bookId = uri.lastPathSegment
        if (bookId != null) {
            return if (intent.getBooleanExtra("from_reader", false)) {
                listOf(Screen.BookInfo(bookId))
            } else {
                listOf(Screen.Library, Screen.BookInfo(bookId))
            }
        }
    }
    return listOf(Screen.Library)
}

@Composable
fun HandleDeepLinks(
    backStack: MutableList<NavKey>,
    addOnNewIntentListener: (Consumer<Intent>) -> Unit,
    removeOnNewIntentListener: (Consumer<Intent>) -> Unit
) {
    var currentIntent by remember { mutableStateOf<Intent?>(null) }

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
            if (uri.scheme == "pinecone" && uri.host == "book_info") {
                val bookId = uri.lastPathSegment
                if (bookId != null) {
                    backStack.add(Screen.BookInfo(bookId))
                }
            }
        }
    }
}
