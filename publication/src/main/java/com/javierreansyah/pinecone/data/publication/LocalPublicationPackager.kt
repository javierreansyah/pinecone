package com.javierreansyah.pinecone.data.publication

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Packages a SAF directory containing manifest.json without retaining access to its provider. */
object LocalPublicationPackager {
    suspend fun packageFolder(context: Context, directory: DocumentFile, destination: File) {
        val manifest =
            requireNotNull(directory.findFile("manifest.json")) { "The publication folder needs manifest.json" }
        val source = context.contentResolver.openInputStream(manifest.uri)?.use {
            val bytes = it.readNBytes(2 * 1024 * 1024 + 1)
            require(bytes.size <= 2 * 1024 * 1024) { "Manifest is too large" }
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as JsonObject
        } ?: error("Cannot read manifest")
        require(source["metadata"] is JsonObject && source["readingOrder"] is JsonArray) { "Not a Readium publication manifest" }
        val hrefs = mutableListOf<String>()
        fun links(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    (element["href"] as? JsonPrimitive)?.content?.let { hrefs += it }
                    element.values.forEach(::links)
                }

                is JsonArray -> element.forEach(::links)
                else -> Unit
            }
        }
        links(source["readingOrder"] ?: return)
        source["resources"]?.let(::links)
        hrefs.forEach { href ->
            val uri = URI(href)
            require(!uri.isAbsolute && uri.rawAuthority == null) { "Remote resources are not supported by local import" }
            val path = uri.path.orEmpty()
            require(
                path.isNotBlank() && !path.startsWith('/') && path.split('/')
                    .none { it == ".." || '\\' in it }) { "Unsafe manifest resource" }
            var resource: DocumentFile? = directory
            path.split('/').filter(String::isNotBlank)
                .forEach { component -> resource = resource?.findFile(component) }
            require(resource?.isFile == true) { "Missing publication resource: $path" }
        }
        ZipOutputStream(destination.outputStream().buffered()).use { zip ->
            var count = 0
            suspend fun copy(folder: DocumentFile, prefix: String, depth: Int) {
                require(depth <= 32) { "Publication folders are nested too deeply" }
                for (child in folder.listFiles().sortedBy { it.name }) {
                    currentCoroutineContext().ensureActive()
                    val name = requireNotNull(child.name)
                    require(name != "." && name != ".." && '/' !in name && '\\' !in name)
                    val path = prefix + name
                    if (child.isDirectory) copy(child, "$path/", depth + 1)
                    else if (child.isFile) {
                        require(++count <= 100_000) { "Too many publication resources" }
                        zip.putNextEntry(ZipEntry(path))
                        context.contentResolver.openInputStream(child.uri)?.use { it.copyTo(zip) }
                            ?: error("Cannot read $path")
                        zip.closeEntry()
                    }
                }
            }
            copy(directory, "", 0)
        }
    }
}
