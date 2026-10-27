package com.javierreansyah.pinecone.data.repository.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.OperationEntity
import com.javierreansyah.pinecone.data.local.database.library.OperationInputEntity
import com.javierreansyah.pinecone.data.local.database.library.OperationUriGrantEntity
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.model.OperationKind
import com.javierreansyah.pinecone.data.model.OperationSnapshot
import com.javierreansyah.pinecone.data.model.OperationState
import com.javierreansyah.pinecone.data.publication.PublicationFormats
import com.javierreansyah.pinecone.data.publication.conversion.PdfReadModeConverter
import com.javierreansyah.pinecone.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Room is authoritative; WorkManager owns execution, never a screen's coroutine scope. */
@Singleton
class PublicationOperations @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val publications: PublicationRepository,
    private val reading: ReadingRepository,
    private val scheduler: OperationScheduler,
    private val preferences: ReaderPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao get() = database.operationDao()
    private val inputMutex = Mutex()
    private val processToken = UUID.randomUUID().toString()
    private val inputsRoot get() = File(context.filesDir, "operation-inputs").also { it.mkdirs() }
    val generation: Flow<Long> get() = dao.generation()
    val libraryState: Flow<Pair<Long, Boolean>> =
        dao.runtimeFlow().map { (it?.generation ?: 0L) to (it?.maintenance ?: false) }
    val imports: Flow<List<OperationSnapshot>> =
        dao.imports().map { rows -> rows.map { snapshot(it) } }

    fun observe(id: String): Flow<OperationSnapshot?> =
        dao.observe(id).map { it?.let { row -> snapshot(row) } }

    fun conversion(bookId: String): Flow<OperationSnapshot?> =
        dao.conversion(bookId).map { it?.let { row -> snapshot(row) } }

    suspend fun acknowledge(id: String) = dao.acknowledge(id)

    suspend fun enqueueImports(uris: List<Uri>): String =
        prepare(OperationKind.IMPORT, uris.distinct())

    suspend fun enqueueScan(uri: Uri): String = prepare(OperationKind.SCAN, listOf(uri))

    private suspend fun prepare(kind: OperationKind, uris: List<Uri>): String = withContext(io) {
        require(uris.isNotEmpty() && uris.size <= MAX_ITEMS)
        val id = UUID.randomUUID().toString()
        val runtime = dao.runtime()
        check(!runtime.maintenance) { "Library restore in progress" }
        dao.insert(
            OperationEntity(
                id,
                kind.name,
                "PREPARING",
                runtime.generation,
                runtime.sequence,
                System.currentTimeMillis(),
                attempt = processToken
            )
        )
        try {
            inputMutex.withLock {
                uris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    val name = displayName(uri)
                    val grant = retainGrant(uri)
                    val input = OperationInputEntity(
                        id, index, uri.toString(), name,
                        folder = kind == OperationKind.SCAN, grantUri = grant
                    )
                    dao.insertInputs(listOf(input))
                    if (grant == null) {
                        if (kind == OperationKind.SCAN) throw SecurityException("Reselect a folder with persistent read access")
                        val file = File(inputsRoot, "${id}_$index")
                        // Persist ownership before copying; PREPARING is never represented as accepted.
                        dao.updateInput(input.copy(stagedPath = file.absolutePath))
                        requireNotNull(context.contentResolver.openInputStream(uri)).use { source ->
                            FileOutputStream(file).use { target ->
                                val buffer = ByteArray(64 * 1024)
                                var sinceCheck = 0
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = source.read(buffer)
                                    if (count < 0) break
                                    target.write(buffer, 0, count)
                                    sinceCheck += count
                                    if (sinceCheck >= 1024 * 1024) {
                                        if (dao.get(id)?.state != "PREPARING" || dao.runtime().generation != runtime.generation) throw Invalidated()
                                        sinceCheck = 0
                                    }
                                }
                                target.fd.sync()
                            }
                        }
                    }
                }
                database.withTransaction {
                    val current = requireNotNull(dao.get(id))
                    if (current.state != "PREPARING" || dao.runtime().generation != current.generation) throw Invalidated()
                    dao.update(
                        current.copy(
                            state = "QUEUED",
                            total = if (kind == OperationKind.SCAN) 0 else uris.size
                        )
                    )
                }
            }
            scheduler.enqueue(id)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                preparationFailed(id, "preparation_interrupted")
                cleanup(id)
            }
            throw cancelled
        } catch (error: Exception) {
            // A scheduling failure leaves the accepted QUEUED record for startup reconciliation.
            preparationFailed(id, errorCode(error))
            cleanup(id)
        }
        id
    }

    suspend fun enqueueReadMode(bookId: String, forceReparse: Boolean): String {
        val operation = database.withTransaction {
            dao.activeConversion(bookId)?.let { return@withTransaction it }
            val runtime = dao.runtime()
            check(!runtime.maintenance)
            val source = requireNotNull(database.publicationDao().rendition(bookId, "original"))
            require(source.profile == "PDF")
            OperationEntity(
                UUID.randomUUID().toString(),
                "READ_MODE",
                "QUEUED",
                runtime.generation,
                runtime.sequence,
                System.currentTimeMillis(),
                bookId,
                source.sha256,
                PdfReadModeConverter.VERSION,
                forceReparse
            )
                .also { dao.insert(it) }
        }
        scheduler.enqueue(operation.id)
        return operation.id
    }

    suspend fun cancel(id: String) {
        database.withTransaction {
            dao.get(id)?.takeUnless { OperationState.valueOf(it.state).terminal }?.let {
                dao.update(it.copy(state = "CANCELLED", attempt = null))
            }
        }
        scheduler.cancel(id)
        cleanup(id)
    }

    suspend fun fail(id: String, error: String) {
        database.withTransaction {
            dao.get(id)?.takeUnless { OperationState.valueOf(it.state).terminal }
                ?.let { dao.update(it.copy(state = "FAILED", error = error)) }
        }
        cleanup(id)
    }

    /** Reconcile the insert/enqueue window, including preparation interrupted by process death. */
    suspend fun reconcile() = withContext(io) {
        dao.resumable().forEach { row ->
            if (row.state == "PREPARING" && row.attempt != processToken) {
                preparationFailed(row.id, "preparation_interrupted")
                cleanup(row.id)
            } else if (row.state != "PREPARING") scheduler.enqueue(row.id)
        }
        dao.finishedWithStaging().forEach { cleanup(it) }
        inputMutex.withLock {
            val owned = dao.stagedPaths().map { File(it).canonicalPath }.toSet()
            inputsRoot.listFiles()?.filter { it.canonicalPath !in owned }?.forEach { it.delete() }
            releaseGrants()
        }
        dao.prune(System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30))
    }

    enum class Execution { COMPLETE, RETRY }

    suspend fun execute(
        id: String,
        lastAttempt: Boolean,
        progress: suspend (OperationSnapshot) -> Unit
    ): Execution = withContext(io) {
        val attempt = UUID.randomUUID().toString()
        val claimed = database.withTransaction {
            val row = dao.get(id) ?: return@withTransaction null
            if (row.state !in listOf("QUEUED", "RUNNING")) return@withTransaction null
            if (dao.runtime().let { it.maintenance || it.generation != row.generation }) {
                dao.update(row.copy(state = "CANCELLED", error = "library_replaced")); null
            } else row.copy(state = "RUNNING", attempt = attempt, error = null)
                .also { dao.update(it) }
        }
        if (claimed == null) {
            cleanup(id); return@withContext Execution.COMPLETE
        }
        try {
            progress(snapshot(claimed))
            if (claimed.kind == "READ_MODE") {
                val bookId = requireNotNull(claimed.bookId)
                require(claimed.converterVersion == PdfReadModeConverter.VERSION) { "Converter changed; request conversion again" }
                require(
                    database.publicationDao()
                        .rendition(bookId, "original")?.sha256 == claimed.sourceHash
                ) { "Original source changed" }
                var lastProgress = 0L
                publications.createReadMode(bookId, claimed.forceReparse, commit = {
                    checked(id, attempt, bookId)
                    reading.activateMappedRendition(bookId, "read-mode")
                    dao.update(
                        requireNotNull(dao.get(id)).copy(
                            state = "SUCCEEDED",
                            completed = it.sourceMapJson?.let { map ->
                                kotlinx.serialization.json.Json.decodeFromString<List<com.javierreansyah.pinecone.data.publication.conversion.SourcePage>>(
                                    map
                                ).size
                            } ?: 0))
                }) { completed, total ->
                    var report = false
                    database.withTransaction {
                        val current = checked(id, attempt, bookId)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (completed == total || now - lastProgress >= 500) {
                            dao.update(current.copy(completed = completed, total = total))
                            lastProgress = now
                            report = true
                        }
                    }
                    if (report) progress(snapshot(requireNotNull(dao.get(id))))
                }
            } else {
                if (claimed.kind == "SCAN") discover(claimed, attempt)
                val inputs = dao.inputs(id).filter { claimed.kind != "SCAN" || it.ordinal > 0 }
                for (input in inputs) {
                    if (input.state != "PENDING") continue
                    checked(id, attempt)
                    try {
                        val commit: suspend (com.javierreansyah.pinecone.data.local.database.library.BookWithDetails) -> Unit =
                            { book ->
                                val current = checked(id, attempt, book.book.id)
                                dao.updateInput(
                                    input.copy(
                                        state = "SUCCESS",
                                        bookId = book.book.id
                                    )
                                )
                                dao.update(current.copy(completed = current.completed + 1))
                            }
                        if (input.folder) {
                            var folder = requireNotNull(
                                DocumentFile.fromTreeUri(
                                    context,
                                    requireNotNull(input.grantUri).toUri()
                                )
                            )
                            kotlinx.serialization.json.Json.decodeFromString<List<String>>(
                                input.folderPathJson ?: "[]"
                            ).forEach { name ->
                                folder =
                                    requireNotNull(folder.findFile(name)) { "Publication folder no longer exists" }
                            }
                            publications.importPublicationFolder(folder, commit)
                        } else {
                            publications.importBook(input.stagedPath?.let { Uri.fromFile(File(it)) }
                                ?: input.uri.toUri(), input.displayName, commit)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        timber.log.Timber.w(error, "Import input failed for operation %s", id)
                        if (retryable(error) && !lastAttempt) throw error
                        database.withTransaction {
                            val current = checked(id, attempt)
                            dao.updateInput(
                                input.copy(
                                    state = if (error is SecurityException || error is java.io.FileNotFoundException) "INPUT_REQUIRED" else "FAILED",
                                    error = errorCode(error)
                                )
                            )
                            dao.update(current.copy(completed = current.completed + 1))
                        }
                    }
                    progress(snapshot(requireNotNull(dao.get(id))))
                }
                database.withTransaction {
                    val current = checked(id, attempt)
                    val items = dao.inputs(id).filter { claimed.kind != "SCAN" || it.ordinal > 0 }
                    val success = items.count { it.state == "SUCCESS" }
                    val state = when {
                        items.all { it.state == "SUCCESS" } -> "SUCCEEDED"
                        success > 0 -> "PARTIAL"
                        items.any { it.state == "INPUT_REQUIRED" } -> "INPUT_REQUIRED"
                        else -> "FAILED"
                    }
                    dao.update(current.copy(state = state))
                }
            }
            Execution.COMPLETE
        } catch (_: Invalidated) {
            database.withTransaction {
                dao.get(id)?.takeIf { it.attempt == attempt && it.state == "RUNNING" }
                    ?.let { dao.update(it.copy(state = "CANCELLED", error = "superseded")) }
            }
            Execution.COMPLETE
        } catch (cancelled: CancellationException) {
            // Scheduler interruption leaves the attempt resumable, unlike explicit user cancellation.
            throw cancelled
        } catch (error: Exception) {
            var retry = false
            database.withTransaction {
                val current = dao.get(id)
                if (current?.attempt == attempt && current.state == "RUNNING") {
                    retry = retryable(error) && !lastAttempt
                    dao.update(
                        current.copy(
                            state = if (retry) "QUEUED" else if (error is SecurityException || error is java.io.FileNotFoundException) "INPUT_REQUIRED" else "FAILED",
                            error = errorCode(error)
                        )
                    )
                }
            }
            if (retry) Execution.RETRY else Execution.COMPLETE
        } finally {
            withContext(NonCancellable) { cleanup(id) }
        }
    }

    private suspend fun checked(
        id: String,
        attempt: String,
        bookId: String? = null
    ): OperationEntity {
        currentCoroutineContext().ensureActive()
        val row = dao.get(id) ?: throw Invalidated()
        val runtime = dao.runtime()
        if (row.state != "RUNNING" || row.attempt != attempt || runtime.maintenance || runtime.generation != row.generation) throw Invalidated()
        if (bookId != null && (dao.deletionSequence(bookId)
                ?: -1L) > row.sequence
        ) throw DeletedInput()
        return row
    }

    private suspend fun discover(operation: OperationEntity, attempt: String) {
        val root = dao.inputs(operation.id).first()
        if (root.state == "DISCOVERED") return
        val found = mutableListOf<OperationInputEntity>()
        val visited = mutableSetOf<String>()
        suspend fun visit(file: DocumentFile, depth: Int, path: List<String>) {
            checked(operation.id, attempt)
            require(depth <= 32 && visited.size < MAX_ITEMS) { "Folder scan limit exceeded" }
            if (!visited.add(file.uri.toString())) return
            if (file.isDirectory && file.findFile("manifest.json") == null) file.listFiles()
                .forEach { visit(it, depth + 1, path + it.name.orEmpty()) }
            else if (file.isDirectory || file.name.orEmpty().substringAfterLast('.')
                    .lowercase() in PublicationFormats.extensions
            ) {
                found += OperationInputEntity(
                    operation.id,
                    found.size + 1,
                    file.uri.toString(),
                    file.name.orEmpty(),
                    file.isDirectory,
                    grantUri = root.grantUri,
                    folderPathJson = if (file.isDirectory) kotlinx.serialization.json.Json.encodeToString(
                        path
                    ) else null
                )
            }
        }
        visit(
            requireNotNull(DocumentFile.fromTreeUri(context, root.uri.toUri())),
            0,
            emptyList()
        )
        database.withTransaction {
            val current = checked(operation.id, attempt)
            dao.insertInputs(found)
            dao.updateInput(root.copy(state = "DISCOVERED"))
            dao.update(current.copy(total = found.size))
        }
    }

    private suspend fun preparationFailed(id: String, error: String) = database.withTransaction {
        dao.get(id)?.takeIf { it.state == "PREPARING" }?.let { row ->
            dao.inputs(id).filter { it.state == "PENDING" }.forEach {
                dao.updateInput(it.copy(state = "INPUT_REQUIRED", error = error))
            }
            dao.update(row.copy(state = "INPUT_REQUIRED", error = error))
        }
    }

    private suspend fun snapshot(row: OperationEntity): OperationSnapshot {
        val failures = dao.failures(row.id)
        return OperationSnapshot(
            row.id, OperationKind.valueOf(row.kind), OperationState.valueOf(row.state), row.bookId,
            row.completed, row.total, failures.map { it.displayName },
            if (failures.any { it.state == "INPUT_REQUIRED" }) "input_required" else row.error,
        )
    }

    private suspend fun retainGrant(uri: Uri): String? {
        if (uri.scheme != "content") return null
        val existing =
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (existing) return uri.toString()
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            dao.ownGrant(OperationUriGrantEntity(uri.toString()))
            uri.toString()
        } catch (_: SecurityException) {
            null
        }
    }

    private fun displayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
    } catch (_: Exception) {
        uri.lastPathSegment.orEmpty()
    }

    private suspend fun cleanup(id: String) = inputMutex.withLock {
        val row = dao.get(id) ?: return@withLock
        if (!OperationState.valueOf(row.state).terminal) return@withLock
        dao.inputs(id).forEach { input ->
            input.stagedPath?.let { path ->
                val file = File(path).canonicalFile
                if (file.parentFile == inputsRoot.canonicalFile) file.delete()
                dao.updateInput(input.copy(stagedPath = null))
            }
        }
        releaseGrants()
    }

    private suspend fun releaseGrants() {
        val backup = preferences.readerSettings.first().backupFolderUri
        dao.unusedGrants().filter { it != backup }.forEach { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* Already revoked by the user/provider. */
            }
            dao.forgetGrant(uri)
        }
    }

    private class Invalidated : CancellationException("Operation superseded")
    private class DeletedInput :
        IllegalStateException("Publication deleted after request was accepted")

    private fun retryable(error: Exception): Boolean =
        error is IOException && error !is java.io.FileNotFoundException

    private fun errorCode(error: Exception): String = when (error) {
        is SecurityException -> "permission_denied"
        is IOException -> "io_error"
        is DeletedInput -> "book_deleted"
        else -> "invalid_input"
    }

    private companion object {
        const val MAX_ITEMS = 10_000
    }
}
