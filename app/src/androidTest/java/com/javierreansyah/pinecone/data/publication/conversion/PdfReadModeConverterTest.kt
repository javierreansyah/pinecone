package com.javierreansyah.pinecone.data.publication.conversion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfReadModeConverterTest {
    @Test
    fun aVectorChartKeepsItsPathsColorsAndLabelsInOneSvg() = runBlocking {
        val commands = "BT /F1 12 Tf 50 740 Td (This paragraph stays outside the chart.) Tj ET\n" +
            "0 0 0 RG 1 w 100 300 m 100 550 l 420 550 l S\n" +
            "0.1 0.4 0.8 rg 125 350 55 120 re f\n" +
            "0.8 0.2 0.1 rg 225 350 55 180 re f\n" +
            "0.2 0.7 0.3 rg 325 350 55 150 re f\n" +
            "0 0 0 rg BT /F1 12 Tf 150 580 Td (Quarterly results) Tj ET\n" +
            "BT /F1 10 Tf 140 330 Td (Alpha) Tj ET\n" +
            "BT /F1 10 Tf 240 330 Td (Beta) Tj ET\n" +
            "BT /F1 10 Tf 340 330 Td (Gamma) Tj ET\n" +
            "BT /F1 10 Tf 300 20 Td (1) Tj ET\n"
        val pdf = MuPdfDocument(Document.openDocument(vectorPdf(commands), "application/pdf"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File(context.cacheDir, "native-chart-audit.epub")
        try {
            val map = PdfReadModeConverter().convert(listOf(PdfSource(pdf, "chart.pdf")), destination,
                "test:chart", "Chart audit", "en",
                GeneratedPublicationStrings("Contents", "Scanned", "Original") { "Page $it" }) { _, _ -> }
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(destination.inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
            }
            val page = MuPdfStructure.parse(entries.getValue("EPUB/text/content.xhtml").toString(Charsets.UTF_8))
            assertTrue(page.textContent.contains("This paragraph stays outside the chart."))
            assertTrue("Chart labels must not become separate reading paragraphs", !page.textContent.contains("Quarterly results"))
            val assets = entries.filterKeys { it.startsWith("EPUB/images/") }
            assertEquals("All paths and labels belong to one chart", 1, assets.size)
            assertTrue(assets.keys.single().endsWith(".svg"))
            val svg = MuPdfStructure.parse(assets.values.single().toString(Charsets.UTF_8))
            assertTrue("A distant printed page number must not enlarge the chart crop",
                svg.getAttribute("viewBox").split(' ')[3].toFloat() < 400f)
            assertTrue(svg.descendants("path").size >= 3)
            assertTrue("Native glyph outlines must keep the labels in the chart", svg.descendants("use").isNotEmpty())
            val colors = svg.descendants().map { it.getAttribute("fill") }.filter { it.isNotBlank() }.toSet()
            assertTrue("Keep original chart colors", colors.size >= 3)
            val sourceMap = kotlinx.serialization.json.Json.decodeFromString<List<SourcePage>>(map)
            assertTrue(sourceMap.single().blocks.any { block -> block.textRuns.any { it.image } })
        } finally { pdf.close() }
    }

    @Test
    fun nativeXmlSerializationPreservesSupplementaryMathAndLaterPages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = File.createTempFile("unicode-content-", ".xhtml", context.cacheDir)
        val output = ByteArrayOutputStream()
        val symbol = "\uD835\uDC5D"
        val math = ReflowMath(FigureBounds(0f, 0f, 10f, 10f),
            listOf(ReflowMathText(symbol, 0f, 8f, 8f, 10f)), emptyList(), 10f, 8f)
        EpubWriter(output, "test:unicode", "Math $symbol", "en",
            GeneratedPublicationStrings("Contents", "Scanned", "Original") { "Page $it" }, content).use {
            it.page(1, listOf(ReflowBlock(text = "Probability $symbol")))
            it.page(2, listOf(ReflowBlock(kind = "equation", sources = listOf(ReflowSource("math", math.bounds, symbol,
                listOf(ReflowRun(symbol, 0, symbol.length, math = math)))))))
            it.page(3, listOf(ReflowBlock(text = "The final page is still readable.")))
            it.finish()
        }
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith(".xml") || entry.name.endsWith(".opf") || entry.name.endsWith(".xhtml")) {
                    val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                        .newDocumentBuilder().parse(zip.readBytes().inputStream())
                    if (entry.name == "EPUB/text/content.xhtml") {
                        assertEquals(3, xml.getElementsByTagNameNS("*", "section").length)
                        assertTrue(xml.documentElement.textContent.contains(symbol))
                        assertTrue(xml.documentElement.textContent.contains("The final page is still readable."))
                        val svg = xml.getElementsByTagNameNS("http://www.w3.org/2000/svg", "svg").item(0) as org.w3c.dom.Element
                        assertEquals(symbol, svg.getAttribute("aria-label"))
                    }
                }
            }
        }
    }

    @Test
    fun optionalThesisConversionReachesTheFinalPdfPage() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("thesisPath")
        org.junit.Assume.assumeTrue("Pass thesisPath to audit the local thesis", path != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val native = MuPdfDocument(Document.openDocument(requireNotNull(path)))
        val destination = File(context.cacheDir, "thesis-reconstruction-audit.epub")
        val started = System.nanoTime()
        try {
            var completed = 0
            val map = PdfReadModeConverter().convert(listOf(PdfSource(native, "thesis.pdf")), destination,
                "test:thesis", "Thesis conversion audit", "id",
                GeneratedPublicationStrings("Contents", "Scanned", "Original") { "Page $it" }) { done, total ->
                assertEquals(native.pageCount * 2, total); completed = done
            }
            assertEquals(native.pageCount * 2, completed)
            val sourceMap = kotlinx.serialization.json.Json.decodeFromString<List<SourcePage>>(map)
            assertEquals(native.pageCount, sourceMap.size)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(destination.inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
            }
            val bytes = entries.getValue("EPUB/text/content.xhtml")
            val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(bytes.inputStream())
            assertEquals(native.pageCount, xml.getElementsByTagNameNS("*", "section").length)
            val all = xml.getElementsByTagName("*")
            val ids = (0 until all.length).mapNotNull { all.item(it).attributes.getNamedItem("id")?.nodeValue }.toSet()
            (1..native.pageCount).forEach { assertTrue("Missing original page $it", "source-page-$it" in ids) }
            assertTrue("Supplementary math letters must survive serialization", xml.documentElement.textContent.contains("\uD835\uDC5D"))
            assertEquals(1, entries.keys.count { it.startsWith("EPUB/text/") })
            verifyWebViewReachesLastPage(bytes.toString(Charsets.UTF_8), native.pageCount)
            println("THESIS_RECONSTRUCTION: pages=${native.pageCount}, htmlBytes=${bytes.size}, elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        } finally { native.close() }
    }

    private fun verifyWebViewReachesLastPage(html: String, pages: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = java.util.concurrent.CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicReference<String>()
        var view: android.webkit.WebView? = null
        instrumentation.runOnMainSync {
            view = android.webkit.WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(webView: android.webkit.WebView, url: String) {
                        webView.evaluateJavascript("[document.querySelectorAll('section[data-source-page]').length," +
                            "!!document.getElementById('source-page-$pages')]") { value -> result.set(value); ready.countDown() }
                    }
                }
                loadDataWithBaseURL("https://localhost/thesis.xhtml", html, "application/xhtml+xml", "UTF-8", null)
            }
        }
        try {
            assertTrue("WebView did not finish parsing the converted thesis", ready.await(30, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("The reader DOM must reach the final source page", "[$pages,true]", result.get())
        } finally { instrumentation.runOnMainSync { view?.destroy() } }
    }

    @Test
    fun convertsFilledVectorArtToNativeSvgWithoutFullPageDuplication() = runBlocking {
        val pdf = MuPdfDocument(Document.openDocument(vectorPdf(), "application/pdf"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File.createTempFile("vector-conversion-", ".epub", context.cacheDir)
        try {
            var completed = 0
            PdfReadModeConverter().convert(
                listOf(PdfSource(pdf, "original.pdf")), destination, "test:vector", "Vector PDF", "en",
                GeneratedPublicationStrings("Contents", "Scanned page", "Original page") { "Page $it" },
            ) { page, total ->
                assertEquals(2, total)
                completed = page
            }
            assertEquals(2, completed)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(destination.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
            val page = entries.getValue("EPUB/text/content.xhtml").toString(Charsets.UTF_8)
            assertTrue(page.contains("A PDF with extractable text."))
            val images = entries.filterKeys { it.startsWith("EPUB/images/") }
            assertEquals(1, images.size)
            assertTrue(images.keys.single().endsWith(".svg"))
            val svg = MuPdfStructure.parse(images.values.single().toString(Charsets.UTF_8))
            val viewport = svg.getAttribute("viewBox").split(' ').map(String::toFloat)
            assertTrue("Keep the drawing as a vector region, not the whole 600 x 800 page", viewport[2] in 150f..160f && viewport[3] in 100f..110f)
            assertTrue(svg.descendants("path").isNotEmpty())
        } finally {
            pdf.close()
            destination.delete()
        }
    }

    @Test
    fun upgradingLegacyRenditionPreservesBookmarkIdsNotesPreferencesAndReadingState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.javierreansyah.pinecone.data.local.database.library.AppDatabase::class.java).build()
        val http = org.readium.r2.shared.util.http.DefaultHttpClient()
        val retriever = org.readium.r2.shared.util.asset.AssetRetriever(context.contentResolver, http)
        val opener = org.readium.r2.streamer.PublicationOpener(
            publicationParser = org.readium.r2.streamer.parser.DefaultPublicationParser(
                context = context, httpClient = http, assetRetriever = retriever,
                pdfFactory = com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory(),
            ),
        )
        val repository = com.javierreansyah.pinecone.data.repository.library.LibraryRepository(
            context, db, db.bookDao(), db.bookmarkDao(), db.shelfDao(), db.noteDao(), db.spaceDao(), opener, retriever,
        )
        val source = File.createTempFile("migration-source-", ".pdf", context.cacheDir).apply { writeBytes(vectorPdf()) }
        val oldFile = File.createTempFile("migration-legacy-", ".epub", context.cacheDir)
        var generated: File? = null
        try {
            val id = java.util.UUID.randomUUID().toString()
            val legacy = """{"href":"text/page-1.xhtml","type":"application/xhtml+xml","title":"Chapter one",
                "text":{"highlight":"A PDF with extractable text."},"locations":{"cssSelector":"#block-0","totalProgression":0.2}}"""
            val originalLocator = """{"href":"original.pdf","type":"application/pdf","locations":{"fragments":["page=1"]}}"""
            db.bookDao().insert(com.javierreansyah.pinecone.data.local.database.library.BookEntity(
                id = id, title = "Migration", filePath = source.absolutePath,
            ))
            val old = com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity(
                id, "read-mode", oldFile.absolutePath, "application/epub+zip", "EPUB", "legacy",
                converterVersion = 2, lastLocatorJson = legacy, furthestLocatorJson = legacy,
                jumpOriginLocatorJson = legacy, preferencesJson = "{\"fontSize\":1.3}", progression = .2,
            )
            db.publicationDao().putRenditions(listOf(old,
                com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity(
                    id, "original", source.absolutePath, "application/pdf", "PDF", "original",
                )))
            val bookmark = com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity(
                bookId = id, locatorJson = legacy, chapterTitle = "Chapter one", renditionKey = "read-mode",
            )
            val bookmarkId = db.bookmarkDao().insert(bookmark)
            val originalId = db.bookmarkDao().insert(bookmark.copy(locatorJson = originalLocator, renditionKey = "original"))
            val note = com.javierreansyah.pinecone.data.local.database.library.NoteEntity(
                bookId = id, locatorJson = legacy, chapterTitle = "Chapter one", noteText = "Keep this note",
                color = 123, renditionKey = "read-mode",
            )
            val noteId = db.noteDao().insert(note)
            val updated = repository.createReadMode(id) { _, _ -> }
            generated = File(updated.filePath)
            val expected = requireNotNull(GeneratedEpubLocations.migrate(legacy))
            assertEquals(bookmark.copy(id = bookmarkId, locatorJson = expected), db.bookmarkDao().getByRendition(id, "read-mode").single())
            assertEquals(note.copy(id = noteId, locatorJson = expected), db.noteDao().getByRendition(id, "read-mode").single())
            assertEquals(originalId, db.bookmarkDao().getByRendition(id, "original").single().id)
            assertEquals(originalLocator, db.bookmarkDao().getByRendition(id, "original").single().locatorJson)
            assertEquals(old.preferencesJson, updated.preferencesJson)
            assertEquals(old.progression, updated.progression, 0.0)
            assertEquals(expected, updated.lastLocatorJson)
            assertEquals(expected, updated.furthestLocatorJson)
            assertEquals(expected, updated.jumpOriginLocatorJson)
            assertEquals(updated, repository.createReadMode(id, onConversionStart = { error("Cached rendition reconverted") }) { _, _ -> })
            repository.activateRendition(id, "read-mode")
            val publication = requireNotNull(repository.openPublication(requireNotNull(repository.getBook(id))))
            try {
                assertEquals(1, publication.readingOrder.size)
                assertEquals("source-page-1", publication.locatorFromLink(publication.tableOfContents.single())?.locations?.fragments?.single())
            } finally {
                publication.close()
            }
        } finally {
            generated?.delete()
            oldFile.delete()
            source.delete()
            db.close()
        }
    }


    @Test
    fun optionalResearchPaperReconstructionAudit() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("pdfPath")
        org.junit.Assume.assumeTrue("Pass pdfPath to audit a local research paper", path != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val native = MuPdfDocument(Document.openDocument(requireNotNull(path)))
        val output = File(context.cacheDir, "pdf-reconstruction-audit.epub")
        val started = System.nanoTime()
        try {
            if (InstrumentationRegistry.getArguments().getString("dumpLayout") == "true") {
                val extractor = PdfLayoutExtractor()
                (0 until native.pageCount).forEach { index ->
                    File(context.cacheDir, "pdf-layout-${index + 1}.json").writeText(
                        kotlinx.serialization.json.Json.encodeToString(extractor.extract(native, index) {}))
                }
            }
            val map = PdfReadModeConverter().convert(listOf(PdfSource(native, "original.pdf")), output,
                "test:research-paper", "Research paper audit", "en",
                GeneratedPublicationStrings("Contents", "Scanned page", "Original page") { "Page $it" }) { _, _ -> }
            File(context.cacheDir, "pdf-reconstruction-audit-map.json").writeText(map)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(output.inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
            }
            val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                .parse(entries.getValue("EPUB/text/content.xhtml").inputStream())
            val headings = (1..6).flatMap { level ->
                val nodes = xml.getElementsByTagNameNS("*", "h$level")
                (0 until nodes.length).map { "h$level" to nodes.item(it).textContent.trim().replace(Regex("\\s+"), " ") }
            }
            File(context.cacheDir, "pdf-reconstruction-audit-headings.txt").writeText(headings.joinToString("\n") { "${it.first}: ${it.second}" })
            assertTrue("Introduction must be a single numbered heading: $headings", headings.any { it.second == "1 Introduction" })
            assertTrue("Attention must be a single subsection heading: $headings", headings.any { it.first == "h3" && it.second == "3.2 Attention" })
            assertTrue("Watermarks and table labels must not become headings", headings.none {
                it.second.startsWith("arXiv:") || it.second.startsWith("Provided proper attribution") ||
                    it.second == "Parser Training" || it.second == "WSJ 23 F1"
            })
            val introduction = headings.first { it.second == "1 Introduction" }.first
            assertEquals(introduction, headings.first { it.second.startsWith("4 Why Self-Attention") }.first)
            val tables = xml.getElementsByTagNameNS("*", "table")
            assertEquals("All four research-paper tables should contain selectable HTML cells", 4, tables.length)
            assertTrue(tables.item(0).textContent.contains("Sequential Operations"))
            val tableTwo = tables.item(1) as org.w3c.dom.Element
            val tableTwoCells = tableTwo.getElementsByTagNameNS("*", "td")
            assertTrue((0 until tableTwoCells.length).any {
                val cell = tableTwoCells.item(it) as org.w3c.dom.Element
                cell.textContent.trim() == "BLEU"
            })
            val paragraphs = xml.getElementsByTagNameNS("*", "p")
            val convolution = (0 until paragraphs.length).map { paragraphs.item(it) as org.w3c.dom.Element }
                .single { it.textContent.contains("Separable convolutions") }
            assertTrue(convolution.textContent.contains("factor of k. Separable convolutions"))
            assertEquals("Inline math on consecutive prose lines must not create a spurious stacked expression",
                0, convolution.getElementsByTagNameNS("http://www.w3.org/2000/svg", "svg").length)
            val references = (0 until paragraphs.length).map { paragraphs.item(it) as org.w3c.dom.Element }
                .filter { it.getAttribute("class") == "reference" }
            assertEquals("Line fragments should form the original 40 bibliography entries", 40, references.size)
            assertTrue(references.first().textContent.contains("arXiv:1607.06450, 2016."))
            assertTrue(references[8].textContent.contains("sequence to sequence learning."))
            val vectors = xml.getElementsByTagNameNS("http://www.w3.org/2000/svg", "svg")
            val attention = (0 until vectors.length).map { vectors.item(it) as org.w3c.dom.Element }
                .single { it.getAttribute("aria-label").startsWith("Attention(") }
            assertTrue("Use MuPDF's original glyph outlines, including the denominator radical",
                attention.getElementsByTagNameNS("*", "path").length > 1)
            assertTrue(attention.getAttribute("aria-label").contains("\u221a"))
            val optimizer = (0 until vectors.length).map { vectors.item(it) as org.w3c.dom.Element }
                .single { it.textContent.startsWith("lrate") }
            assertTrue("The complete optimizer expression must stay together", optimizer.textContent.contains("warmup"))
            assertTrue(optimizer.textContent.contains("model"))
            assertTrue(optimizer.getAttribute("aria-label").contains("warmup"))
            assertEquals("Extracted formulas must not contain bitmap images", 0, optimizer.getElementsByTagNameNS("*", "img").length)
            val bullets = xml.getElementsByTagNameNS("*", "ul")
            val applications = (0 until bullets.length).map { bullets.item(it) as org.w3c.dom.Element }
                .single { it.textContent.contains("encoder-decoder attention") }
            assertEquals("Section 3.2.3 must have exactly three list items", 3, applications.getElementsByTagNameNS("*", "li").length)
            assertTrue("A separate continuation block must stay inside the second item",
                applications.getElementsByTagNameNS("*", "li").item(1).textContent.trim().endsWith("encoder."))
            val all = xml.getElementsByTagName("*")
            val ids = (0 until all.length).mapNotNull { all.item(it).attributes.getNamedItem("id")?.nodeValue }
            assertEquals("Source and run anchors must remain unique inside tables", ids.size, ids.toSet().size)
            val sourceMap = kotlinx.serialization.json.Json.decodeFromString<List<SourcePage>>(map)
            sourceMap.flatMap { it.blocks }.forEach { block ->
                assertTrue("Missing source anchor ${block.anchor}", block.anchor in ids)
                block.textRuns.forEach { assertTrue("Missing text run ${it.anchor}", it.anchor in ids) }
            }
            println("PDF_RECONSTRUCTION: pages=${native.pageCount}, elapsedMs=${(System.nanoTime() - started) / 1_000_000}, outputBytes=${output.length()}")
        } finally { native.close() }
    }


    @Test
    fun nativeInlineSuperscriptsAndSubscriptsStayTextAndNeedNoBitmaps() = runBlocking {
        val content = "BT /F1 12 Tf 1 0 0 1 50 700 Tm (We compute ) Tj " +
            "1 0 0 1 116 700 Tm (x) Tj /F1 8 Tf 1 0 0 1 122 705 Tm (2) Tj " +
            "/F1 12 Tf 1 0 0 1 126 700 Tm ( + y) Tj /F1 8 Tf 1 0 0 1 152 697 Tm (i) Tj " +
            "/F1 12 Tf 1 0 0 1 158 700 Tm ( for each input in this example.) Tj ET\n"
        val pdf = MuPdfDocument(Document.openDocument(vectorPdf(content), "application/pdf"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("inline-text-", ".epub", context.cacheDir)
        try {
            PdfReadModeConverter().convert(listOf(PdfSource(pdf, "inline.pdf")), file, "test:inline", "Inline", "en",
                GeneratedPublicationStrings("Contents", "Scanned", "Original") { "Page $it" }) { _, _ -> }
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(file.inputStream()).use { zip ->
                while (true) { val e = zip.nextEntry ?: break; entries[e.name] = zip.readBytes() }
            }
            assertTrue("Text-only math must not rasterize its page", entries.keys.none { it.startsWith("EPUB/images/") })
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                .parse(entries.getValue("EPUB/text/content.xhtml").inputStream())
            val sup = doc.getElementsByTagNameNS("*", "sup")
            val sub = doc.getElementsByTagNameNS("*", "sub")
            assertTrue((0 until sup.length).any { sup.item(it).textContent == "2" })
            assertTrue((0 until sub.length).any { sub.item(it).textContent == "i" })
        } finally { pdf.close(); file.delete() }
    }

    @Test
    fun cancellationRemovesTemporaryPageRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("layout-") }.map { it.name }.toSet()
        val pdf = MuPdfDocument(Document.openDocument(vectorPdf(), "application/pdf"))
        val file = File.createTempFile("cancelled-layout-", ".epub", context.cacheDir)
        try {
            var cancelled = false
            try {
                PdfReadModeConverter().convert(listOf(PdfSource(pdf, "original.pdf")), file, "test:cancel", "Cancel", "en",
                    GeneratedPublicationStrings("Contents", "Scanned", "Original") { "Page $it" }) { _, _ ->
                    throw kotlinx.coroutines.CancellationException("Test cancellation after extraction")
                }
            } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertEquals(before, context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("layout-") }.map { it.name }.toSet())
        } finally { pdf.close(); file.delete() }
    }

    private fun vectorPdf(customContent: String? = null): ByteArray {
        val content = customContent ?: "1 1 1 rg 0 0 600 800 re f\n" +
            "0 0 0 rg 100 300 150 100 re f\n" +
            "BT /F1 12 Tf 50 700 Td (A PDF with extractable text.) Tj ET\n"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] " +
                "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length ${content.length} >>\nstream\n${content}endstream",
        )
        val output = ByteArrayOutputStream()
        fun write(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        write("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, value ->
            output.size().also { write("${index + 1} 0 obj\n$value\nendobj\n") }
        }
        val xref = output.size()
        write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return output.toByteArray()
    }
}
