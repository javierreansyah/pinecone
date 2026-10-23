package com.javierreansyah.pinecone.data.publication.conversion

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class OrderedPagePipelineTest {
    @Test
    fun preparationOverlapsButConsumptionStaysInSourceOrder(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            val secondReady = CompletableDeferred<Unit>()
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val consumed = mutableListOf<Int>()
            forEachPreparedPage(8, 2, prepare = { index ->
                val running = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, running) }
                try {
                    if (index == 0) secondReady.await()
                    if (index == 1) secondReady.complete(Unit)
                    index * 10
                } finally {
                    active.decrementAndGet()
                }
            }) { index, result ->
                assertEquals(index * 10, result)
                consumed += index
            }
            assertEquals((0 until 8).toList(), consumed)
            assertEquals(2, peak.get())
        }
    }

    @Test
    fun cancellationJoinsPendingWorkBeforeReturningToCleanup(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            val secondReady = CompletableDeferred<Unit>()
            val prepared = AtomicInteger()
            val released = AtomicInteger()
            val cancellation = CancellationException("Cancel from progress callback")
            try {
                forEachPreparedPage(20, 2, prepare = { index ->
                    prepared.incrementAndGet()
                    try {
                        if (index == 0) secondReady.await()
                        else {
                            secondReady.complete(Unit); awaitCancellation()
                        }
                    } finally {
                        released.incrementAndGet()
                    }
                }) { _, _ -> throw cancellation }
                fail("Conversion should be cancelled")
            } catch (actual: CancellationException) {
                assertEquals(cancellation.message, actual.message)
            }
            assertEquals(2, prepared.get())
            assertEquals(2, released.get())
        }
    }

    @Test
    fun workerFailureCancelsOtherWorkAndReachesCaller(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            val firstReady = CompletableDeferred<Unit>()
            val released = AtomicInteger()
            val failure = IllegalStateException("Cannot export page")
            try {
                forEachPreparedPage(20, 2, prepare = { index ->
                    try {
                        if (index == 0) {
                            firstReady.complete(Unit); awaitCancellation()
                        }
                        firstReady.await()
                        throw failure
                    } finally {
                        released.incrementAndGet()
                    }
                }) { _, _ -> fail("No page should be consumed") }
                fail("Export should fail")
            } catch (actual: IllegalStateException) {
                assertEquals(failure.message, actual.message)
            }
            assertEquals(2, released.get())
        }
    }

    @Test
    fun singleWorkerAndEmptyInputDoNotNeedAdditionalTasks(): Unit = runBlocking {
        val consumed = mutableListOf<Int>()
        forEachPreparedPage(3, 1, prepare = { it }) { _, page -> consumed += page }
        forEachPreparedPage(
            0,
            2,
            prepare = { fail("No work expected") }) { _, _ -> fail("No result expected") }
        assertEquals(listOf(0, 1, 2), consumed)
    }
}
