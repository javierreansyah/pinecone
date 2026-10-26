package com.javierreansyah.pinecone.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Error
import com.composables.icons.materialsymbols.outlined.Warning
import com.javierreansyah.pinecone.R

@Composable
fun ImportErrorDialog(
    report: ImportErrorReport,
    onDismiss: () -> Unit,
) {
    val isPartialSuccess = report.successCount > 0
    val title = if (isPartialSuccess) {
        stringResource(R.string.import_warning_title)
    } else {
        stringResource(R.string.import_failed_title)
    }

    val icon = if (isPartialSuccess) {
        MaterialSymbols.Outlined.Warning
    } else {
        MaterialSymbols.Outlined.Error
    }

    val iconTint = if (isPartialSuccess) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (report.inputRequired) {
                    Text(stringResource(R.string.publication_input_required))
                }
                if (report.failedFiles.size == 1 && report.totalCount == 1) {
                    Text(
                        text = stringResource(
                            R.string.import_failed_single_message,
                            report.failedFiles.first(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (isPartialSuccess) {
                        Text(
                            text = "${
                                pluralStringResource(
                                    R.plurals.import_partial_success_message,
                                    report.successCount,
                                    report.successCount,
                                )
                            } ${
                                pluralStringResource(
                                    R.plurals.import_partial_failed_message,
                                    report.failedFiles.size,
                                    report.failedFiles.size,
                                )
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = pluralStringResource(
                                R.plurals.import_all_failed_message,
                                report.failedFiles.size,
                                report.failedFiles.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(report.failedFiles) { fileName ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.import_failed_reason_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}
