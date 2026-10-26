package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.OperationSnapshot
import com.javierreansyah.pinecone.ui.components.OperationFeedback
import com.javierreansyah.pinecone.ui.components.OperationReport

data class ImportFeedbackState(
    val operation: OperationSnapshot? = null,
    val report: ImportReport? = null,
)

data class ImportReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val operationId: String? = null,
    val failedFiles: List<String>,
    val successCount: Int,
    val inputRequired: Boolean = false,
)

/** Screen-owned presentation; workers and pending reports outlive this composition. */
@Composable
internal fun ImportFeedback(
    state: ImportFeedbackState,
    onCancel: (String) -> Unit,
    onDismissReport: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    val resumed =
        LocalLifecycleOwner.current.lifecycle.currentStateAsState().value == Lifecycle.State.RESUMED
    val operation = state.operation
    val report = state.report.takeIf { resumed }
    OperationFeedback(
        operationId = operation?.id,
        progress = operation?.takeIf { it.total > 0 }?.let { it.completed.toFloat() / it.total },
        title = stringResource(R.string.publication_work_importing),
        progressMessage = operation?.let {
            if (it.total > 0) stringResource(
                R.string.publication_work_processing,
                it.completed,
                it.total
            )
            else stringResource(R.string.publication_work_preparing)
        }.orEmpty(),
        onCancel = { operation?.let { onCancel(it.id) } },
        report = report?.toFeedbackReport(),
        onDismissReport = { report?.let { onDismissReport(it.id) } },
        content = content,
        modifier = modifier,
    )
}

@Composable
internal fun ImportReport.toFeedbackReport(): OperationReport {
    val title = stringResource(
        when {
            successCount > 0 -> R.string.import_warning_title
            else -> R.string.import_failed_title
        }
    )
    val messages = mutableListOf<String>()
    if (successCount > 0) {
        messages += pluralStringResource(
            R.plurals.import_partial_success_message,
            successCount,
            successCount
        )
    }
    messages += if (failedFiles.isNotEmpty()) {
        stringResource(R.string.import_failed_files, failedFiles.joinToString("\n"))
    } else {
        stringResource(R.string.import_failed_reason_note)
    }
    if (inputRequired) messages += stringResource(R.string.publication_input_required)
    return OperationReport(id, title, messages.joinToString("\n\n"))
}
