package com.javierreansyah.pinecone.data.publication.conversion

import com.artifex.mupdf.fitz.Buffer
import com.artifex.mupdf.fitz.DisplayList
import com.artifex.mupdf.fitz.DocumentWriter
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Rect
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument

/** Use MuPDF's native exporters, not the Java structured-text vector callback. */
internal object MuPdfPageExporter {
    const val TEXT_OPTIONS = "preserve-images=yes,structured=yes,segment=yes,paragraph-break=yes," +
            "table-hunt=yes,dehyphenate=yes,collect-styles=yes,vectors=yes,accurate-bboxes=yes," +
            "lazy-vectors=yes,vertically-collapse-bordered-cells=no"

    // Region segmentation can detach short final lines from wide paragraphs and emit
    // them before the paragraph. Use native paragraph order for the canonical prose;
    // keep segmented exports only for auxiliary table/figure geometry.
    val PROSE_OPTIONS: String = TEXT_OPTIONS.replace("table-hunt=yes", "table-hunt=no")
        .replace("segment=yes", "segment=no")

    fun read(pdf: MuPdfDocument, index: Int, checkCancelled: () -> Unit): MuPdfPageRecord {
        checkCancelled()
        // Only page loading touches the shared document. Each worker owns its display list;
        // MuPDF can replay independent lists concurrently using the JNI thread-local contexts.
        val (display, bounds) = pdf.access { document ->
            checkCancelled()
            val page = document.loadPage(index)
            try {
                val bounds = page.bounds
                page.toDisplayList(false) to bounds
            } finally {
                page.destroy()
            }
        }
        try {
            fun text(format: String, tables: Boolean): String {
                checkCancelled()
                return export(
                    display, bounds, format,
                    if (tables) TEXT_OPTIONS else PROSE_OPTIONS
                )
            }

            val xhtml = text("xhtml", false)
            val xml = text("stext.xml", false)
            val tableXhtml = text("xhtml", true)
            val tableXml = text("stext.xml", true)
            checkCancelled()
            return MuPdfStructure.read(xhtml, xml, tableXhtml, tableXml)
        } finally {
            display.destroy()
        }
    }

    /** Original paths, glyph outlines, images, masks and clipping stay in one native SVG. */
    fun <T> withSvgExporter(
        pdf: MuPdfDocument,
        index: Int,
        block: ((FigureBounds) -> String) -> T
    ): T = pdf.access { document ->
        var page: Page? = null
        var display: DisplayList? = null
        try {
            block { region ->
                val current =
                    display ?: document.loadPage(index).also { page = it }.toDisplayList(false)
                        .also { display = it }
                export(
                    current, Rect(0f, 0f, region.width, region.height), "svg", "text=path",
                    Matrix(1f, 0f, 0f, 1f, -region.left, -region.top)
                )
            }
        } finally {
            display?.destroy(); page?.destroy()
        }
    }

    fun export(
        pdf: MuPdfDocument,
        index: Int,
        format: String,
        options: String = TEXT_OPTIONS
    ): String = pdf.access { document ->
        val page = document.loadPage(index)
        try {
            export(page, format, options)
        } finally {
            page.destroy()
        }
    }

    private fun export(page: Page, format: String, options: String): String {
        val display = page.toDisplayList(false)
        try {
            return export(
                display,
                page.bounds,
                format,
                if (format == "svg") "text=path" else options
            )
        } finally {
            display.destroy()
        }
    }

    private fun export(
        display: DisplayList, bounds: Rect, format: String, options: String,
        matrix: Matrix = Matrix.Identity()
    ): String {
        val buffer = Buffer()
        try {
            val writer = DocumentWriter(buffer, format, options)
            try {
                val device = writer.beginPage(bounds)
                try {
                    display.run(device, matrix, bounds, null)
                    writer.endPage()
                } finally {
                    device.destroy()
                }
                writer.close()
                return buffer.asString()
            } finally {
                writer.destroy()
            }
        } finally {
            buffer.destroy()
        }
    }
}
