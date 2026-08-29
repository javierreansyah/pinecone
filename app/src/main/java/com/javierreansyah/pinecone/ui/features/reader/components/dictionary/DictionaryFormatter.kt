package com.javierreansyah.pinecone.ui.features.reader.components.dictionary

import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryEntry

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

    fun prepareHtml(definition: String): String {
        val trimmed = definition.trim()
        if (trimmed.isEmpty()) return ""

        return if (isHtml(trimmed)) {
            cleanHtml(trimmed)
        } else {
            plainTextToHtml(trimmed)
        }
    }

    private fun isHtml(content: String): Boolean {
        return HTML_TAG_REGEX.containsMatchIn(content)
    }

    private fun cleanHtml(html: String): String {
        var cleaned = html

        cleaned = cleaned.replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")

        cleaned = cleaned.replace(Regex("</?html[^>]*>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("<head[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("</?body[^>]*>", RegexOption.IGNORE_CASE), "")

        cleaned = cleaned.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        return cleaned.trim()
    }

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

            val bareLabel = trimmedLine.trimEnd(':', ' ')
            if (PARTS_OF_SPEECH.contains(bareLabel)) {
                if (inList) {
                    sb.append("</ol>")
                    inList = false
                }
                sb.append("<h3>").append(escapeHtml(bareLabel)).append("</h3>")
                continue
            }

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

            sb.append("<p>").append(formatInlineElements(escapeHtml(trimmedLine))).append("</p>")
        }

        if (inList) {
            sb.append("</ol>")
        }

        return sb.toString()
    }

    private fun formatInlineElements(escapedText: String): String {

        return escapedText.replace(Regex("\\(([^)]+)\\)")) { match ->
            "<i class=\"qualifier-content\">${match.value}</i>"
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
