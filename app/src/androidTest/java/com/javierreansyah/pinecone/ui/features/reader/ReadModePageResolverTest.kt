package com.javierreansyah.pinecone.ui.features.reader

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReadModePageResolverTest {
    @Test
    // The resolver needs JavaScript against this inline test fixture only. External loading
    // is disabled; no untrusted document or remote content is executed by this test WebView.
    @SuppressLint("SetJavaScriptEnabled")
    fun anonymousTablesAndSvgResolveToTheirSourcePageAndPreserveMovedReferencePages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1)
        val rendererFailure = AtomicReference<String>()
        var view: WebView? = null
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                layout(0, 0, 1080, 1800)
                // Lint flags this super-constructor call despite the override below, which
                // destroys the WebView, unblocks the test, and returns true. The detector's
                // visitConstructor reports calls whose source PSI is not KtSuperTypeCallEntry:
                // https://github.com/androidx/androidx/blob/androidx-main/webkit/webkit-lint/src/main/java/androidx/webkit/lint/RenderProcessGoneDetector.kt
                @SuppressLint("MissingOnRenderProcessGone")
                class FixtureWebViewClient : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        loaded.countDown()
                    }

                    override fun onRenderProcessGone(webView: WebView, detail: RenderProcessGoneDetail): Boolean {
                        rendererFailure.set("WebView renderer exited (crashed=${detail.didCrash()})")
                        view = null
                        webView.destroy()
                        loaded.countDown()
                        return true
                    }
                }
                webViewClient = FixtureWebViewClient()
                loadDataWithBaseURL(
                    "https://readium_package/EPUB/text/content-0004.xhtml", """
                    <html><body>
                    <section data-source-page="85" style="display:none"><p>Earlier page</p></section>
                    <section data-source-page="86"><table><tr><td>
                    <svg id="anonymous"><g><path d="M0 0L10 10"/></g></svg>
                    </td></tr></table><p><span id="page-88-native-2-new"><b id="continuation">Continued entry</b></span></p></section>
                    <section data-source-page="87"><p>Next page</p></section>
                    </body></html>
                """.trimIndent(), "text/html", "UTF-8", null
                )
            }
        }
        try {
            assertTrue("WebView did not load the page fixture", loaded.await(10, TimeUnit.SECONDS))
            rendererFailure.get()?.let { throw AssertionError(it) }
            listOf(
                "#anonymous > g > path" to 86, "section:nth-child(3) > p" to 87,
                "#continuation" to 88, null to 86, "#missing" to 86
            ).forEach { (selector, page) ->
                val completed = CountDownLatch(1)
                val result = AtomicReference<String>()
                instrumentation.runOnMainSync {
                    requireNotNull(view).evaluateJavascript(ReadModePageResolver.script(selector)) {
                        result.set(it); completed.countDown()
                    }
                }
                assertTrue(
                    "Source page lookup did not complete",
                    completed.await(10, TimeUnit.SECONDS)
                )
                val resolved = JSONObject(result.get())
                assertEquals("Wrong source page for $selector", page, resolved.getInt("page"))
                assertEquals("/EPUB/text/content-0004.xhtml", resolved.getString("href"))
            }
        } finally {
            instrumentation.runOnMainSync { view?.destroy() }
        }
    }
}
