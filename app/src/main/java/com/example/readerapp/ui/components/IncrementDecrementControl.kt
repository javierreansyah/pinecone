package com.example.readerapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedIconButton(
                onClick = onDecrement,
                enabled = enabled,
                shape = CircleShape,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(
                    1.5.dp,
                    if (enabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                )
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.widthIn(min = 60.dp)
            )
            
            OutlinedIconButton(
                onClick = onIncrement,
                enabled = enabled,
                shape = CircleShape,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(
                    1.dp, 
                    if (enabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                )
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
