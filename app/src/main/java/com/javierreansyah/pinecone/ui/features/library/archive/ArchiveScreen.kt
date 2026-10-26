package com.javierreansyah.pinecone.ui.features.library.archive

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Book
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.features.library.LibraryCollectionScreen

@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToBookInfo: (String) -> Unit,
    onNavigateToOrganize: (Set<String>) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    LibraryCollectionScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToReader = onNavigateToReader,
        onNavigateToBookInfo = onNavigateToBookInfo,
        onNavigateToOrganize = onNavigateToOrganize,
        viewModel = viewModel,
        title = stringResource(R.string.library_archives_title),
        emptyText = stringResource(R.string.library_empty_archives),
        emptyIcon = MaterialSymbols.Outlined.Book,
        archived = true,
    )
}
