package com.javierreansyah.pinecone.data.publication.conversion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import com.javierreansyah.pinecone.testing.InstrumentationLoggingRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Opt-in device benchmark. Compare entry/source-map digests between builds as well as timings. */
@RunWith(AndroidJUnit4::class)
class PdfConversionBenchmarkTest {
    @get:Rule
    val logging: InstrumentationLoggingRule = InstrumentationLoggingRule()

    @Test
    fun syntheticConversionBenchmark(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("synthetic-benchmark-", ".pdf", context.cacheDir)
        try {
            source.writeBytes(com.javierreansyah.pinecone.testing.syntheticPdf(100))
            benchmark(source.absolutePath, 3, "synthetic-100-pages")
        } finally {
            source.delete()
        }
    }

    @Test
    fun syntheticReaderOperationsBenchmark(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("reader-benchmark-", ".pdf", context.cacheDir)
        try {
            source.writeBytes(com.javierreansyah.pinecone.testing.syntheticPdf(100))
            repeat(4) { pass ->
                val started = System.nanoTime()
                val pdf = MuPdfDocument(Document.openDocument(source.absolutePath))
                val openMs = (System.nanoTime() - started) / 1_000_000.0
                try {
                    var bitmapBytes = 0
                    val renderStart = System.nanoTime()
                    for (page in listOf(0, 99, 49, 0)) {
                        val bitmap = pdf.render(page, 600)
                        try {
                            bitmapBytes = maxOf(bitmapBytes, bitmap.allocationByteCount)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    val renderMs = (System.nanoTime() - renderStart) / 1_000_000.0
                    val textStart = System.nanoTime()
                    for (page in listOf(0, 99, 49, 0)) {
                        org.junit.Assert.assertTrue("readable" in pdf.textPage(page).text)
                    }
                    val textMs = (System.nanoTime() - textStart) / 1_000_000.0
                    val searchStart = System.nanoTime()
                    var hits = 0
                    repeat(pdf.pageCount) { hits += pdf.search(it, "readable").matches.size }
                    assertEquals(100, hits)
                    val searchMs = (System.nanoTime() - searchStart) / 1_000_000.0
                    Timber.tag("PdfConversionBenchmark").i(
                        "PDF_READER_BENCHMARK pass=%d pages=100 openMs=%.3f renderFourMs=%.3f textFourMs=%.3f searchAllMs=%.3f bitmapBytes=%d",
                        pass, openMs, renderMs, textMs, searchMs, bitmapBytes,
                    )
                } finally {
                    pdf.close()
                }
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun optionalConversionBenchmark(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val path = arguments.getString("benchmarkPath")
        assumeTrue("Pass benchmarkPath to measure a local PDF", path != null)
        val passes = arguments.getString("benchmarkPasses")?.toInt() ?: 3
        val label = arguments.getString("benchmarkLabel") ?: "current"
        benchmark(requireNotNull(path), passes, label)
    }

    private suspend fun benchmark(path: String, passes: Int, label: String) {
        require(passes > 0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = withContext(Dispatchers.IO) {
            File.createTempFile("conversion-benchmark-", ".epub", context.cacheDir)
        }
        var expected: Map<String, String>? = null
        try {
            // The first conversion warms the VM/native caches; subsequent passes are measured.
            repeat(passes + 1) { pass ->
                val pdf = MuPdfDocument(Document.openDocument(path))
                try {
                    val started = System.nanoTime()
                    var extractionMs = 0L
                    var completed = 0
                    val map = PdfReadModeConverter().convert(
                        listOf(PdfSource(pdf, "original.pdf")), output,
                        "test:benchmark", "Conversion benchmark", "en",
                        GeneratedPublicationStrings(
                            "Contents",
                            "Scanned"
                        ) { "Page $it" }) { done, total ->
                        assertEquals(++completed, done)
                        assertEquals(pdf.pageCount * 2, total)
                        if (done == pdf.pageCount) extractionMs =
                            (System.nanoTime() - started) / 1_000_000
                    }
                    val totalMs = (System.nanoTime() - started) / 1_000_000
                    assertEquals(pdf.pageCount * 2, completed)
                    val digests = ZipFile(output).use { zip ->
                        zip.entries().asSequence().associate { entry ->
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            // EPUB's required modification time is the only variable metadata.
                            val stable =
                                if (entry.name == "EPUB/package.opf") bytes.toString(Charsets.UTF_8)
                                    .replace(
                                        Regex("<meta property=\"dcterms:modified\">[^<]*</meta>"),
                                        ""
                                    )
                                    .toByteArray(Charsets.UTF_8) else bytes
                            entry.name to digest(stable)
                        } + ("source-map" to digest(map.toByteArray(Charsets.UTF_8)))
                    }
                    expected?.let {
                        assertEquals(
                            "Repeated conversions must preserve every entry and source anchor",
                            it,
                            digests
                        )
                    }
                    expected = digests
                    Timber.tag("PdfConversionBenchmark").i(
                        "PDF_BENCHMARK: label=%s, file=%s, pass=%d, pages=%d, " +
                                "extractionMs=%d, totalMs=%d, bytes=%d, contentDigest=%s",
                        label, File(path).name, pass, pdf.pageCount,
                        extractionMs, totalMs, output.length(),
                        digest(digests.toSortedMap().toString().toByteArray(Charsets.UTF_8)),
                    )
                } finally {
                    pdf.close()
                }
            }
        } finally {
            output.delete()
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
