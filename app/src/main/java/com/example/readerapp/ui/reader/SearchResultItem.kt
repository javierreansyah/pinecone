package com.example.readerapp.ui.reader

import org.readium.r2.shared.publication.Locator

/**
 * A single search result entry, pre-processed for display.
 */
data class SearchResultItem(
    /** The Readium locator pointing to this match — used for navigation. */
    val locator: Locator,
    /** Title of the chapter/section this match belongs to (from locator.title). */
    val chapterTitle: String?,
    /** Human-readable position label, e.g. "Position 42" or "34%". */
    val positionLabel: String,
    /** Text immediately before the matched highlight (may be null/empty). */
    val textBefore: String?,
    /** The exact matched text (may be null if the service doesn't provide it). */
    val highlight: String?,
    /** Text immediately after the matched highlight (may be null/empty). */
    val textAfter: String?
)
