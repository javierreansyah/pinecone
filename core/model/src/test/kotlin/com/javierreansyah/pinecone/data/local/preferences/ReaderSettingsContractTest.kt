package com.javierreansyah.pinecone.data.local.preferences

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderSettingsContractTest {
    @Test
    fun legacyPdfSettingsDecodeWithoutAnyNavigatorDependency() {
        val json =
            """{"fontSize":1.5,"formatPreferences":{"pdf":{"scroll":false,"invert":true,"pageSpacing":24.0},"audioSpeed":1.25}}"""
        val decoded = Json.decodeFromString<ReaderSettings>(json)
        assertEquals(1.5, decoded.fontSize, 0.0)
        assertEquals(
            PdfPreferences(scroll = false, invert = true, pageSpacing = 24.0),
            decoded.formatPreferences.pdf
        )
        assertEquals(decoded, Json.decodeFromString<ReaderSettings>(Json.encodeToString(decoded)))
    }

    @Test
    fun enabledDefaultsRemainDifferentFromTheDisabledMarker() {
        val enabled = ReaderSettings().encodeReadingOverrides()
        assertNotEquals("{}", enabled)
        assertEquals(ReaderSettings(), Json.decodeFromString<ReaderSettings>(enabled))
        assertEquals(Json.decodeFromString<PdfPreferences>("{}"), PdfPreferences())
    }
}
