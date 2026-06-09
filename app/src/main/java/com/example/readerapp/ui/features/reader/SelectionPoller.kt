package com.example.readerapp.ui.features.reader

import android.view.ActionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.shared.publication.Locator
import kotlin.time.Duration.Companion.milliseconds

/**
 * Workaround for a Chromium bug where text selection across columns
 * does not reliably trigger selection callbacks. This class continuously
 * polls the active selection while the selection action mode is alive.
 */
class SelectionPoller(
    private val coroutineScope: CoroutineScope,
    private val onSelectionUpdated: (Locator) -> Unit
) {
    private var job: Job? = null

    fun start(navigator: SelectableNavigator, actionModeProvider: () -> ActionMode?) {
        job?.cancel()
        job = coroutineScope.launch {
            while (isActive && actionModeProvider() != null) {
                val selection = navigator.currentSelection()
                if (selection != null) {
                    onSelectionUpdated(selection.locator)
                }
                delay(300.milliseconds)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
