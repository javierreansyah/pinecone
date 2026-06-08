package com.example.readerapp.ui.features.reader.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.toColorInt

@Composable
fun CustomColorPickerDialog(
    initialName: String = "",
    initialBgColor: String = "#",
    initialTextColor: String = "#",
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var bgColorInput by remember { mutableStateOf(if (initialBgColor.isBlank() || initialBgColor == "#") "#" else initialBgColor) }
    var textColorInput by remember { mutableStateOf(if (initialTextColor.isBlank() || initialTextColor == "#") "#" else initialTextColor) }

    val formatColorInput = { input: String ->
        val cleaned = input.replace("#", "")
        if (cleaned.isNotEmpty()) "#$cleaned" else "#"
    }

    fun parseColorSafe(colorStr: String): Color? {
        if (colorStr.length != 7 && colorStr.length != 9) return null
        return try {
            Color(colorStr.toColorInt())
        } catch (e: Exception) {
            null
        }
    }

    val parsedBg = parseColorSafe(bgColorInput)
    val parsedText = parseColorSafe(textColorInput)

    val isValid = name.isNotBlank() && parsedBg != null && parsedText != null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (onDelete != null) "Edit Theme" else "New Theme", 
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Start)
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(parsedBg ?: Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.headlineLarge,
                        color = parsedText ?: MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Theme Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bgColorInput,
                    onValueChange = { bgColorInput = formatColorInput(it) },
                    label = { Text("Background Color (Hex)") },
                    placeholder = { Text("#FFFFFF") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = textColorInput,
                    onValueChange = { textColorInput = formatColorInput(it) },
                    label = { Text("Text Color (Hex)") },
                    placeholder = { Text("#000000") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    } else {
                        // Spacer to keep Save button on the right when Delete is not present
                        Box(modifier = Modifier.weight(1f))
                    }
                    
                    Row(horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (isValid) {
                                    onConfirm(name, bgColorInput.uppercase(), textColorInput.uppercase())
                                }
                            },
                            enabled = isValid
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}
