package com.example.readerapp.ui.features.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.NoteEntity

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
    var text by remember { mutableStateOf(note.noteText) }
    var selectedColor by remember { mutableIntStateOf(note.color) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 3,
                maxLines = 5,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.reader_enter_note_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Color Swatches
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                val swatches = listOf(
                    "#40fac02e".toColorInt(), // Yellow
                    "#40fd7142".toColorInt(), // Orange
                    "#408bc24a".toColorInt(), // Green
                    "#4025c6da".toColorInt()  // Blue
                )
                swatches.forEach { colorInt ->
                    val isSelected = selectedColor == colorInt
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Color(colorInt).copy(alpha = 1f) else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedColor = colorInt }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = Color(colorInt).copy(alpha = 1f), shape = CircleShape
                                )
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = {
                        if (note.id != 0L) {
                            onDeleteNote(note.id)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(
                            if (note.id != 0L) R.string.action_delete else R.string.action_cancel
                        )
                    )
                }
                Button(
                    onClick = {
                        onUpdateNote(note.copy(noteText = text, color = selectedColor))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
