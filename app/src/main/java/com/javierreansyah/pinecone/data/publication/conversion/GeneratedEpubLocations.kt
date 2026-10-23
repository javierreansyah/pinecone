package com.javierreansyah.pinecone.data.publication.conversion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Stable source-page identities shared by navigation, saved locators and PDF mode switching. */
object GeneratedEpubLocations {
    const val CONTENT_HREF: String = "text/content.xhtml"
    private val legacyPage = Regex("(?:^|/)page-(\\d+)\\.xhtml(?:#.*)?$")

    // Reparsed blocks can have suffixes such as "-new", and SVG descendants use
    // IDs such as page-83-visual-1-clip_1. All retain the same source-page prefix.
    private val anchorPage = Regex("(?:source-page-|page-)(\\d+)(?=$|[^0-9])")
    fun pageHref(page: Int): String = "$CONTENT_HREF#source-page-$page"
    fun blockAnchor(page: Int, block: Int): String = "page-$page-block-$block"
    private fun contentResource(href: String): String? = Regex("(?:^|/)(text/content(?:-\\d+)?\\.xhtml)$")
        .find(href.substringBefore('#'))?.groupValues?.get(1)

    fun isContent(href: String): Boolean = contentResource(href) != null
    fun sameContentResource(left: String, right: String): Boolean =
        contentResource(left)?.let { it == contentResource(right) } == true

