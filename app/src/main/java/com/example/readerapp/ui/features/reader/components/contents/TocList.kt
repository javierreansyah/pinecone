package com.example.readerapp.ui.features.reader.components.contents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Format_list_bulleted
import com.example.readerapp.ui.components.EmptyState

@Composable
fun TocList(
    tableOfContents: List<Link>,
    currentLocator: Locator?,
    getChapterPageLabel: (Link) -> String,
    onChapterClick: (Link) -> Unit
) {
    if (tableOfContents.isEmpty()) {
        EmptyState(
            icon = MaterialSymbols.Outlined.Format_list_bulleted,
            text = stringResource(R.string.reader_no_toc),
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        )
    } else {
        val currentChapterIndex = remember(tableOfContents, currentLocator) {
            if (currentLocator == null) return@remember 0
            val currentHref = currentLocator.href.toString().substringBefore("#")
            val index = tableOfContents.indexOfFirst { it.href.toString().substringBefore("#") == currentHref }
            if (index >= 0) index else 0
        }
        
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = currentChapterIndex
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(tableOfContents.size) { index ->
                val link = tableOfContents[index]
                val currentHref = currentLocator?.href?.toString()?.substringBefore("#")
                val linkHref = link.href.toString().substringBefore("#")
                val isCurrentChapter = currentHref == linkHref
                val pageLabel = getChapterPageLabel(link)
                ListItem(
                    headlineContent = {
                        Text(
                            text = link.title ?: link.href.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    },
                    supportingContent = if (pageLabel.isNotBlank()) {
                        {
                            Text(
                                text = pageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else null,
                    modifier = Modifier.clickable { onChapterClick(link) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
