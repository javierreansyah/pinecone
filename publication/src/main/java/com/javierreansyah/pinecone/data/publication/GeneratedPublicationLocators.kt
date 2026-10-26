package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.publication.conversion.GeneratedEpubLocations
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** Source maps use package-relative paths; Readium locators use container-relative paths. */
fun Publication.resolveGeneratedLocator(locator: Locator): Locator {
    val link = readingOrder.firstOrNull {
        GeneratedEpubLocations.sameContentResource(it.href.toString(), locator.href.toString())
    } ?: return locator
    val resource = locatorFromLink(link)?.href ?: return locator
    val fragment = locator.href.toString().substringAfter('#', "").takeIf { it.isNotBlank() }
    return locator.copy(
        href = resource, locations = locator.locations.copy(
            fragments = (locator.locations.fragments + listOfNotNull(fragment)).distinct(),
        )
    )
}
