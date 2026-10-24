package com.javierreansyah.pinecone.ui.features.reader.components.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R

@Composable
internal fun ConversionProgressDialog(
    page: Int,
    total: Int,
    onHide: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onHide,
        title = { Text(stringResource(R.string.reader_preparing_read_mode)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (total > 0) {
                    // Both conversion passes report work units; two units represent one page.
                    // Keep the bar based on raw units so it advances on every update.
                    Text(stringResource(R.string.reader_conversion_progress, page / 2, total / 2))
                    LinearWavyProgressIndicator(
                        progress = { (page.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onHide) {
                Text(stringResource(R.string.reader_hide_conversion))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
