package com.javierreansyah.pinecone.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UiStringStyleTest {
    private data class Entry(val file: File, val name: String, val text: String)

    @Test
    fun `UI resources use sentence case while preserving proper names and acronyms`() {
        val properNames =
            setOf("Pinecone", "Readium", "Stardict", "Anda", "All", "Unsorted", "Semua", "Belum")
        val failures = entries().flatMap { entry ->
            if (entry.name == "about_copyright") return@flatMap emptyList()
            entry.text.replace("\\n", "\n").split(Regex("[.!?:]\\s+|\\n+")).flatMap { sentence ->
                Regex("\\b[A-Za-z]+\\b").findAll(sentence).mapIndexedNotNull { index, match ->
                    val word = match.value
                    if (index > 0 && word.matches(Regex("[A-Z][a-z]+")) && word !in properNames) {
                        "${requireNotNull(entry.file.parentFile).name}/${entry.name}: $word in ${entry.text}"
                    } else null
                }.toList()
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `book wording is isolated to resources selected by the format aware vocabulary`() {
        val failures = entries().filter { entry ->
            requireNotNull(entry.file.parentFile).name == "values" &&
                    Regex("\\bbooks?\\b", RegexOption.IGNORE_CASE).containsMatchIn(entry.text) &&
                    entry.file.name != "content_strings.xml" && entry.name != "library_books_count"
        }
        assertTrue(failures.joinToString("\n") { "${it.name}: ${it.text}" }, failures.isEmpty())
    }

    @Test
    fun `all translatable UI resources have Indonesian translations including aliases`() {
        val resources = entries(includeAliases = true)
        val defaults = resources.filter { requireNotNull(it.file.parentFile).name == "values" }
            .map { it.name }.toSet()
        val translated = resources.filter { requireNotNull(it.file.parentFile).name == "values-in" }
            .map { it.name }.toSet()
        val missing = defaults - translated
        assertTrue("Missing Indonesian translations: ${missing.sorted().joinToString()}", missing.isEmpty())
    }

    private fun entries(includeAliases: Boolean = false): List<Entry> {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        return listOf("values", "values-in").flatMap { locale ->
            File(res, locale).listFiles().orEmpty().filter { it.extension == "xml" }
                .flatMap { file ->
                    val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(file).documentElement
                    (0 until root.childNodes.length).flatMap nodeLoop@{ index ->
                        val node =
                            root.childNodes.item(index) as? Element ?: return@nodeLoop emptyList()
                        val name = node.getAttribute("name")
                        if (node.getAttribute("translatable") == "false") return@nodeLoop emptyList()
                        when (node.tagName) {
                            "string" -> listOf(Entry(file, name, node.textContent))
                            "item" -> if (node.getAttribute("type") == "string") {
                                listOf(Entry(file, name, node.textContent))
                            } else emptyList()
                            "plurals" -> (0 until node.childNodes.length).mapNotNull { child ->
                                (node.childNodes.item(child) as? Element)?.let {
                                    Entry(
                                        file,
                                        name,
                                        it.textContent
                                    )
                                }
                            }

                            else -> emptyList()
                        }.filterNot { !includeAliases && it.text.startsWith('@') }
                    }
                }
        }
    }
}
