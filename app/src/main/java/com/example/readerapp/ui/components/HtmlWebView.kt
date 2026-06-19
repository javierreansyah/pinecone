@file:Suppress("SameReturnValue")

package com.example.readerapp.ui.components

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

// ─────────────────────────────────────────────────────────────────────────────
// Config
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Selects the CSS preset injected into the WebView.
 *
 * - [Definition] – rich dictionary styling: part-of-speech markers, qualifier
 *   labels, Wiktionary class hooks, etc.
 * - [Description] – book-blurb styling: normalises messy publisher HTML
 *   (`<BR>`, bare `<b><i>` preambles, `<div>`/`<p>` mix) into a clean,
 *   readable paragraph layout.
 */
enum class HtmlPreset { Definition, Description }

/**
 * Configuration for [HtmlWebView].
 *
 * @param preset        Which CSS preset to apply.
 * @param onWordClick   Optional callback invoked (on the main thread) when the
 *                      user taps a word or link.  Pass `null` to omit the JS
 *                      bridge entirely.
 * @param extraCss      Additional CSS injected after the preset styles.  Useful
 *                      for one-off tweaks without creating a new preset.
 */
data class HtmlWebViewConfig(
    val preset: HtmlPreset = HtmlPreset.Definition,
    val onWordClick: ((String) -> Unit)? = null,
    val extraCss: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders an HTML string inside a transparent, non-scrolling [WebView] that is
 * styled using the current Material 3 color tokens.
 *
 * Scrolling is intentionally disabled so the surrounding lazy/scroll container
 * handles it.  JavaScript is enabled only when [HtmlWebViewConfig.onWordClick]
 * is provided.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    config: HtmlWebViewConfig = HtmlWebViewConfig(),
    baseFontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val fontSizeCss = with(density) { baseFontSize.toPx() / density.density }

    // Capture every color token used by CSS; re-render HTML when any changes.
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

    val fullHtml = remember(
        htmlContent,
        config.preset,
        config.extraCss,
        onSurface, onSurfaceVariant, primary, primaryContainer, onPrimaryContainer,
        surfaceContainerHigh, outline, outlineVariant, secondary, tertiary,
        fontSizeCss
    ) {
        buildHtml(
            body = normaliseHtml(htmlContent, config.preset),
            preset = config.preset,
            fontSizeCss = fontSizeCss,
            extraCss = config.extraCss,
            includeWordClickJs = config.onWordClick != null,
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isSaveEnabled = false
            setBackgroundColor(AndroidColor.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = config.onWordClick != null
                loadWithOverviewMode = false
                useWideViewPort = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }
            if (config.onWordClick != null) {
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onWordClick(word: String) {
                        post { config.onWordClick.invoke(word) }
                    }
                }, "Android")
            }
        }
    }, update = { webView ->
        webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
    }, onRelease = { webView ->
        webView.destroy()
    })
}

// ─────────────────────────────────────────────────────────────────────────────
// HTML normalisation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pre-processes the raw HTML string before injection.
 *
 * For the [HtmlPreset.Description] preset this handles the common mess found
 * in book-metadata descriptions:
 * - Converts bare `<BR>` / `<BR />` line breaks into paragraph breaks so
 *   the CSS paragraph spacing takes effect properly.
 * - Strips leading/trailing whitespace inside block elements.
 * - Collapses multiple consecutive blank paragraphs.
 */
private fun normaliseHtml(html: String, preset: HtmlPreset): String {
    if (preset != HtmlPreset.Description) return html

    var result = html
        // Normalise self-closing and upper-case <BR> variants
        .replace(Regex("<[Bb][Rr]\\s*/?>"), "<br>")
        // Convert sequences of <br> (with optional whitespace between) to paragraph boundaries
        .replace(Regex("(<br>\\s*){2,}"), "</p><p>")
        // Single <br> → space (avoids orphan line breaks inside a paragraph)
        .replace(Regex("\\s*<br>\\s*"), " ")
        // Strip trailing whitespace inside <p> tags
        .replace(Regex("<p>\\s+"), "<p>")
        .replace(Regex("\\s+</p>"), "</p>")
        // Collapse consecutive empty paragraphs produced by the transformation above
        .replace(Regex("(<p>\\s*</p>\\s*)+"), "")
        .trim()

    // If the content has no block-level tags at all, wrap in a <p> so
    // paragraph CSS applies.
    val hasBlock = Regex("<(p|div|h[1-6]|ul|ol|blockquote)[\\s>]", RegexOption.IGNORE_CASE)
        .containsMatchIn(result)
    if (!hasBlock) {
        result = "<p>$result</p>"
    }

    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// HTML document builder
// ─────────────────────────────────────────────────────────────────────────────

private fun buildHtml(
    body: String,
    preset: HtmlPreset,
    fontSizeCss: Float,
    extraCss: String,
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
    tertiary: Color
): String {
    val presetCss = when (preset) {
        HtmlPreset.Definition -> definitionCss()
        HtmlPreset.Description -> descriptionCss()
    }

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

$presetCss

$extraCss
</style>
</head>
<body>
$body
</body>
$wordClickJs
</html>
    """.trimIndent()
}

// ─────────────────────────────────────────────────────────────────────────────
// CSS blocks
// ─────────────────────────────────────────────────────────────────────────────

/** Styles common to every preset. */
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

/** Extra CSS for the [HtmlPreset.Definition] preset (Wiktionary/dict class hooks). */
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

/**
 * Extra CSS for the [HtmlPreset.Description] preset.
 *
 * Optimised for book-blurb HTML: de-emphasises heavy bold/italic preambles,
 * tightens paragraph spacing, and gives the first paragraph (often the
 * marketing hook in bold-italic) a slightly distinct treatment.
 */
private fun descriptionCss() = """
body {
    padding: 0;
}

/* Tighten up paragraph spacing for blurb prose */
p {
    margin-bottom: 0.55em;
    line-height: 1.65;
}

p:last-child { margin-bottom: 0; }

/* Many descriptions start with a bold-italic marketing hook.
   Keep it on-surface but soften to surface-variant so it reads
   as a teaser rather than a heading. */
p:first-child > b > i,
p:first-child > i > b,
p:first-child > b,
p:first-child > strong {
    color: var(--on-surface);
    font-style: italic;
}

/* Inline italic inside body prose → on-surface-variant */
i, em { color: var(--on-surface-variant); }

/* Reset: description content uses <b> for in-line character dialogue /
   chat-transcript style.  Keep it on-surface, not primary. */
b, strong {
    color: var(--on-surface);
    font-weight: 600;
}

/* Strip any div wrapper margins publishers sometimes add */
div {
    margin: 0;
    padding: 0;
}
"""

// ─────────────────────────────────────────────────────────────────────────────
// JavaScript
// ─────────────────────────────────────────────────────────────────────────────

/** JS that calls `Android.onWordClick(word)` when the user taps a word. */
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

// ─────────────────────────────────────────────────────────────────────────────
// Colour helper
// ─────────────────────────────────────────────────────────────────────────────

/** Converts a Compose [Color] to a CSS `rgba()` string. */
internal fun Color.toCssRgba(): String {
    val argb = this.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val a = ((argb shr 24) and 0xFF) / 255f
    return "rgba($r, $g, $b, $a)"
}
