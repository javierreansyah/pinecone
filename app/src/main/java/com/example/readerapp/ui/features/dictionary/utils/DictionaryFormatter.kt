package com.example.readerapp.ui.features.dictionary.utils

import com.example.readerapp.data.local.database.dictionary.DictionaryEntry

/**
 * Utility for preparing dictionary definition content for WebView rendering.
 *
 * Detects whether the raw definition string is HTML or plain text and
 * normalises it into a clean HTML fragment suitable for [DefinitionWebView].
 */
object DictionaryFormatter {

    private val HTML_TAG_REGEX = Regex(
        "<(?:p|div|span|ol|ul|li|table|br|h[1-6]|a |dl|dt|dd|blockquote|em|strong)[ >/]",
        RegexOption.IGNORE_CASE
    )

    private val PARTS_OF_SPEECH = setOf(
        "Noun",
        "Proper Noun",
        "Verb",
        "Adjective",
        "Adverb",
        "Pronoun",
        "Preposition",
        "Conjunction",
        "Interjection",
        "Idiom",
        "Phrase",
        "Synonyms",
        "Antonyms",
        "Derived terms",
        "Related terms",
        "Translations",
        "Usage notes",
        "Article",
        "Particle",
        "Numeral",
        "Symbol",
        "Prefix",
        "Suffix",
        "Infix",
        "Circumfix",
        "Etymology",
        "Pronunciation",
        "Alternative forms",
        "Determiner",
        "Contraction"
    )

    /**
     * Prepares a single HTML string containing multiple dictionary entries,
     * including their word titles and dividers.
     */
    fun prepareHtmlForMultipleEntries(entries: List<DictionaryEntry>): String {
        val sb = StringBuilder()
        entries.forEachIndexed { index, entry ->
            sb.append("<h2 class=\"word-title\">").append(escapeHtml(entry.word)).append("</h2>\n")
            sb.append("<div class=\"definition-content\">\n")
            sb.append(prepareHtml(entry.definition))
            sb.append("\n</div>\n")
            if (index < entries.size - 1) {
                sb.append("<hr class=\"definition-divider\">\n")
            }
        }
        return sb.toString()
    }

    /**
     * Returns a clean HTML fragment ready for [DefinitionWebView].
     *
     * - If the input already contains HTML tags it is returned with minimal
     *   cleanup (strips outer `<html>`, `<head>`, `<body>` wrappers).
     * - If the input is plain text it is converted into lightweight HTML
     *   with part-of-speech headings and numbered list formatting.
     */
    fun prepareHtml(definition: String): String {
        val trimmed = definition.trim()
        if (trimmed.isEmpty()) return ""

        return if (isHtml(trimmed)) {
            cleanHtml(trimmed)
        } else {
            plainTextToHtml(trimmed)
        }
    }

    /** Simple heuristic: does the content contain recognisable HTML tags? */
    private fun isHtml(content: String): Boolean {
        return HTML_TAG_REGEX.containsMatchIn(content)
    }

    /**
     * Strips outer document wrappers (`<html>`, `<head>`, `<body>`) so we
     * get just the inner content for embedding in our own document shell.
     */
    private fun cleanHtml(html: String): String {
        var cleaned = html
        // Remove doctype
        cleaned = cleaned.replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")
        // Remove <html>, <head>, <body> and their closing tags (but keep content)
        cleaned = cleaned.replace(Regex("</?html[^>]*>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("<head[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("</?body[^>]*>", RegexOption.IGNORE_CASE), "")
        // Remove inline <style> blocks (we provide our own)
        cleaned = cleaned.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        return cleaned.trim()
    }

    /**
     * Converts a plain-text dictionary definition into lightweight HTML.
     *
     * Recognises:
     * - Part-of-speech headings (exact line matches from [PARTS_OF_SPEECH])
     * - Numbered definitions (`1. …`, `2. …`)
     * - Parenthesised qualifiers like `(archaic)`, `(informal)`
     * - Blank-line separation into paragraphs
     */
    private fun plainTextToHtml(text: String): String {
        val lines = text.split("\n")
        val sb = StringBuilder()
        var inList = false

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                if (inList) {
                    sb.append("</ol>")
                    inList = false
                }
                continue
            }

            // Check for part of speech heading
            val bareLabel = trimmedLine.trimEnd(':', ' ')
            if (PARTS_OF_SPEECH.contains(bareLabel)) {
                if (inList) {
                    sb.append("</ol>")
                    inList = false
                }
                sb.append("<h3>").append(escapeHtml(bareLabel)).append("</h3>")
                continue
            }

            // Check for numbered definition (e.g. "1. definition text")
            val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)").find(trimmedLine)
            if (numberedMatch != null) {
                if (!inList) {
                    sb.append("<ol>")
                    inList = true
                }
                val defText = numberedMatch.groupValues[2]
                sb.append("<li>").append(formatInlineElements(escapeHtml(defText))).append("</li>")
                continue
            }

            // Regular text line
            sb.append("<p>").append(formatInlineElements(escapeHtml(trimmedLine))).append("</p>")
        }

        if (inList) {
            sb.append("</ol>")
        }

        return sb.toString()
    }

    /**
     * Applies inline formatting to already-escaped HTML text:
     * - Parenthesised qualifiers → `<i class="qualifier-content">…</i>`
     */
    private fun formatInlineElements(escapedText: String): String {
        // Style parenthesised qualifiers
        return escapedText.replace(Regex("\\(([^)]+)\\)")) { match ->
            "<i class=\"qualifier-content\">${match.value}</i>"
        }
    }

    /** Minimal HTML entity escaping for plain text content. */
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
