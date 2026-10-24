package com.javierreansyah.pinecone.ui.features.reader

import com.javierreansyah.pinecone.data.publication.conversion.GeneratedEpubLocations
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import kotlin.math.abs

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

fun List<Locator>.findIndexForLocator(locator: Locator): Int {
    if (isEmpty()) return -1

    val targetTotal = locator.locations.totalProgression
    if (targetTotal != null) {
        var low = 0
        var high = size - 1
        var bestIndex = -1
        while (low <= high) {
            val mid = (low + high) / 2
            val midVal = this[mid].locations.totalProgression ?: -1.0
            if (midVal <= targetTotal) {
                bestIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (bestIndex != -1) return bestIndex
    }

    val targetHref = locator.href.toString().substringBefore("#")
    val targetProgression = locator.locations.progression ?: 0.0

    var bestIndex = -1
    for (i in indices.reversed()) {
        val (posHref2, _, _, posLocations, _) = this[i]
        val posHref = posHref2.toString().substringBefore("#")
        if (posHref == targetHref && (posLocations.progression ?: 0.0) <= targetProgression) {
            bestIndex = i
            break
        }
    }
    if (bestIndex != -1) return bestIndex

    return indexOfFirst { (itHref, _, _, _, _) -> itHref.toString().substringBefore("#") == targetHref }
}

fun List<Locator>.findClosestByProgression(targetProgression: Double): Locator? {
    if (isEmpty()) return null
    val boundedProgression = targetProgression.coerceIn(0.0, 1.0)

    var low = 0
    var high = size - 1
    var closest = this[0]
    var minDiff = Double.MAX_VALUE

    while (low <= high) {
        val mid = (low + high) / 2
        val midLocator = this[mid]
        val midVal = midLocator.locations.totalProgression ?: 0.0

        val diff = abs(midVal - boundedProgression)
        if (diff < minDiff) {
            minDiff = diff
            closest = midLocator
        }

        if (midVal == boundedProgression) {
            return midLocator
        } else if (midVal < boundedProgression) {
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return closest
}

fun Locator.isSamePosition(other: Locator): Boolean {
    if (href != other.href) return false

    val thisFragments = locations.fragments
    val otherFragments = other.locations.fragments
    if (thisFragments.isNotEmpty() && otherFragments.isNotEmpty()) {
        return thisFragments == otherFragments
    }

    val thisProg = locations.progression
    val otherProg = other.locations.progression
    if (thisProg != null && otherProg != null) {
        return abs(thisProg - otherProg) < 0.0001
    }

    val thisTotalProg = locations.totalProgression
    val otherTotalProg = other.locations.totalProgression
    if (thisTotalProg != null && otherTotalProg != null) {
        return abs(thisTotalProg - otherTotalProg) < 0.0001
    }

    val thisPos = locations.position
    val otherPos = other.locations.position
    return thisPos != null && otherPos != null && thisPos == otherPos
}
