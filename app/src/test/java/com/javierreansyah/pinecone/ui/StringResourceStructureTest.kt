package com.javierreansyah.pinecone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourceStructureTest {
    private data class Resource(val name: String, val type: String, val file: String)

    @Test
    fun `localized resources have unique names and matching types`() {
        val defaults = resources("values")
        val translated = resources("values-in")
        for (entries in listOf(defaults, translated)) {
            val duplicates = entries.groupBy { it.name }.filterValues { it.size > 1 }
            assertTrue("Duplicate resources: $duplicates", duplicates.isEmpty())
        }
        assertEquals(
            defaults.associate { it.name to it.type },
            translated.associate { it.name to it.type },
        )
    }

    @Test
    fun `resource names and files follow feature conventions`() {
        for (locale in listOf("values", "values-in")) {
            for ((name, _, file) in resources(locale)) {
                assertTrue(
                    name,
                    name.matches(Regex("[a-z][a-z0-9]*(_[a-z0-9]+)+"))
                )
                val prefix = name.substringBefore('_')
                val expectedFile = if (prefix in setOf("action", "common")) {
                    "strings.xml"
                } else "${prefix}_strings.xml"
                assertEquals(name, expectedFile, file)
            }
        }
    }

    private fun resources(locale: String): List<Resource> {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        return File(res, locale).listFiles().orEmpty().filter { it.extension == "xml" }
            .flatMap { file ->
                val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(file).documentElement
                (0..<root.childNodes.length).mapNotNull { index ->
                    val element = root.childNodes.item(index) as? Element ?: return@mapNotNull null
                    val type =
                        if (element.tagName == "item") element.getAttribute("type") else element.tagName
                    if (type !in setOf("string", "plurals")) return@mapNotNull null
                    Resource(element.getAttribute("name"), type, file.name)
                }
            }
    }
}
