package com.javierreansyah.pinecone

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.javierreansyah.pinecone.ui.features.library.info.BookInfoViewModel
import com.javierreansyah.pinecone.ui.features.settings.SettingsViewModel
import com.javierreansyah.pinecone.ui.features.reader.AudioPlayerViewModel
import com.javierreansyah.pinecone.ui.features.reader.ReaderNotesViewModel
import com.javierreansyah.pinecone.ui.root.AudioControlsViewModel
import com.javierreansyah.pinecone.ui.root.audioPlayerIntent
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Real Hilt graph, navigation entry, Readium engine and Media3 controller on the device. */
class AudioPlaybackIntegrationTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val imported = mutableListOf<String>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PineconeApplication
    private lateinit var controls: AudioControlsViewModel

    private fun importTone(): String = runBlocking(Dispatchers.IO) {
        val file = File(app.cacheDir, "audio-regression-${UUID.randomUUID()}.wav")
        val samples = 22_050 * 60
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(22_050).putInt(44_100).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { bytes.putShort((1500 * kotlin.math.sin(2 * Math.PI * 440 * it / 22_050)).toInt().toShort()) }
        try {
            file.writeBytes(bytes.array())
            requireNotNull(app.libraryRepository.importBook(Uri.fromFile(file))).book.id.also { imported += it }
        } finally { file.delete() }
    }

    private fun await(timeoutMillis: Long, description: String = "playback condition", condition: () -> Boolean) = runBlocking {
        try {
            withTimeout(timeoutMillis) { while (!condition()) delay(50) }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("Timed out: $description; state=${if (::controls.isInitialized) controls.state.value else null}", timeout)
        }
    }

    private fun containsDescription(node: AccessibilityNodeInfo?, description: String): Boolean = node != null &&
        (node.contentDescription?.toString() == description || (0 until node.childCount).any { containsDescription(node.getChild(it), description) })

    private fun launch(id: String) {
        scenario = ActivityScenario.launch(audioPlayerIntent(app, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        scenario.onActivity { controls = ViewModelProvider(it)[AudioControlsViewModel::class.java] }
    }

    private fun showPlayer(id: String) {
        launch(id)
        await(20_000) { controls.state.value.bookId == id && (controls.state.value.ready || controls.state.value.error != null) }
        assertNull(controls.state.value.error)
        assertTrue(controls.state.value.ready)
        await(10_000) {
            containsDescription(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow, app.getString(R.string.reader_play))
        }
    }

    @After fun cleanup() {
        if (::controls.isInitialized) {
            scenario.onActivity { controls.dismiss() }
            await(20_000) { controls.state.value.bookId == null }
        }
        if (::scenario.isInitialized) scenario.close()
        runBlocking(Dispatchers.IO) { imported.forEach { app.libraryRepository.deleteBook(it) } }
    }

    @Test fun audioEntryLoadsAndControllerPlaybackSurvivesRecreation() {
        val id = importTone()
        showPlayer(id)
        val generation = controls.state.value.generation
        assertFalse(controls.state.value.playing) // opening does not autoplay
        scenario.onActivity { controls.togglePlayback() }
        await(15_000) { controls.state.value.playing && controls.state.value.elapsedSeconds >= 1 }
        scenario.recreate()
        await(15_000) { controls.state.value.playing && controls.state.value.elapsedSeconds >= 2 }
        assertEquals(generation, controls.state.value.generation)
        scenario.onActivity { controls.togglePlayback() }
        await(10_000) { !controls.state.value.playing }
        val before = controls.state.value.elapsedSeconds
        scenario.onActivity { controls.skip(10) }
        await(10_000) { controls.state.value.elapsedSeconds >= before + 9 }
    }

    @Test fun differentAudioViewModelTypesRemainRetainedInTheSameStore() {
        val id = importTone()
        showPlayer(id)
        lateinit var player: AudioPlayerViewModel
        lateinit var notes: ReaderNotesViewModel
        scenario.onActivity { activity ->
            player = ViewModelProvider(activity.viewModelStore, activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<AudioPlayerViewModel.Factory> { it.create(id) })[AudioPlayerViewModel::class.java]
            notes = ViewModelProvider(activity.viewModelStore, activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<ReaderNotesViewModel.Factory> { it.create(id) })[ReaderNotesViewModel::class.java]
            assertSame(player, ViewModelProvider(activity)[AudioPlayerViewModel::class.java])
            assertSame(notes, ViewModelProvider(activity)[ReaderNotesViewModel::class.java])
        }
        scenario.recreate()
        scenario.onActivity { activity ->
            assertSame(player, ViewModelProvider(activity)[AudioPlayerViewModel::class.java])
            assertSame(notes, ViewModelProvider(activity)[ReaderNotesViewModel::class.java])
        }
    }

    @Test fun deletingActiveBookAcknowledgesReleaseBeforeRemovingAssets() {
        val id = importTone()
        showPlayer(id)
        val path = runBlocking(Dispatchers.IO) { requireNotNull(app.libraryRepository.getBook(id)).book.filePath }
        var deleted = false
        scenario.onActivity { activity ->
            val info = ViewModelProvider(activity.viewModelStore, activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<BookInfoViewModel.Factory> { it.create(id) })["delete:$id", BookInfoViewModel::class.java]
            info.deleteBook { deleted = true }
        }
        await(20_000) { deleted }
        assertNull(controls.state.value.bookId)
        assertFalse(controls.state.value.ready)
        assertFalse(File(path).exists())
    }

    @Test fun missingBookReportsAnErrorInsteadOfSpinning() {
        val id = "missing-${UUID.randomUUID()}"
        launch(id)
        await(15_000) { controls.state.value.bookId == id && controls.state.value.error != null }
        assertFalse(controls.state.value.loading)
        assertFalse(controls.state.value.ready)
    }
    @Test fun stopDuringOpeningCannotResurrectAndAnotherBookCanOpen() {
        val first = importTone()
        val second = importTone()
        launch(first)
        scenario.onActivity { activity ->
            val replacement = ViewModelProvider(activity.viewModelStore, activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<AudioPlayerViewModel.Factory> { it.create(second) })["replacement:$second", AudioPlayerViewModel::class.java]
            replacement.open()
            controls.dismiss()
        }
        await(20_000) { controls.state.value.bookId == null }
        runBlocking { delay(500) }
        assertNull(controls.state.value.bookId)
        scenario.onActivity { activity ->
            ViewModelProvider(activity)["replacement:$second", AudioPlayerViewModel::class.java].open(retry = true)
        }
        await(20_000) { controls.state.value.bookId == second && controls.state.value.ready }
        assertNull(controls.state.value.error)
    }

    @Test fun backgroundPlaybackRespondsToSystemPause() {
        showPlayer(importTone())
        scenario.onActivity { controls.togglePlayback() }
        await(15_000) { controls.state.value.playing && controls.state.value.elapsedSeconds >= 1 }
        val before = controls.state.value.elapsedSeconds
        scenario.moveToState(Lifecycle.State.CREATED)
        await(10_000) { controls.state.value.elapsedSeconds > before }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_MEDIA_PAUSE").close()
        await(10_000) { !controls.state.value.playing }
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test fun restoreCoordinatorStopsPlaybackEvenWhenArchiveCannotBeRead() {
        showPlayer(importTone())
        scenario.onActivity { controls.togglePlayback() }
        await(15_000) { controls.state.value.playing }
        val failed = java.util.concurrent.atomic.AtomicBoolean()
        scenario.onActivity { activity ->
            ViewModelProvider(activity)[SettingsViewModel::class.java].restoreFullBackup(
                Uri.fromFile(File(app.cacheDir, "missing-backup-${UUID.randomUUID()}.zip")),
                onStart = {}, onSuccess = {}, onWarning = {}, onFailure = { failed.set(true) })
        }
        await(20_000) { failed.get() }
        assertNull(controls.state.value.bookId)
        assertFalse(controls.state.value.playing)
    }

    @Test fun bookSettingsReachThePlayerAndBookmarksAndPositionSurviveReopening() {
        val id = importTone()
        showPlayer(id)
        val global = runBlocking { app.readerPreferences.readerSettings.first() }
        lateinit var player: AudioPlayerViewModel
        lateinit var notes: ReaderNotesViewModel
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
        scenario.onActivity { activity ->
            player = ViewModelProvider(activity.viewModelStore, activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<AudioPlayerViewModel.Factory> { it.create(id) })["settings:$id", AudioPlayerViewModel::class.java]
            notes = ViewModelProvider(activity.viewModelStore, activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<ReaderNotesViewModel.Factory> { it.create(id) })[ReaderNotesViewModel::class.java]
            player.setUseBookSettings(true)
            future = androidx.media3.session.MediaController.Builder(activity, androidx.media3.session.SessionToken(activity,
                android.content.ComponentName(activity, com.javierreansyah.pinecone.reader.audio.AudioPlaybackService::class.java))).buildAsync()
        }
        await(10_000, "enabling per-book settings") { runBlocking(Dispatchers.IO) { app.libraryRepository.rendition(id, "original")?.preferencesJson != "{}" } }
        scenario.onActivity {
            player.updateSettings { it.copy(formatPreferences = it.formatPreferences.copy(audioSpeed = 1.5, audioPitch = 1.1)) }
        }
        val controller = future.get(15, java.util.concurrent.TimeUnit.SECONDS)
        try {
            var applied = false
            await(10_000, "applying speed and pitch to Media3") {
                scenario.onActivity {
                    applied = kotlin.math.abs(controller.playbackParameters.speed - 1.5f) < 0.01f &&
                        kotlin.math.abs(controller.playbackParameters.pitch - 1.1f) < 0.01f
                }
                applied
            }
            assertEquals(global.formatPreferences, runBlocking { app.readerPreferences.readerSettings.first() }.formatPreferences)
            scenario.onActivity { player.seek(12.0) }
            await(10_000, "seeking to 12 seconds") { controls.state.value.elapsedSeconds >= 12 &&
                (controls.state.value.locator?.locations?.totalProgression ?: 0.0) >= 0.18 }
            scenario.onActivity {
                notes.toggleBookmark(requireNotNull(controls.state.value.locator), controls.state.value.chapter)
            }
            await(10_000) { runBlocking { app.libraryRepository.getBookmarks(id).first().isNotEmpty() } }
        } finally { scenario.onActivity { controller.release() } }
        scenario.onActivity { controls.dismiss() }
        await(15_000) { controls.state.value.bookId == null }
        val saved = runBlocking { app.libraryRepository.getLastLocator(id) }
        assertTrue((saved?.locations?.totalProgression ?: 0.0) >= 0.18)
        scenario.onActivity { player.open(retry = true) }
        await(20_000) { controls.state.value.bookId == id && controls.state.value.ready && controls.state.value.elapsedSeconds >= 11 }
        assertFalse(controls.state.value.playing)
    }

    @Test fun malformedBookSettingsReportAnErrorAndCanBeDisabledAndRetried() {
        val id = importTone()
        showPlayer(id)
        lateinit var player: AudioPlayerViewModel
        scenario.onActivity { activity ->
            player = ViewModelProvider(activity.viewModelStore, activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<AudioPlayerViewModel.Factory> { it.create(id) })["recovery:$id", AudioPlayerViewModel::class.java]
        }
        runBlocking { app.libraryRepository.setReadingOverrides(id, "original", "{invalid-json") }
        await(10_000, "reporting malformed book settings") { controls.state.value.error != null }
        scenario.onActivity { player.setUseBookSettings(false) }
        await(10_000, "disabling malformed book settings") {
            runBlocking { app.libraryRepository.rendition(id, "original")?.preferencesJson == "{}" }
        }
        scenario.onActivity { player.open(retry = true) }
        await(20_000, "retry after clearing book settings") {
            controls.state.value.bookId == id && controls.state.value.ready && controls.state.value.error == null
        }
    }

}
