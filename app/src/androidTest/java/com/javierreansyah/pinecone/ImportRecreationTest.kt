package com.javierreansyah.pinecone

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.testing.ReadingTestEntryPoint
import com.javierreansyah.pinecone.testing.syntheticPdf
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class ImportRecreationTest {
    @Test
    fun recreatingTheActivityDoesNotSubmitItsExternalImportTwice() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PineconeApplication
        app.startup.initialize()
        val services = EntryPointAccessors.fromApplication(app, ReadingTestEntryPoint::class.java)
        val source = File(app.cacheDir, "recreation-${UUID.randomUUID()}.pdf").apply {
            writeBytes(syntheticPdf() + "\n% ${UUID.randomUUID()}\n".toByteArray())
        }
        val uri = Uri.fromFile(source)
        fun requests(): List<String> = services.database().openHelper.readableDatabase.query(
            "SELECT operationId FROM publication_operation_inputs WHERE uri = ?",
            arrayOf(uri.toString())
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        try {
            val intent = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                withTimeout(15_000L.milliseconds) { while (requests().isEmpty()) delay(25L.milliseconds) }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                // Preparation runs in the application scope; let any accidental duplicate enqueue finish.
                delay(500L.milliseconds)
                assertEquals(1, requests().size)
            }
        } finally {
            requests().forEach { id ->
                app.operations.cancel(id)
                services.database().operationDao().inputs(id).mapNotNull { it.bookId }.forEach {
                    app.libraryRepository.deleteBook(it)
                }
                app.operations.acknowledge(id)
            }
            source.delete()
        }
    }
}
