package com.example.readerapp.ui.features.reader.components.contents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Edit
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.ui.components.EmptyState
import org.readium.r2.shared.publication.Locator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesList(
    notes: List<NoteEntity>,
    getPositionLabel: (Locator) -> String,
    onNoteClick: (Locator) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    val context = LocalContext.current
    if (notes.isEmpty()) {
        EmptyState(
            icon = MaterialSymbols.Outlined.Edit,
            text = stringResource(R.string.reader_no_notes),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(notes) { note ->
                val locator = try {
                    Locator.fromJSON(org.json.JSONObject(note.locatorJson))
                } catch (_: Exception) {
                    null
                }
                if (locator != null) {
                    val inDocument = stringResource(R.string.reader_in_document)
                    val chapterTitle =
                        note.chapterTitle?.takeIf { it.isNotBlank() && it != inDocument }
                            ?: inDocument

                    val positionLabel = getPositionLabel(locator)
                    val formattedDate = remember(note.createdAt, context) {
                        val date = Date(note.createdAt)
                        val locale = Locale.getDefault()
                        val dateStr = if (locale.language == "en") {
                            SimpleDateFormat("MM/dd/yyyy", locale).format(date)
                        } else {
                            android.text.format.DateFormat.getDateFormat(context).format(date)
                        }
                        val timeStr =
                            android.text.format.DateFormat.getTimeFormat(context).format(date)
                        "created $dateStr $timeStr"
                    }
                    val subtitle = if (positionLabel.isNotBlank()) {
                        "$positionLabel | $formattedDate"
                    } else {
                        formattedDate
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNoteClick(locator) }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        EntryHeader(
                            title = chapterTitle,
                            positionLabel = subtitle,
                            onDelete = { onDeleteNote(note.id) }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            val quote = locator.text.highlight
                            if (!quote.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(note.color).copy(alpha = 1.0f))
                                    )
                                    Text(
                                        text = quote,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                                Text(
                                    text = note.noteText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(note.color).copy(alpha = 1.0f))
                                    )
                                    Text(
                                        text = note.noteText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
