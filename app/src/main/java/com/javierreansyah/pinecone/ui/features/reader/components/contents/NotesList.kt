package com.javierreansyah.pinecone.ui.features.reader.components.contents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.InsetListItemDefaults
import com.javierreansyah.pinecone.ui.features.reader.toLocatorOrNull
import org.readium.r2.shared.publication.Locator

@Composable
fun NotesList(
    notes: List<NoteEntity>,
    getPositionLabel: (Locator) -> String,
    onNoteClick: (Locator) -> Unit,
    onDeleteNote: (Long) -> Unit,
) {
    val context = LocalContext.current
    if (notes.isEmpty()) {
        EmptyState(
            icon = MaterialSymbols.Outlined.Edit,
            text = stringResource(R.string.reader_no_notes),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
    } else {
        val itemEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val itemPlacementSpec = MaterialTheme.motionScheme.fastEffectsSpec<IntOffset>()
        LazyColumn(
            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(notes, key = { it.id }) { note ->
                val locator = note.locatorJson.toLocatorOrNull()
                if (locator != null) {
                    val inDocument = stringResource(R.string.reader_in_document)
                    val chapterTitle =
                        note.chapterTitle?.takeIf { it.isNotBlank() && it != inDocument }
                            ?: inDocument

                    val positionLabel = getPositionLabel(locator)
                    val formattedDate = remember(note.createdAt, context) {
                        annotationTimestamp(context, note.createdAt)
                    }
                    val subtitle = if (positionLabel.isNotBlank()) {
                        "$positionLabel | $formattedDate"
                    } else {
                        formattedDate
                    }

                    ListItem(
                        onClick = { onNoteClick(locator) },
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = itemEffectsSpec,
                                placementSpec = itemPlacementSpec,
                                fadeOutSpec = itemEffectsSpec,
                            )
                            .padding(horizontal = InsetListItemDefaults.OuterHorizontalPadding),
                        verticalAlignment = Alignment.Top,
                        shapes = InsetListItemDefaults.shapes(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        contentPadding = InsetListItemDefaults.ContentPadding,
                        trailingContent = {
                            IconButton(onClick = { onDeleteNote(note.id) }) {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            }
                        },
                        supportingContent = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = subtitle)

                                val quote = locator.text.highlight
                                if (!quote.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(note.color).copy(alpha = 1.0f)),
                                        )
                                        Text(
                                            text = quote,
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                    if (note.noteText.isNotBlank()) {
                                        Text(
                                            text = note.noteText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                } else if (note.noteText.isNotBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(note.color).copy(alpha = 1.0f)),
                                        )
                                        Text(
                                            text = note.noteText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                }
                            }
                        },
                        content = {
                            Text(
                                text = chapterTitle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}
