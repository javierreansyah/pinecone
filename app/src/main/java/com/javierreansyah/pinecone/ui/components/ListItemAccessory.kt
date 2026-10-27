package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer

/** Centers accessories against the title and first supporting line, even when supporting text wraps. */
@Composable
internal fun ListItemAccessory(content: @Composable () -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    val titleHeight =
        textMeasurer.measure(" ", style = MaterialTheme.typography.bodyLarge).size.height
    val summaryHeight =
        textMeasurer.measure(" ", style = MaterialTheme.typography.bodyMedium).size.height
    val height = with(LocalDensity.current) { (titleHeight + summaryHeight).toDp() }
    Box(
        modifier = Modifier
            .height(height)
            .wrapContentHeight(unbounded = true),
        contentAlignment = Alignment.Center,
    ) { content() }
}
