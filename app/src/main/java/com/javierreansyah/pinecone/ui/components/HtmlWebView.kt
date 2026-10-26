@file:Suppress("SameReturnValue")

package com.javierreansyah.pinecone.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Color as AndroidColor

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    onWordClick: ((String) -> Unit)? = null,
    baseFontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize,
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val fontSizeCss = with(density) { baseFontSize.toPx() / density.density }

    val onSurface = colorScheme.onSurface
    val onSurfaceVariant = colorScheme.onSurfaceVariant
    val primary = colorScheme.primary
    val primaryContainer = colorScheme.primaryContainer
    val onPrimaryContainer = colorScheme.onPrimaryContainer
    val surfaceContainerHigh = colorScheme.surfaceContainerHigh
    val outline = colorScheme.outline
    val outlineVariant = colorScheme.outlineVariant
    val secondary = colorScheme.secondary
    val tertiary = colorScheme.tertiary

    val wordClick = rememberUpdatedState(onWordClick)
    val wordClickEnabled = onWordClick != null
    val fullHtml = remember(
        wordClickEnabled,
        htmlContent,
        onSurface, onSurfaceVariant, primary, primaryContainer, onPrimaryContainer,
        surfaceContainerHigh, outline, outlineVariant, secondary, tertiary,
        fontSizeCss,
    ) {
        buildHtml(
            body = ImportedHtml.clean(htmlContent),
            fontSizeCss = fontSizeCss,
            includeWordClickJs = onWordClick != null,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            primary = primary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            outline = outline,
            outlineVariant = outlineVariant,
            secondary = secondary,
            tertiary = tertiary,
        )
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                isSaveEnabled = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = onWordClick != null
                    loadWithOverviewMode = false
                    useWideViewPort = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onWordClick(word: String) {
                            post { wordClick.value?.invoke(word) }
                        }
                    },
                    "Android",
                )
            }
        },
        update = { webView ->
            webView.settings.javaScriptEnabled = wordClickEnabled
            if (webView.tag != fullHtml) {
                webView.tag = fullHtml
                webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            webView.destroy()
        },
    )
}

private fun buildHtml(
    body: String,
    fontSizeCss: Float,
    includeWordClickJs: Boolean,
    onSurface: Color,
    onSurfaceVariant: Color,
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    surfaceContainerHigh: Color,
    outline: Color,
    outlineVariant: Color,
    secondary: Color,
    tertiary: Color,
): String {
    val wordClickJs = if (includeWordClickJs) wordClickScript() else ""

    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
:root {
    --on-surface: ${onSurface.toCssRgba()};
    --on-surface-variant: ${onSurfaceVariant.toCssRgba()};
    --primary: ${primary.toCssRgba()};
    --primary-container: ${primaryContainer.toCssRgba()};
    --on-primary-container: ${onPrimaryContainer.toCssRgba()};
    --surface-container-high: ${surfaceContainerHigh.toCssRgba()};
    --outline: ${outline.toCssRgba()};
    --outline-variant: ${outlineVariant.toCssRgba()};
    --secondary: ${secondary.toCssRgba()};
    --tertiary: ${tertiary.toCssRgba()};
}

${baseCss()}

body {
    font-size: ${fontSizeCss}px;
    padding: 0 24px;
}

${definitionCss()}
</style>
</head>
<body>
$body
</body>
$wordClickJs
</html>
    """.trimIndent()
}

private fun baseCss() = """
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: 'Roboto', 'Noto Sans', system-ui, -apple-system, sans-serif;
    line-height: 1.6;
    color: var(--on-surface);
    background: transparent;
    word-wrap: break-word;
    overflow-wrap: break-word;
    -webkit-text-size-adjust: none;
}

h1, h2, h3, h4, h5, h6 {
    color: var(--primary);
    margin-top: 0.8em;
    margin-bottom: 0.3em;
    line-height: 1.3;
}
h1 { font-size: 1.4em; }
h2 { font-size: 1.25em; }
h3 { font-size: 1.15em; }
h4 { font-size: 1.05em; }
h5, h6 { font-size: 1em; }

body > h1:first-child,
body > h2:first-child,
body > h3:first-child,
body > h4:first-child { margin-top: 0; }

p { margin-bottom: 0.6em; }

ol, ul {
    padding-left: 1.5em;
    margin-top: 0.3em;
    margin-bottom: 0.5em;
}

li {
    margin-bottom: 0.35em;
    line-height: 1.55;
}

