package com.javierreansyah.pinecone.ui.features.reader.components.dictionary

import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryEntry
import com.javierreansyah.pinecone.ui.components.ImportedHtml

object DictionaryFormatter {

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
        "Contraction",
    )

    fun prepareHtmlForMultipleEntries(entries: List<DictionaryEntry>): String {
        val sb = StringBuilder()
        entries.forEachIndexed { index, (_, _, entryWord, entryDefinition) ->
            sb.append("<h2 class=\"word-title\">").append(escapeHtml(entryWord)).append("</h2>\n")
            sb.append("<div class=\"definition-content\">\n")
            sb.append(prepareHtml(entryDefinition))
            sb.append("\n</div>\n")
            if (index < entries.size - 1) {
                sb.append("<hr class=\"definition-divider\">\n")
            }
        }
        return sb.toString()
    }

    private fun prepareHtml(definition: String): String {
        val trimmed = definition.trim()
        if (trimmed.isEmpty()) return ""

        return if (isHtml(trimmed)) {
            cleanHtml(trimmed)
        } else {
            plainTextToHtml(trimmed)
        }
    }

    private fun isHtml(content: String): Boolean = ImportedHtml.containsMarkup(content)

    private fun cleanHtml(html: String): String = ImportedHtml.clean(html)

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
            if (bareLabel in PARTS_OF_SPEECH) {
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
