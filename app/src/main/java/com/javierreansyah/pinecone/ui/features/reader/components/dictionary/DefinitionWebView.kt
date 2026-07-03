package com.javierreansyah.pinecone.ui.features.reader.components.dictionary

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import com.javierreansyah.pinecone.ui.components.HtmlPreset
import com.javierreansyah.pinecone.ui.components.HtmlWebView
import com.javierreansyah.pinecone.ui.components.HtmlWebViewConfig

/**
 * Renders a dictionary definition HTML string inside a themed WebView.
 *
 * This is a focused wrapper around [HtmlWebView] that opts into the
 * [HtmlPreset.Definition] styling and wires up the word-click JS bridge.
 */
@Composable
fun DefinitionWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize,
    onWordClick: (String) -> Unit = {}
) {
    HtmlWebView(
        htmlContent = htmlContent,
        modifier = modifier,
        baseFontSize = baseFontSize,
        config = HtmlWebViewConfig(
            preset = HtmlPreset.Definition,
            onWordClick = onWordClick
        )
    )
}