li::marker {
    color: var(--primary);
    font-weight: 600;
}

li > ol, li > ul {
    margin-top: 0.2em;
    margin-bottom: 0.2em;
}

dl { margin-bottom: 0.5em; }
dt {
    font-weight: 700;
    color: var(--primary);
    margin-top: 0.5em;
}
dd {
    margin-left: 1.2em;
    margin-bottom: 0.3em;
}

a {
    color: var(--primary);
    text-decoration: none;
}

i, em {
    color: var(--on-surface-variant);
    font-style: italic;
}

b, strong {
    font-weight: 700;
    color: var(--on-surface);
}

blockquote {
    border-left: 3px solid var(--primary-container);
    padding: 0.3em 0 0.3em 0.8em;
    margin: 0.4em 0;
    color: var(--on-surface-variant);
    font-style: italic;
    background: var(--surface-container-high);
    border-radius: 0 6px 6px 0;
}

table {
    width: 100%;
    border-collapse: collapse;
    margin: 0.5em 0;
    font-size: 0.92em;
}
th, td {
    border: 1px solid var(--outline-variant);
    padding: 0.35em 0.6em;
    text-align: left;
}
th {
    background: var(--surface-container-high);
    color: var(--on-surface);
    font-weight: 600;
}
td { color: var(--on-surface); }

hr {
    border: none;
    border-top: 1px solid var(--outline-variant);
    margin: 0.8em 0;
}

code, .IPA {
    font-family: 'Roboto Mono', 'Noto Sans Mono', monospace;
    font-size: 0.9em;
    color: var(--tertiary);
    background: var(--surface-container-high);
    padding: 0.1em 0.35em;
    border-radius: 4px;
}

sup, sub { font-size: 0.75em; line-height: 0; }
sup { vertical-align: super; }
sub { vertical-align: sub; }

img { max-width: 100%; height: auto; }

::selection {
    background: var(--primary-container);
    color: var(--on-primary-container);
}
"""

private fun definitionCss() = """
.pos, .part-of-speech, .mw-headline {
    color: var(--primary);
    font-weight: 700;
    font-size: 1.1em;
    display: block;
    margin-top: 0.6em;
    margin-bottom: 0.2em;
}

.qualifier-content, .ib-content, .usage-label-sense,
.label-glosses, .form-of-definition-link {
    color: var(--secondary);
    font-style: italic;
    font-size: 0.92em;
}

.e-example, .example, .h-usage-example, .citation-whole {
    display: block;
    color: var(--on-surface-variant);
    font-style: italic;
    margin: 0.25em 0 0.25em 0.8em;
    padding-left: 0.6em;
    border-left: 2px solid var(--outline-variant);
}

.e-translation, .t_line {
    color: var(--on-surface-variant);
    font-size: 0.92em;
}

.gender, abbr {
    color: var(--secondary);
    font-size: 0.85em;
    font-weight: 500;
}

.senseno {
    color: var(--primary);
    font-weight: 700;
}

.mw-editsection, .mw-edit-link, .noprint { display: none !important; }

h2.word-title {
    color: var(--primary);
    font-weight: 500;
    font-size: 1.4em;
    margin-top: 0;
    margin-bottom: 0.2em;
}

hr.definition-divider {
    border: none;
    border-top: 1px solid var(--outline-variant);
    margin: 16px 0;
}
"""

private fun wordClickScript() = """
<script>
document.body.addEventListener('click', function(e) {
    if (e.target.tagName.toLowerCase() === 'a') {
        var word = e.target.textContent.trim();
        if (word.length > 0) { Android.onWordClick(word); }
        e.preventDefault();
        return;
    }

    var range;
    if (document.caretRangeFromPoint) {
        range = document.caretRangeFromPoint(e.clientX, e.clientY);
    }
    if (range && range.startContainer.nodeType === 3) {
        var text = range.startContainer.nodeValue;
        var offset = range.startOffset;
        var start = offset, end = offset;
        while (start > 0 && /[^\s\.,;:'"!?()\[\]{}""'']/.test(text[start - 1])) start--;
        while (end < text.length && /[^\s\.,;:'"!?()\[\]{}""'']/.test(text[end])) end++;
        var word = text.substring(start, end).trim();
        if (word.length > 0) { Android.onWordClick(word); }
    }
});
</script>
"""

internal fun Color.toCssRgba(): String {
    val argb = toArgb()
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    val a = (argb shr 24 and 0xFF) / 255f
    return "rgba($r, $g, $b, $a)"
}
