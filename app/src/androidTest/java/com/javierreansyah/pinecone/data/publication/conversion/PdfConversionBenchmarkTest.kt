package com.javierreansyah.pinecone.data.publication.conversion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import com.javierreansyah.pinecone.testing.InstrumentationLoggingRule
import kotlinx.coroutines.runBlocking
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
    fun optionalConversionBenchmark(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val path = arguments.getString("benchmarkPath")
        assumeTrue("Pass benchmarkPath to measure a local PDF", path != null)
        val passes = arguments.getString("benchmarkPasses")?.toInt() ?: 3
        val label = arguments.getString("benchmarkLabel") ?: "current"
        require(passes > 0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File.createTempFile("conversion-benchmark-", ".epub", context.cacheDir)
        var expected: Map<String, String>? = null
        try {
            // The first conversion warms the VM/native caches; subsequent passes are measured.
            repeat(passes + 1) { pass ->
                val pdf = MuPdfDocument(Document.openDocument(requireNotNull(path)))
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
