package com.example.readerapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Remove

@Composable
fun IncrementDecrementControl(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.5f else 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement, enabled = enabled) {
                    Icon(MaterialSymbols.Outlined.Remove, contentDescription = "Decrease")
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                IconButton(onClick = onIncrement, enabled = enabled) {
                    Icon(MaterialSymbols.Outlined.Add, contentDescription = "Increase")
                }
            }
        }
    }
}
