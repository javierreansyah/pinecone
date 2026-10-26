package com.javierreansyah.pinecone.data.repository.library

import com.javierreansyah.pinecone.data.publication.conversion.GeneratedEpubLocations
import com.javierreansyah.pinecone.data.publication.conversion.SourcePage
import org.json.JSONArray
import org.json.JSONObject

internal object RenditionLocations {
    fun map(source: String, stored: String?, from: String, to: String, pages: List<SourcePage>, progression: Double): String? {
        fun page(json: JSONObject, rendition: String): Int {
            val locations = json.optJSONObject("locations")
            val fragments = locations?.optJSONArray("fragments")?.let { values -> (0 until values.length()).map { values.getString(it) } }.orEmpty()
            val href = json.optString("href")
            if (rendition == "read-mode") return GeneratedEpubLocations.page(href, locations?.optString("cssSelector"), fragments) ?: 1
            val sourcePage = fragments.firstNotNullOfOrNull { it.removePrefix("page=").toIntOrNull() }
                ?: locations?.optInt("position", 1) ?: 1
            return pages.firstOrNull { it.sourceHref == href && it.sourcePage == sourcePage }?.page ?: sourcePage
        }
        val original = JSONObject(source)
        val number = page(original, from)
        if (stored != null && page(JSONObject(stored), to) == number) return stored
        val mapping = pages.firstOrNull { it.page == number } ?: return stored
        val href = if (to == "read-mode") mapping.href.substringBefore('#') else mapping.sourceHref
        val fragment = if (to == "read-mode") "source-page-$number" else "page=${mapping.sourcePage}"
        return JSONObject().put("href", href).put("type", if (to == "read-mode") "application/xhtml+xml" else "application/pdf")
            .put("locations", JSONObject().put("fragments", JSONArray(listOf(fragment))).put("totalProgression", progression))
            .toString()
    }
}
