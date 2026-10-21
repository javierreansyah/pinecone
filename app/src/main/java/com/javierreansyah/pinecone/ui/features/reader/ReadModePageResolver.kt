package com.javierreansyah.pinecone.ui.features.reader

import org.json.JSONObject

/** Resolve anonymous table/SVG descendants through their source-page DOM ancestors. */
internal object ReadModePageResolver {
    fun script(selector: String?): String = """
        (() => {
            function sourcePage(element) {
                for (let node = element; node; node = node.parentElement) {
                    const match = /^(?:source-page-|page-)(\d+)(?:$|[^0-9])/.exec(node.id || '');
                    if (match) return Number(match[1]);
                    const page = Number(node.getAttribute('data-source-page'));
                    if (page > 0) return page;
                }
                return null;
            }
            let element = null;
            try { element = document.querySelector(${selector?.let(JSONObject::quote) ?: "null"}); } catch (_) {}
            let page = sourcePage(element);
            if (!page) {
                const sections = document.querySelectorAll('section[data-source-page]');
                for (const section of sections) {
                    const visible = Array.from(section.getClientRects()).some(rect =>
                        rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.left < innerWidth &&
                        rect.bottom > 0 && rect.top < innerHeight);
                    if (visible) { page = sourcePage(section); break; }
                }
            }
            return {page: page, href: location.pathname};
        })();
    """.trimIndent()
}
