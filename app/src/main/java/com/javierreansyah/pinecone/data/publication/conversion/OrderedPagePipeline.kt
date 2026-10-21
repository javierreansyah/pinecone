package com.javierreansyah.pinecone.data.publication.conversion

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Bounded preparation with ordered consumption; failure joins workers before callers clean up. */
internal suspend fun <T> forEachPreparedPage(
    count: Int,
    parallelism: Int,
    prepare: suspend (Int) -> T,
    consume: suspend (Int, T) -> Unit,
) = coroutineScope {
    require(count >= 0 && parallelism > 0)
    val pending = ArrayDeque<Deferred<T>>()
    var next = 0
    fun enqueue() {
        val index = next++
        pending.addLast(async(Dispatchers.Default) { prepare(index) })
    }
    repeat(minOf(count, parallelism)) { enqueue() }
    repeat(count) { index ->
        consume(index, pending.removeFirst().await())
        if (next < count) enqueue()
    }
}
