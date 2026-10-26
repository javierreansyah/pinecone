package com.javierreansyah.pinecone.ui.features.library.unsorted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Inbox
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.features.library.LibraryCollectionScreen

@Composable
fun UnsortedScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    viewModel: UnsortedViewModel = hiltViewModel(),
) {
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
}
