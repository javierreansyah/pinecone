package com.javierreansyah.pinecone.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StartupCoordinatorTest {
    @Test
    fun concurrentEntryPointsShareRecoveryAndWaitForSettings() = runTest {
        val recovery = CompletableDeferred<Unit>()
        var recovered = 0
        var settingsLoaded = false
        val startup = StartupCoordinator(
            recover = { ++recovered; recovery.await() },
            loadSettings = { settingsLoaded = true },
        )
        val first = async { startup.initialize() }
        val second = async { startup.initialize() }
        val waiting = async { startup.awaitReady() }
        runCurrent()
        assertEquals(1, recovered)
        assertFalse(settingsLoaded)
        assertFalse(second.isCompleted)
        assertFalse(waiting.isCompleted)
        recovery.complete(Unit)
        first.await()
        second.await()
        waiting.await()
        assertEquals(1, recovered)
        assertTrue(settingsLoaded)
        assertEquals(StartupState.Ready, startup.state.value)
    }

    @Test
    fun failedRecoveryPreservesFailureUntilAnExplicitRetrySucceeds() = runTest {
        var fail = true
        var loaded = 0
        val startup = StartupCoordinator(
            recover = { check(!fail) { "Recovery failed" } },
            loadSettings = { ++loaded },
        )
        try {
            startup.initialize()
            throw AssertionError("Recovery must fail")
        } catch (_: IllegalStateException) {
            assertTrue(startup.state.value is StartupState.Failed)
            assertEquals(0, loaded)
        }
        fail = false
        startup.initialize()
        assertEquals(StartupState.Ready, startup.state.value)
        assertEquals(1, loaded)
    }

    @Test
    fun cancellationDoesNotPublishReadinessAndAnotherCallerCanRetry() = runTest {
        var attempts = 0
        val startup = StartupCoordinator(
            recover = { if (++attempts == 1) CompletableDeferred<Unit>().await() },
            loadSettings = {},
        )
        val first = launch { startup.initialize() }
        runCurrent()
        first.cancelAndJoin()
        assertEquals(StartupState.Loading, startup.state.value)
        startup.initialize()
        assertEquals(2, attempts)
        assertEquals(StartupState.Ready, startup.state.value)
    }
}
