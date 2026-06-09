package com.example.readerapp.ui.features.reader.components.contents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.ui.theme.spacing
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Edit
import com.example.readerapp.ui.components.EmptyState

@Composable
fun NotesList(
    notes: List<NoteEntity>,
    tableOfContents: List<Link>,
    getPositionLabel: (Locator) -> String,
    onNoteClick: (Locator) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    if (notes.isEmpty()) {
        EmptyState(
            icon = MaterialSymbols.Outlined.Edit,
            text = stringResource(R.string.reader_no_notes),
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(notes) { note ->
                val locator = try { Locator.fromJSON(JSONObject(note.locatorJson)) } catch (_: Exception) { null }
                if (locator != null) {
                    val inDocument = stringResource(R.string.reader_in_document)
                    val chapterTitle = note.chapterTitle
                        ?.takeIf { it.isNotBlank() && it != inDocument }
                        ?: tableOfContents.find { it.href.toString().substringBefore("#") == locator.href.toString().substringBefore("#") }?.title
                        ?: inDocument
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNoteClick(locator) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing.space8)
                    ) {
                        EntryHeader(
                            title = chapterTitle,
                            positionLabel = getPositionLabel(locator),
                            onDelete = { onDeleteNote(note.id) }
                        )
                        
                        // Display the highlighted text if available
                        locator.text.highlight?.takeIf { it.isNotBlank() }?.let { highlight ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(Color(note.color).copy(alpha = 1f))
                                )
                                Text(
                                    text = highlight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                        
                        if (note.noteText.isNotBlank()) {
                            Text(
                                text = note.noteText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
