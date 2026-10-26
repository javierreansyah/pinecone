package com.javierreansyah.pinecone.testing

import java.io.ByteArrayOutputStream
import java.util.Locale

/** Synthetic, locally authored fixture: no external document or network dependency. */
fun syntheticPdf(pageCount: Int = 1): ByteArray {
    require(pageCount > 0)
    val fontId = 3 + pageCount
    val objects = mutableListOf("<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [${(0 until pageCount).joinToString(" ") { "${it + 3} 0 R" }}] /Count $pageCount >>")
    repeat(pageCount) { index -> objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 $fontId 0 R >> >> /Contents ${fontId + 1 + index} 0 R >>" }
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    repeat(pageCount) { index ->
        val text = "BT /F1 14 Tf 72 720 Td (A readable PDF publication. Page ${index + 1}.) Tj ET"
        objects += "<< /Length ${text.length} >>\nstream\n$text\nendstream"
    }
    val out = ByteArrayOutputStream()
    fun write(text: String) { out.write(text.toByteArray(Charsets.US_ASCII)) }
    write("%PDF-1.4\n")
    val offsets = objects.mapIndexed { index, obj -> out.size().also { write("${index + 1} 0 obj\n$obj\nendobj\n") } }
    val xref = out.size()
    write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
    offsets.forEach { write("%010d 00000 n \n".format(Locale.ROOT, it)) }
    write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF")
    return out.toByteArray()
}
