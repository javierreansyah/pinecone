package com.javierreansyah.pinecone.testing

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/** Keeps WebView fixtures from hanging or crashing the test process after renderer failure. */
// Verified against webkit 1.17.0's lint.jar with javap: RenderProcessGoneDetector.visitConstructor
// reports every WebViewClient constructor call, including this super call, without checking the
// override below. Keep this exception on this fixture only and recheck it on WebKit upgrades.
// Upstream detector: https://github.com/androidx/androidx/blob/androidx-main/webkit/webkit-lint/src/main/java/androidx/webkit/lint/RenderProcessGoneDetector.kt
@SuppressLint("MissingOnRenderProcessGone")
class FixtureWebViewClient(
    private val onPageLoaded: (WebView) -> Unit,
    private val onRendererExited: (String) -> Unit,
) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) {
        onPageLoaded(view)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        view.destroy()
        onRendererExited("WebView renderer exited (crashed=${detail.didCrash()})")
        return true
    }
}
