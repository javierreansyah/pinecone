package com.javierreansyah.pinecone

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.local.database.library.OperationEntity
import com.javierreansyah.pinecone.data.model.OperationState
import com.javierreansyah.pinecone.data.publication.conversion.PdfReadModeConverter
import com.javierreansyah.pinecone.testing.ReadingTestEntryPoint
import com.javierreansyah.pinecone.testing.syntheticPdf
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Two explicitly selected steps around an external normal-process-death check; no force-stop. */
class PublicationRecoveryFixture {
    @Test fun seedAcceptedRequestBeforeScheduling(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seedDurableRecovery") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PineconeApplication
        app.startup.initialize()
        app.operations.reconcile()
        // Allow the application's own initial reconciliation to finish before creating the crash window.
        delay(600)
        val services = EntryPointAccessors.fromApplication(app, ReadingTestEntryPoint::class.java)
        val source = File(app.cacheDir, "recovery-${UUID.randomUUID()}.pdf").apply { writeBytes(syntheticPdf(25)) }
        try {
            val book = services.publications().importBook(Uri.fromFile(source))
            val runtime = services.database().operationDao().runtime()
            val operation = OperationEntity(UUID.randomUUID().toString(), "READ_MODE", "QUEUED", runtime.generation,
                runtime.sequence, System.currentTimeMillis(), book.book.id, book.book.id, PdfReadModeConverter.VERSION)
            services.database().operationDao().insert(operation)
            val bytes = JSONObject().put("operationId", operation.id).put("bookId", book.book.id).toString().toByteArray()
            FileOutputStream(File(app.filesDir, "durable-recovery-fixture.json")).use { it.write(bytes); it.fd.sync() }
            assertEquals("QUEUED", services.database().operationDao().get(operation.id)?.state)
        } finally { source.delete() }
    }

    @Test fun verifyRecoveredRequest(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyDurableRecovery") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PineconeApplication
        val file = File(app.filesDir, "durable-recovery-fixture.json")
        val fixture = JSONObject(file.readText())
        val id = fixture.getString("operationId")
        val bookId = fixture.getString("bookId")
        val result = withTimeout(150_000) { app.operations.observe(id).first { it?.state?.terminal == true } }!!
        assertEquals(result.toString(), OperationState.SUCCEEDED, result.state)
        val book = requireNotNull(app.libraryRepository.getBook(bookId))
        assertEquals("read-mode", book.book.activeRendition)
        val services = EntryPointAccessors.fromApplication(app, ReadingTestEntryPoint::class.java)
        assertTrue(File(requireNotNull(services.readingRepository().rendition(bookId, "read-mode")).filePath).isFile)
        app.libraryRepository.deleteBook(bookId)
        app.operations.acknowledge(id)
        file.delete()
    }
}
