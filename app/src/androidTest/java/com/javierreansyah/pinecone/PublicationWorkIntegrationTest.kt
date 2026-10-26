package com.javierreansyah.pinecone

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.model.OperationState
import com.javierreansyah.pinecone.testing.ReadingTestEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicationWorkIntegrationTest {
    @Test fun hiltWorkerImportsAndCommitsWithoutAnActivityOrViewModel(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PineconeApplication
        app.startup.initialize()
        val services = EntryPointAccessors.fromApplication(app, ReadingTestEntryPoint::class.java)
        val file = File(app.cacheDir, "durable-${UUID.randomUUID()}.epub")
        val title = "Durable worker ${UUID.randomUUID()}"
        ZipOutputStream(file.outputStream()).use { zip ->
            val resources = mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "content.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">$title</dc:identifier><dc:title>$title</dc:title><dc:language>en</dc:language></metadata><manifest><item id="page" href="page.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="page"/></spine></package>""",
                "page.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head><body><p>$title</p></body></html>""",
            )
            resources.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
        val operations = services.operations()
        var operationId: String? = null
        try {
            val id = operations.enqueueImports(listOf(Uri.fromFile(file))).also { operationId = it }
            val result = withTimeout(150_000) { operations.observe(id).first { it?.state?.terminal == true } }!!
            assertEquals(result.toString(), OperationState.SUCCEEDED, result.state)
            val input = services.database().operationDao().inputs(id).single()
            assertEquals("SUCCESS", input.state)
            val book = requireNotNull(app.libraryRepository.getBook(requireNotNull(input.bookId)))
            assertEquals(title, book.book.title)
            assertTrue(File(book.book.filePath).isFile)
        } finally {
            operationId?.let { id ->
                operations.cancel(id)
                services.database().operationDao().inputs(id).mapNotNull { it.bookId }.forEach { app.libraryRepository.deleteBook(it) }
                operations.acknowledge(id)
            }
            file.delete()
        }
    }
}
