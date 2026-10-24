package com.javierreansyah.pinecone.data.publication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioMetadataImporterDeviceTest {
    @Test
    fun importsHamletTitleAndAuthorFromOriginalId3Tags() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("hamlet-", ".mp3", instrumentation.targetContext.cacheDir)
        try {
            instrumentation.context.assets.open("audio/hamlet-id3.mp3").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val imported = PublicationMetadataImporter.read(
                "hamlet", file, Metadata(conformsTo = setOf(Publication.Profile.AUDIOBOOK)),
            )
            assertEquals("Hamlet: Act 1, Scene 1", imported.title)
            assertEquals(
                listOf("William Shakespeare"),
                imported.credits.filter { it.role == "author" }.map { it.name })
            assertEquals("hamlet", imported.credits.single { it.role == "author" }.bookId)
        } finally {
            file.delete()
        }
    }
}
