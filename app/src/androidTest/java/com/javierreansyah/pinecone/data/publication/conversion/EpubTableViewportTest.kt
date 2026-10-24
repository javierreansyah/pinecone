package com.javierreansyah.pinecone.data.publication.conversion

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class EpubTableViewportTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun tallTablesScrollLocallyWithoutExpandingOtherColumns() {
        val output = ByteArrayOutputStream()
        EpubWriter(output, "test:viewport", "Viewport", "en",
            GeneratedPublicationStrings("Contents", "Scanned") { "Page $it" }).use {
            it.page(1, listOf(ReflowBlock("Synthetic content")))
            it.finish()
        }
        var css = ""
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "EPUB/styles/book.css") css = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(css.isNotBlank())
        val rows = (1..150).joinToString("") { "<tr><td>Row $it</td><td>Value $it</td></tr>" }
        val html = """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"/>
            <style>$css
            html{height:480px;width:360px;column-width:360px;column-gap:0;column-fill:auto;font-size:16px}
            #after{break-before:column}
            </style></head><body><div class="table-scroll" id="table"><table>$rows</table></div>
            <p id="after">A short page after the table.</p></body></html>"""
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val result = AtomicReference<String>()
        var view: WebView? = null
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript("""(function(){
                            var t=document.getElementById('table'),p=document.getElementById('after');
                            var before=t.scrollTop;t.scrollTop=t.scrollHeight;
                            return {height:document.documentElement.scrollHeight,
                                tableHeight:t.getBoundingClientRect().height,
                                scrolled:t.scrollTop>before,
                                afterBottom:p.getBoundingClientRect().bottom};
                        })()""") { result.set(it); ready.countDown() }
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        view.destroy()
                        result.set("{}")
                        ready.countDown()
                        return true
                    }
                }
                measure(android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(480, android.view.View.MeasureSpec.EXACTLY))
                layout(0, 0, 360, 480)
                loadDataWithBaseURL("https://localhost/viewport", html, "text/html", "UTF-8", null)
            }
        }
        try {
            assertTrue("WebView did not finish", ready.await(30, TimeUnit.SECONDS))
            val metrics = JSONObject(result.get())
            assertTrue("Tall table expanded the document: $metrics", metrics.getDouble("height") <= 481)
            assertTrue("Wrapper must fit the reading viewport: $metrics", metrics.getDouble("tableHeight") <= 480)
            assertTrue("All rows must remain reachable by local scrolling: $metrics", metrics.getBoolean("scrolled"))
            assertTrue("Following page must stay within the viewport: $metrics", metrics.getDouble("afterBottom") <= 481)
        } finally {
            instrumentation.runOnMainSync { view?.destroy() }
        }
    }
}
