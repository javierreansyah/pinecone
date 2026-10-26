package com.javierreansyah.pinecone.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javierreansyah.pinecone.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Screen-owned failure state; navigation callbacks run only after the mutation succeeds. */
class MutationErrorState {
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()

    fun dismiss() { mutableFailed.value = false }

    suspend fun run(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.e(error, "Could not update collection")
            mutableFailed.value = true
        }
    }
}

@Composable
fun MutationErrorDialog(state: MutationErrorState) {
    val failed by state.failed.collectAsStateWithLifecycle()
    if (failed) AlertDialog(
        onDismissRequest = state::dismiss,
        text = { Text(stringResource(R.string.common_operation_failed)) },
        confirmButton = {
            TextButton(onClick = state::dismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}
