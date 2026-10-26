package com.javierreansyah.pinecone.data.publication.conversion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MuPdfExportTest {
    @Test
    fun optionalNativeExportAudit() {
        val path = InstrumentationRegistry.getArguments().getString("pdfPath")
        assumeTrue(path != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pdf = MuPdfDocument(Document.openDocument(requireNotNull(path)))
        try {
            val pages = InstrumentationRegistry.getArguments().getString("pages")?.split(',')
                ?.map { it.trim().toInt() - 1 } ?: listOf(0, 2, 3, 4, 5, 6, 8, 9, 10, 12, 13, 14)
            pages.filter { it in 0..<pdf.pageCount }.forEach { index ->
                listOf("xhtml", "stext.xml").forEach { format ->
                    File(context.cacheDir, "native-base-${index + 1}.$format").writeText(
                        MuPdfPageExporter.export(
                            pdf,
                            index,
                            format,
                            MuPdfPageExporter.PROSE_OPTIONS
                        )
                    )
                }
                listOf("xhtml", "stext.xml", "svg").forEach { format ->
                    File(context.cacheDir, "native-${index + 1}.$format")
                        .writeText(MuPdfPageExporter.export(pdf, index, format))
                }
            }
        } finally {
            pdf.close()
        }
    }
}