    fun page(
        href: String,
        cssSelector: String? = null,
        fragments: List<String> = emptyList()
    ): Int? {
        if (!isContent(href) && !legacyPage.containsMatchIn(href)) return null
        return sequenceOf(cssSelector, *fragments.toTypedArray(), href.substringAfter('#', ""))
            .filterNotNull()
            .firstNotNullOfOrNull { anchorPage.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: legacyPage.find(href)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Exact heading anchors distinguish chapters sharing a PDF page or HTML file. */
    fun chapterIndex(
        chapters: List<String>,
        href: String,
        cssSelector: String? = null,
        fragments: List<String> = emptyList()
    ): Int? {
        val currentPage = page(href, cssSelector, fragments) ?: return null
        fun block(value: String?): Int? = value?.let {
            Regex("page-\\d+-(?:block|source|native)-(\\d+)").find(it)?.groupValues?.get(1)
                ?.toIntOrNull()
        }

        val currentBlock =
            sequenceOf(
                cssSelector,
                *fragments.toTypedArray(),
                href.substringAfter('#', "")
            ).firstNotNullOfOrNull(::block)
        return chapters.indexOfLast { target ->
            val targetPage = page(target) ?: return@indexOfLast false
            targetPage < currentPage || (targetPage == currentPage && (currentBlock == null || (block(
                target
            ) ?: -1) <= currentBlock))
        }.coerceAtLeast(0)
    }

    /** Rebase legacy per-page locators; preserve text quotes, range offsets and custom metadata. */
    fun migrate(json: String?): String? {
        if (json == null) return null
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return json
        val href = (root["href"] as? JsonPrimitive)?.contentOrNull ?: return json
        val page = legacyPage.find(href)?.groupValues?.get(1)?.toIntOrNull() ?: return json
        val locations = root["locations"] as? JsonObject ?: JsonObject(emptyMap())
        fun selector(value: String): String = value
            .replace(Regex("#block-(\\d+)")) { "#${blockAnchor(page, it.groupValues[1].toInt())}" }
            .replace(Regex("^html\\s*>\\s*"), "")
            .replace(Regex("\\bbody\\b"), "#page-$page")

        fun rebase(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement =
            when (element) {
                is JsonObject -> JsonObject(element.mapValues { (key, value) ->
                    if (key == "cssSelector" && value is JsonPrimitive) JsonPrimitive(selector(value.content))
                    else rebase(value)
                })

                is JsonArray -> JsonArray(element.map(::rebase))
                else -> element
            }

        val rebased = (rebase(locations) as JsonObject).toMutableMap()
        rebased.remove("partialCfi") // CFIs and position numbers address the old resource tree.
        rebased.remove("position")
        rebased.remove("progression")
        locations["totalProgression"]?.let { rebased["progression"] = it }
        val fragments = ((locations["fragments"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } + listOfNotNull(href.substringAfter('#', "").takeIf { it.isNotBlank() })).distinct()
            .map { fragment ->
                if (fragment.startsWith("block-")) "page-$page-$fragment" else fragment
            }.filterNot { it.startsWith("epubcfi(") }
        rebased["fragments"] =
            JsonArray((fragments.ifEmpty { listOf("source-page-$page") }).map(::JsonPrimitive))
        if (rebased["cssSelector"] == null) rebased["cssSelector"] =
            JsonPrimitive("#${fragments.firstOrNull() ?: "source-page-$page"}")
        return JsonObject(
            root + mapOf(
                "href" to JsonPrimitive(CONTENT_HREF),
                "locations" to JsonObject(rebased)
            )
        ).toString()
    }

    /** Move DOM range endpoints to their corresponding styled text node after reconstruction. */
    fun remap(json: String?, previous: List<SourcePage>, updated: List<SourcePage>): String? {
        val normalized = migrate(json) ?: return null
        val root = Json.parseToJsonElement(normalized) as? JsonObject ?: return normalized
        val href = (root["href"] as? JsonPrimitive)?.contentOrNull ?: return normalized
        if (!isContent(href)) return normalized
        val oldBlocks = previous.flatMap { it.blocks }.associateBy { it.anchor }
        val newBlocks = updated.flatMap { it.blocks }.associateBy { it.anchor }
        val oldRuns = previous.flatMap { it.blocks }
            .flatMap { block -> block.textRuns.map { it.anchor to (block to it) } }.toMap()
        var cannotSelectText = false
        fun endpoint(node: JsonObject, end: Boolean): JsonObject {
            val selector = (node["cssSelector"] as? JsonPrimitive)?.contentOrNull ?: return node
            val anchor = Regex("^#([A-Za-z0-9_-]+)$").matchEntire(selector)?.groupValues?.get(1)
                ?: return node
            val oldRun = oldRuns[anchor]
            val old = oldRun?.first ?: oldBlocks[anchor]
            val sourceAnchor = old?.anchor ?: anchor
            val fresh = newBlocks[sourceAnchor] ?: old?.let { missing ->
                val page = previous.firstOrNull { missing in it.blocks }?.page
                updated.firstOrNull { it.page == page }?.blocks?.minByOrNull {
                    kotlin.math.abs(it.x - missing.x) + kotlin.math.abs(it.y - missing.y)
                }
            } ?: return node
            val map = node.toMutableMap()
            if (fresh.anchor != sourceAnchor) {
                cannotSelectText = true
                map["cssSelector"] = JsonPrimitive("#${fresh.anchor}")
                return JsonObject(map)
            }
            val offset = (node["charOffset"] as? JsonPrimitive)?.content?.toIntOrNull()
            if (offset == null && oldRun == null) return node // Block IDs remain valid navigation targets.
            val absolute = (oldRun?.second?.start ?: 0) + (offset ?: 0)
            val target = fresh.textRuns.firstOrNull {
                if (end) absolute > it.start && absolute <= it.end else absolute >= it.start && absolute < it.end
            } ?: fresh.textRuns.lastOrNull() ?: return node
            map["cssSelector"] = JsonPrimitive("#${target.anchor}")
            if (offset != null) {
                if (target.image) cannotSelectText = true
                map["charOffset"] =
                    JsonPrimitive((absolute - target.start).coerceIn(0, target.end - target.start))
                map["textNodeIndex"] = JsonPrimitive(0)
            }
            return JsonObject(map)
        }

        fun rewrite(
            value: kotlinx.serialization.json.JsonElement,
            end: Boolean = false
        ): kotlinx.serialization.json.JsonElement = when (value) {
            is JsonObject -> endpoint(JsonObject(value.mapValues { (key, child) ->
                rewrite(
                    child,
                    key == "end"
                )
            }), end)

            is JsonArray -> JsonArray(value.map { rewrite(it, end) })
            else -> value
        }

        val originalLocations = root["locations"] as? JsonObject ?: JsonObject(emptyMap())
        val rangeStart =
            ((originalLocations["domRange"] as? JsonObject)?.get("start") as? JsonObject)?.get("cssSelector")
        val hrefAnchor = href.substringAfter('#', "").takeIf { Regex("[A-Za-z0-9_-]+").matches(it) }
        val locations =
            if (originalLocations["cssSelector"] == null && (rangeStart != null || hrefAnchor != null))
                JsonObject(
                    originalLocations + ("cssSelector" to (rangeStart
                        ?: JsonPrimitive("#$hrefAnchor")))
                )
            else originalLocations
        val rebuilt = (rewrite(locations) as JsonObject).toMutableMap()
        rebuilt.remove("partialCfi")
        // A source PDF page can span several HTML resources. Resolve the block/run,
        // rather than assuming every anchor on the page lives in its first file.
        val newRunBlocks = updated.flatMap { page ->
            page.blocks.flatMap { block ->
                block.textRuns.map { it.anchor to block }
            }
        }.toMap()

        fun resource(selector: String?): String? {
            val anchor =
                selector?.let { Regex("^#([A-Za-z0-9_-]+)$").matchEntire(it)?.groupValues?.get(1) }
                    ?: return null
            val block = newBlocks[anchor] ?: newRunBlocks[anchor]
            return block?.href?.takeIf(String::isNotBlank)?.substringBefore('#')
                ?: updated.firstOrNull { "source-page-${it.page}" == anchor || "page-${it.page}" == anchor }
                    ?.href?.substringBefore('#')
        }

        val selector = (rebuilt["cssSelector"] as? JsonPrimitive)?.contentOrNull
        val range = rebuilt["domRange"] as? JsonObject
        fun rangeResource(key: String) =
            ((range?.get(key) as? JsonObject)?.get("cssSelector") as? JsonPrimitive)
                ?.contentOrNull?.let(::resource)

        val startResource = rangeResource("start")
        val endResource = rangeResource("end")
        if (startResource != null && endResource != null && startResource != endResource) cannotSelectText =
            true
        val sourcePage = page(
            href, selector, ((rebuilt["fragments"] as? JsonArray).orEmpty())
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
        val destination = resource(selector) ?: startResource
        ?: updated.firstOrNull { it.page == sourcePage }?.href?.substringBefore('#')
        if (destination != null && destination != href.substringBefore('#')) {
            rebuilt.remove("position"); rebuilt.remove("progression")
            val anchor = selector?.takeIf { Regex("^#[A-Za-z0-9_-]+$").matches(it) }?.drop(1)
                ?: sourcePage?.let { "source-page-$it" }
            if (anchor != null) rebuilt["fragments"] = JsonArray(listOf(JsonPrimitive(anchor)))
        }
        if (cannotSelectText) rebuilt.remove("domRange") // Image equations retain their note and navigation anchor.
        return JsonObject(
            root + mapOf(
                "href" to JsonPrimitive(destination ?: href),
                "locations" to JsonObject(rebuilt)
            )
        ).toString()
    }
}
