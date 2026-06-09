package com.example.readerapp.ui.features.dictionary.utils

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Color as AndroidColor

/**
 * Renders dictionary definition HTML inside a WebView styled with the current
 * Material 3 theme colors. The WebView uses a transparent background and
 * disables scrolling so the surrounding LazyColumn handles scroll.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DefinitionWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize
) {
    val colorScheme = MaterialTheme.colorScheme

    // Read theme colors at composition time
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

    val density = LocalDensity.current
    val fontSizeCss = with(density) { baseFontSize.toPx() / density.density }

    val fullHtml = remember(
        htmlContent,
        onSurface,
        primary,
        primaryContainer,
        onPrimaryContainer,
        onSurfaceVariant,
        surfaceContainerHigh,
        outline,
        outlineVariant,
        secondary,
        tertiary,
        fontSizeCss
    ) {
        buildDefinitionHtml(
            body = htmlContent,
            fontSizeCss = fontSizeCss,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            primary = primary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            outline = outline,
            outlineVariant = outlineVariant,
            secondary = secondary,
            tertiary = tertiary
        )
    }

    AndroidView(modifier = modifier.fillMaxWidth(), factory = { context ->
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(AndroidColor.TRANSPARENT)
            // Disable scrolling — let the parent handle it
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            settings.apply {
                // No JS needed for static definitions
                javaScriptEnabled = false
                // Allow text to reflow
                loadWithOverviewMode = false
                useWideViewPort = false
                // Disable zoom
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }
        }
    }, update = { webView ->
        webView.loadDataWithBaseURL(
            null, fullHtml, "text/html", "UTF-8", null
        )
    })
}

/**
 * Converts a Compose [Color] to a CSS `rgba()` string.
 */
private fun Color.toCssRgba(): String {
    val argb = this.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val a = ((argb shr 24) and 0xFF) / 255f
    return "rgba($r, $g, $b, $a)"
}

/**
 * Builds a full HTML document with embedded CSS that maps Material 3 color
 * tokens to CSS custom properties for dictionary definition rendering.
 */
private fun buildDefinitionHtml(
    body: String,
    fontSizeCss: Float,
    onSurface: Color,
    onSurfaceVariant: Color,
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    surfaceContainerHigh: Color,
    outline: Color,
    outlineVariant: Color,
    secondary: Color,
    tertiary: Color
): String {
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

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: 'Roboto', 'Noto Sans', system-ui, -apple-system, sans-serif;
    font-size: ${fontSizeCss}px;
    line-height: 1.6;
    color: var(--on-surface);
    background: transparent;
    word-wrap: break-word;
    overflow-wrap: break-word;
    -webkit-text-size-adjust: none;
    padding: 0 24px;
}

/* ── Headings ── */
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

/* First heading should not have top margin */
body > h1:first-child,
body > h2:first-child,
body > h3:first-child,
body > h4:first-child {
    margin-top: 0;
}

/* ── Paragraphs ── */
p {
    margin-bottom: 0.5em;
}

/* ── Lists (ordered & unordered) ── */
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

/* Nested lists */
li > ol, li > ul {
    margin-top: 0.2em;
    margin-bottom: 0.2em;
}

/* ── Definition lists (used by some Wiktionary exports) ── */
dl {
    margin-bottom: 0.5em;
}
dt {
    font-weight: 700;
    color: var(--primary);
    margin-top: 0.5em;
}
dd {
    margin-left: 1.2em;
    margin-bottom: 0.3em;
}

/* ── Links ── */
a {
    color: var(--primary);
    text-decoration: none;
}

/* ── Emphasis & Italics (qualifiers, labels, usage context) ── */
i, em {
    color: var(--on-surface-variant);
    font-style: italic;
}

b, strong {
    font-weight: 700;
    color: var(--on-surface);
}

/* ── Blockquotes (example sentences, citations) ── */
blockquote {
    border-left: 3px solid var(--primary-container);
    padding: 0.3em 0 0.3em 0.8em;
    margin: 0.4em 0;
    color: var(--on-surface-variant);
    font-style: italic;
    background: var(--surface-container-high);
    border-radius: 0 6px 6px 0;
}

/* ── Tables ── */
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
td {
    color: var(--on-surface);
}

/* ── Horizontal rules ── */
hr {
    border: none;
    border-top: 1px solid var(--outline-variant);
    margin: 0.8em 0;
}

/* ── Code / phonetic ── */
code, .IPA {
    font-family: 'Roboto Mono', 'Noto Sans Mono', monospace;
    font-size: 0.9em;
    color: var(--tertiary);
    background: var(--surface-container-high);
    padding: 0.1em 0.35em;
    border-radius: 4px;
}

/* ── Superscript / subscript ── */
sup, sub {
    font-size: 0.75em;
    line-height: 0;
}
sup { vertical-align: super; }
sub { vertical-align: sub; }

/* ── Wiktionary / reader-dict.com specific class styling ── */

/* Part of speech markers */
.pos, .part-of-speech, .mw-headline {
    color: var(--primary);
    font-weight: 700;
    font-size: 1.1em;
    display: block;
    margin-top: 0.6em;
    margin-bottom: 0.2em;
}

/* Qualifier / label spans (e.g. "informal", "archaic") */
.qualifier-content, .ib-content, .usage-label-sense,
.label-glosses, .form-of-definition-link {
    color: var(--secondary);
    font-style: italic;
    font-size: 0.92em;
}

/* Example sentences */
.e-example, .example, .h-usage-example, .citation-whole {
    display: block;
    color: var(--on-surface-variant);
    font-style: italic;
    margin: 0.25em 0 0.25em 0.8em;
    padding-left: 0.6em;
    border-left: 2px solid var(--outline-variant);
}

/* Translation gloss */
.e-translation, .t_line {
    color: var(--on-surface-variant);
    font-size: 0.92em;
}

/* Gender/class markers */
.gender, abbr {
    color: var(--secondary);
    font-size: 0.85em;
    font-weight: 500;
}

/* Sense numbering (Wiktionary) */
.senseno {
    color: var(--primary);
    font-weight: 700;
}

/* Hide edit links and other wiki artifacts */
.mw-editsection, .mw-edit-link, .noprint {
    display: none !important;
}

/* ── Images (some dictionaries embed small icons) ── */
img {
    max-width: 100%;
    height: auto;
}

/* ── Selection styling ── */
::selection {
    background: var(--primary-container);
    color: var(--on-primary-container);
}

/* ── Multi-entry styling ── */
h2.word-title {
    color: var(--primary);
    font-weight: 500;
    font-size: 1.4em;
    margin-top: 0;
    margin-bottom: 0.5em;
}

hr.definition-divider {
    border: none;
    border-top: 1px solid var(--outline-variant);
    margin: 16px 0;
}
</style>
</head>
<body>
$body
</body>
</html>
""".trimIndent()
}
