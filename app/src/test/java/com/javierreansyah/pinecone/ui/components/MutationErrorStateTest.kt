package com.javierreansyah.pinecone.ui.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class MutationErrorStateTest {
    @Test
    fun operationTimeoutIsReportedWhileTheOwningScreenRemainsActive() = runTest {
        val state = MutationErrorState()
        state.run { kotlinx.coroutines.withTimeout(1L.milliseconds) { kotlinx.coroutines.awaitCancellation() } }
        assertTrue(state.failed.value)
    }

    @Test
    fun failureDoesNotRunSuccessAndCanBeDismissed() = runTest {
        val state = MutationErrorState()
        var success = false
        state.run {
            check(false) { "Write failed" }
            success = true
        }
        assertFalse(success)
        assertTrue(state.failed.value)
        state.dismiss()
        assertFalse(state.failed.value)
    }

    @Test
    fun cancellationPropagatesWithoutPresentingAnOrdinaryFailure() = runTest {
        val state = MutationErrorState()
        try {
            state.run { throw CancellationException("Leaving screen") }
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(state.failed.value)
        }
    }
}
