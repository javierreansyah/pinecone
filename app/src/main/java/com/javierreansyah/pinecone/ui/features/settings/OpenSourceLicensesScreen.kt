package com.javierreansyah.pinecone.ui.features.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBarTitle
import com.javierreansyah.pinecone.ui.components.SegmentedListDefaults
import com.javierreansyah.pinecone.ui.components.horizontalPadding
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Volatile
private var cachedOpenSourceLibraries: List<Library>? = null

private suspend fun loadOpenSourceLibraries(
    appContext: Context,
): List<Library> {
    cachedOpenSourceLibraries?.let { return it }
    return withContext(Dispatchers.IO) {
        val customJson =
            appContext.resources.openRawResource(R.raw.custom_libraries).bufferedReader()
                .use { it.readText() }
        val (generatedLibsLibraries, _) = Libs.Builder().withContext(appContext).build()
        val (customLibsLibraries, _) = Libs.Builder().withJson(customJson).build()

        (generatedLibsLibraries + customLibsLibraries)
            .sortedBy { library -> library.name.lowercase() }
            .also { cachedOpenSourceLibraries = it }
    }
}

@Composable
fun OpenSourceLicensesScreen(
    onNavigateBack: () -> Unit,
    onLicenseClick: (libraryId: String, libraryName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var libraries by remember { mutableStateOf(cachedOpenSourceLibraries) }

    LaunchedEffect(Unit) {
        if (libraries == null) {
            libraries = loadOpenSourceLibraries(appContext)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopAppBar(
                title = { LibraryTopAppBarTitle(stringResource(R.string.about_open_source_licenses)) },
                onBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
                isEmpty = libraries?.isEmpty() == true,
            )
        },
    ) { innerPadding ->
        val libs = libraries
        if (libs != null && libs.isEmpty()) {
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
                    .horizontalPadding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 8.dp + LocalAudioPlayerBottomPadding.current,
                ),
                verticalArrangement = Arrangement.spacedBy(SegmentedListDefaults.Gap),
            ) {
                itemsIndexed(
                    libs ?: emptyList(),
                    key = { _, library -> library.uniqueId },
                ) { index, library ->
                    val license = library.licenses.firstOrNull()
                    SegmentedListItem(
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shapes = SegmentedListDefaults.shapes(
                            index = index,
                            count = (libs ?: return@itemsIndexed).size
                        ),
                        onClick = {
                            onLicenseClick(library.uniqueId, library.name)
                        },
                        content = {
                            Text(
                                text = library.name,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = license?.name
                                    ?: stringResource(R.string.about_unknown_license),
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun OpenSourceLicenseDetailScreen(
    libraryId: String,
    libraryName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    var library by remember(libraryId) {
        mutableStateOf(cachedOpenSourceLibraries?.find { it.uniqueId == libraryId })
    }

    LaunchedEffect(libraryId) {
        if (library == null) {
            library = loadOpenSourceLibraries(appContext).find { it.uniqueId == libraryId }
        }
    }

    val scrollState = rememberScrollState()
    val topAppBarState = rememberTopAppBarState()
    val canScroll by remember {
        derivedStateOf { scrollState.maxValue > 0 || topAppBarState.heightOffset < 0f }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = topAppBarState,
        canScroll = { canScroll },
    )

    val licenses = library?.licenses.orEmpty()
    val subtitleText = licenses
        .mapNotNull { it.name.takeIf(String::isNotBlank) }
        .distinct()
        .joinToString(", ")
        .ifBlank { null }

    Scaffold(
        modifier = modifier.then(
            if (canScroll) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        ),
        topBar = {
            LibraryTopAppBar(
                title = { LibraryTopAppBarTitle(library?.name ?: libraryName) },
                subtitle = subtitleText?.let { text -> { Text(text = text) } },
                onBack = onNavigateBack,
                scrollBehavior = if (canScroll) scrollBehavior else null,
            )
        },
    ) { innerPadding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, enabled = canScroll)
                    .padding(innerPadding)
                    .padding(bottom = LocalAudioPlayerBottomPadding.current)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (library != null) {
                    if (licenses.isEmpty()) {
                        Text(
                            text = stringResource(R.string.about_unknown_license),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        licenses.forEachIndexed { index, license ->
                            if (licenses.size > 1 && license.name.isNotBlank()) {
                                Text(
                                    text = license.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = if (index > 0) Modifier.padding(top = 8.dp) else Modifier,
                                )
                            }
                            Text(
                                text = license.licenseContent?.takeIf(String::isNotBlank)
                                    ?: stringResource(R.string.about_unknown_license),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
