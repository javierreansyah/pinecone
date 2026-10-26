package com.javierreansyah.pinecone.ui.features.library.unsorted

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Inbox
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.features.library.LibraryCollectionScreen
import com.javierreansyah.pinecone.ui.features.library.components.ImportFeedback
import com.javierreansyah.pinecone.ui.features.library.components.ImportFeedbackState
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding

@Composable
fun UnsortedScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    viewModel: UnsortedViewModel = hiltViewModel(),
    importFeedbackState: ImportFeedbackState = ImportFeedbackState(),
    importFeedbackEnabled: Boolean = true,
    onImportFiles: () -> Unit = {},
    onScanFolder: () -> Unit = {},
    onCancelImport: (String) -> Unit = {},
    onDismissImportReport: (String) -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        LibraryCollectionScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToReader = onNavigateToReader,
            onNavigateToBookInfo = onNavigateToBookInfo,
            onNavigateToOrganize = onNavigateToOrganize,
            viewModel = viewModel,
            title = stringResource(R.string.library_unsorted_title),
            emptyText = stringResource(R.string.library_empty_unsorted),
            emptyIcon = MaterialSymbols.Outlined.Inbox,
            archived = false,
        )
        if (importFeedbackEnabled) {
            ImportFeedback(
                state = importFeedbackState,
                onCancel = onCancelImport,
                onDismissReport = onDismissImportReport,
                content = { UnsortedImportFabMenu(onImportFiles, onScanFolder) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = LocalAudioPlayerBottomPadding.current),
            )
        }
    }
}
