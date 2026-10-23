package com.javierreansyah.pinecone.data.publication.conversion

import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredTextWalker
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import kotlin.math.abs

/** Native handles never escape a callback. Cached pages contain ordinary Kotlin values only. */
internal class PdfLayoutExtractor {
    private data class Style(val bold: Boolean, val italic: Boolean, val math: Boolean)

    private val fontStyles = mutableMapOf<String, Style>()

    fun extract(pdf: MuPdfDocument, index: Int, checkCancelled: () -> Unit): PdfPageLayout =
        pdf.access { doc ->
            val page = doc.loadPage(index)
            try {
                val bounds = page.bounds.toBounds()
                val blocks = mutableListOf<PdfLayoutBlock>()
                // Keep the original block segmentation for saved source anchors. Vector callbacks
                // in StructuredTextWalker are unsafe in MuPDF 1.28.5; use Device for drawings.
                val structured =
                    page.toStructuredText("preserve-images=yes,vectors=no,segment=yes,paragraph-break=yes")
                try {
                    structured.walk(object : StructuredTextWalker {
                        var box = bounds
                        var lines = mutableListOf<PdfTextLine>()
                        var runs = mutableListOf<PdfTextRun>()
                        var text = StringBuilder()
                        var runBounds = bounds
                        var runSize = 12f
                        var runBaseline = 0f
                        var runFont = ""
                        var style = Style(bold = false, italic = false, math = false)
                        var characters = 0
                        fun flushRun() {
                            if (text.isNotEmpty()) {
                                runs += PdfTextRun(
                                    text.toString(),
                                    runBounds,
                                    runBaseline,
                                    runSize,
                                    style.bold,
                                    style.italic,
                                    style.math
                                )
                                text = StringBuilder()
                            }
                        }

                        override fun beginTextBlock(bbox: Rect, flags: Int) {
                            box = bbox.toBounds(); lines = mutableListOf()
                        }

                        override fun beginLine(bbox: Rect, wmode: Int, dir: Point) {
                            runs = mutableListOf()
                        }

                        override fun onChar(
                            c: Int,
                            origin: Point,
                            font: Font,
                            size: Float,
                            q: Quad,
                            argb: Int,
                            flags: Int,
                            bidi: Int
                        ) {
                            if (++characters % 2048 == 0) checkCancelled()
                            if (!Character.isValidCodePoint(c)) return
                            val name = font.name ?: "unknown"
                            val currentStyle = (fontStyles[name] ?: Style(
                                font.isBold, font.isItalic,
                                Regex(
                                    "(?:Math|Symbol|CMSY|CMEX|CMMI|MSAM|MSBM|STIX)",
                                    RegexOption.IGNORE_CASE
                                ).containsMatchIn(name)
                            )
                                .also { if (fontStyles.size < 128) fontStyles[name] = it }).let {
                                if (flags and 8 != 0) it.copy(bold = true) else it
                            }
                            val glyph = q.toRect().toBounds()
                            if (text.isNotEmpty() && (name != runFont || abs(size - runSize) > .05f || abs(
                                    origin.y - runBaseline
                                ) > .1f ||
                                        glyph.left - runBounds.right > size || currentStyle != style)
                            ) flushRun()
                            if (text.isEmpty()) {
                                runFont = name; runSize = size; runBaseline = origin.y; runBounds =
                                    glyph
                                style = currentStyle
                            } else runBounds = runBounds.union(glyph)
                            text.appendCodePoint(c)
                        }

                        override fun endLine() {
                            flushRun(); lines += PdfTextLine(runs.toList())
                        }

                        override fun endTextBlock() {
                            blocks += PdfLayoutBlock("", box, lines.toList())
                        }

                        override fun onImageBlock(bbox: Rect, transform: Matrix, image: Image) {
                            blocks += PdfLayoutBlock("", bbox.toBounds(), image = true)
                        }

                        override fun beginStruct(standard: String, raw: String?, index: Int) = Unit
                        override fun endStruct() = Unit
                        override fun onVector(
                            bbox: Rect,
                            info: StructuredTextWalker.VectorInfo,
                            argb: Int
                        ) = Unit
                    })
                } finally {
                    structured.destroy()
                }
                checkCancelled()
                val graphics = PdfGraphicsDevice()
                val rules: List<FigureBounds>
                try {
                    page.run(graphics, Matrix.Identity())
                    graphics.close()
                    val paths = graphics.bounds.map { it.toBounds() }
                    rules = paths.filter { it.height <= 2f && it.width in 3f..bounds.width * .95f }
                        .take(4096)
                    pdfFigureRegions(bounds, paths).forEach { region ->
                        if (blocks.none {
                                it.image && it.bounds.left <= region.left && it.bounds.top <= region.top &&
                                        it.bounds.right >= region.right && it.bounds.bottom >= region.bottom
                            }) {
                            blocks += PdfLayoutBlock("", region, image = true)
                        }
                    }
                } finally {
                    graphics.destroy()
                }
                PdfPageLayout(bounds, blocks.filter { it.image || it.text.isNotBlank() }, rules)
            } finally {
                page.destroy()
            }
        }

    internal fun Rect.toBounds() = FigureBounds(x0, y0, x1, y1)
}
