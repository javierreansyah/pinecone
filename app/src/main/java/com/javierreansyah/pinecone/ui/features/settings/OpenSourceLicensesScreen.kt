package com.javierreansyah.pinecone.ui.features.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.core.net.toUri
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.SegmentedListItem
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var libraries by remember { mutableStateOf<List<Library>?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val customJson =
                resources.openRawResource(R.raw.custom_libraries).bufferedReader()
                    .use { it.readText() }
            val generatedLibs = Libs.Builder().withContext(context).build()
            val customLibs = Libs.Builder().withJson(customJson).build()

            libraries =
                (generatedLibs.libraries + customLibs.libraries).sortedBy { it.name.lowercase() }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopAppBar(
                title = { Text(stringResource(R.string.about_open_source_licenses)) },
                onBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
                isEmpty = libraries?.isEmpty() == true
            )
        }
    ) { innerPadding ->
        val libs = libraries
        if (libs != null && libs.isEmpty()) {
            Text(
                text = stringResource(R.string.about_no_libraries),
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    libs ?: emptyList(),
                    key = { _, item -> item.uniqueId }) { index, library ->
                    val license = library.licenses.firstOrNull()
                    SegmentedListItem(
                        selected = false,
                        index = index,
                        count = libs!!.size,
                        onClick = {
                            val url = library.website ?: license?.url
                            if (!url.isNullOrBlank()) {
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                context.startActivity(intent)
                            }
                        },
                        content = {
                            Text(
                                text = library.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = license?.name
                                    ?: stringResource(R.string.about_unknown_license),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    )
                }
            }
        }
    }
}
