package com.javierreansyah.pinecone.data.publication

import org.readium.r2.shared.publication.Publication

enum class ReaderProfile { EPUB, PDF, IMAGE, AUDIO }

object PublicationFormats {
    val audioFileExtensions: Set<String> = setOf(
        "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav",
        "mp4", "aif", "aiff", "webm",
    )
    val audioExtensions: Set<String> = audioFileExtensions + setOf("audiobook", "zab")
    val imageExtensions: Set<String> = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "tif", "tiff", "jxl",
    )
    val extensions: Set<String> = setOf("epub", "pdf", "cbz", "zip", "webpub", "divina") +
            audioExtensions + imageExtensions
    val pickerMimeTypes: Array<String> = arrayOf(
        "application/epub+zip",
        "application/pdf",
        "application/zip",
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/webpub+zip",
        "application/divina+zip",
        "application/audiobook+zip",
        "audio/*",
        "image/*",
        "application/octet-stream",
    )

    fun profile(publication: Publication): ReaderProfile = when {
        publication.conformsTo(Publication.Profile.EPUB) -> ReaderProfile.EPUB
        publication.conformsTo(Publication.Profile.PDF) -> ReaderProfile.PDF
        publication.conformsTo(Publication.Profile.DIVINA) -> ReaderProfile.IMAGE
        publication.conformsTo(Publication.Profile.AUDIOBOOK) -> ReaderProfile.AUDIO
        // Readium's web-capable EPUB navigator can also render self-contained HTML WebPubs.
        // Do not infer an HTML profile from a single resource in a mixed-media reading order.
        publication.readingOrder.isNotEmpty() && publication.readingOrder.all { it.mediaType?.isHtml == true } -> ReaderProfile.EPUB
        else -> error("This publication has no supported Readium navigator")
    }

    fun requireLocalResources(publication: Publication) {
        (publication.readingOrder + publication.resources).forEach { link ->
            val uri = java.net.URI(link.href.toString())
            require(!uri.isAbsolute && uri.rawAuthority == null) { "Remote publication resources are not supported" }
            val path = uri.path.orEmpty()
            require(
                !path.startsWith('/') && path.split('/')
                    .none { it == ".." || '\\' in it }) { "Unsafe publication resource path" }
        }
    }
}
