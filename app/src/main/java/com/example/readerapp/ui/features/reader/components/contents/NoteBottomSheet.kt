package com.example.readerapp.ui.features.reader.components.contents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteBottomSheet(
    note: NoteEntity,
    onUpdateNote: (NoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var editText by remember(note.id) { mutableStateOf(note.noteText) }
    var editColor by remember(note.id) { mutableIntStateOf(note.color) }

    val swatches = listOf(
        "#40fac02e".toColorInt(), // Yellow
        "#40fd7142".toColorInt(), // Orange
        "#408bc24a".toColorInt(), // Green
        "#4025c6da".toColorInt()  // Blue
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.space16)
        ) {
            // Text field for editing note text
            OutlinedTextField(
                value = editText,
                onValueChange = {
                    editText = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.reader_note_text_placeholder), style = MaterialTheme.typography.bodyLarge) },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 2
            )

            // Color Swatches: smaller, outline and ring look, justified left
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                swatches.forEach { colorInt ->
                    val isSelected = editColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .let { m ->
                                if (isSelected) {
                                    m.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                } else m
                            }
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt).copy(alpha = 1f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable {
                                editColor = colorInt
                            }
                    )
                }
            }

            // Buttons: full width side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        onDeleteNote(note.id)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = ButtonDefaults.shape
                ) {
                    Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = {
                        onUpdateNote(note.copy(noteText = editText, color = editColor))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = ButtonDefaults.shape
                ) {
                    Text(stringResource(R.string.action_save), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
