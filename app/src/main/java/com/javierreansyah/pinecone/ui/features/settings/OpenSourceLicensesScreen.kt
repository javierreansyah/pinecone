package com.javierreansyah.pinecone.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.SegmentedListItem
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OpenSourceLicensesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var libraries by remember { mutableStateOf<List<Library>?>(null) }
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val customJson =
                resources.openRawResource(R.raw.custom_libraries).bufferedReader()
                    .use { it.readText() }
            val (generatedLibsLibraries) = Libs.Builder().withContext(context).build()
            val (customLibsLibraries) = Libs.Builder().withJson(customJson).build()

            libraries =
                (generatedLibsLibraries + customLibsLibraries).sortedBy { (_, _, itName) -> itName.lowercase() }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopAppBar(
                title = { Text(stringResource(R.string.about_open_source_licenses)) },
                onBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
                isEmpty = libraries?.isEmpty() == true,
            )
        },
    ) { innerPadding ->
        val libs = libraries
        if ((libs != null) && libs.isEmpty()) {
            Text(
                text = stringResource(R.string.about_no_libraries),
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    libs ?: emptyList(),
                    key = { _, (itemUniqueId) -> itemUniqueId },
                ) { index, library ->
                    val license = library.licenses.firstOrNull()
                    SegmentedListItem(
                        selected = false,
                        index = index,
                        count = (libs ?: return@itemsIndexed).size,
                        onClick = {
                            selectedLibrary = library
                        },
                        content = {
                            Text(
                                text = library.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = license?.name
                                    ?: stringResource(R.string.about_unknown_license),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                    )
                }
            }
        }

        selectedLibrary?.let { (_, _, libraryName, _, _, _, _, _, libraryLicenses) ->
            val license = libraryLicenses.firstOrNull()
            AlertDialog(
                onDismissRequest = { selectedLibrary = null },
                confirmButton = {
                    TextButton(onClick = { selectedLibrary = null }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                title = {
                    Text(text = libraryName)
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = license?.licenseContent
                                ?: stringResource(R.string.about_unknown_license),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
        }
    }
}
