package com.javierreansyah.pinecone.data.repository.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One process-wide owner for publication asset paths and reference mutations. */
@Singleton
class PublicationAssetStore @Inject constructor(@ApplicationContext private val context: Context) {
    internal val mutationMutex = Mutex()
    private val leases = mutableMapOf<String, Int>()
    private val stageRoot get() = File(context.filesDir, "publication-staging").also { it.mkdirs() }

    internal fun lease(file: File): AutoCloseable {
        val path = file.canonicalPath
        synchronized(leases) { leases[path] = (leases[path] ?: 0) + 1 }
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) synchronized(leases) {
                val remaining = requireNotNull(leases[path]) - 1
                if (remaining == 0) leases.remove(path) else leases[path] = remaining
            }
        }
    }

    /** Caller holds mutationMutex and has checked that no durable reference remains. */
    internal fun deleteIfUnleased(file: File): Boolean = synchronized(leases) {
        val path = file.canonicalPath
        if (leases.keys.any { it == path || it.startsWith(path + File.separator) }) false else file.delete()
    }

    internal suspend fun stage(): Stage = mutationMutex.withLock {
        val directory = File(stageRoot, UUID.randomUUID().toString()).also { check(it.mkdir()) }
        Stage(directory, lease(directory))
    }

    internal class Stage(val directory: File, private val lease: AutoCloseable) : AutoCloseable {
        override fun close() {
            directory.deleteRecursively()
            lease.close()
        }
    }

    /** Only abandoned attempt directories are removed; live attempts lease the whole directory. */
    internal fun cleanupStaging() = synchronized(leases) {
        stageRoot.listFiles()?.forEach { directory ->
            if (directory.canonicalPath !in leases) directory.deleteRecursively()
        }
    }
    internal val booksDirectory: File
        get() = File(context.filesDir, "publication-assets").also { it.mkdirs() }
    internal val coversDirectory: File
        get() = File(context.filesDir, "publication-covers").also { it.mkdirs() }
}
