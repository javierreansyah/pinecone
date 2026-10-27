package com.javierreansyah.pinecone.ui.features.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Code
import com.composables.icons.materialsymbols.outlined.Gavel
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.LibraryTopAppBar
import com.javierreansyah.pinecone.ui.root.LocalAudioPlayerBottomPadding

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    if (showLicense) {
        val license =
            remember { context.assets.open("COPYING").bufferedReader().use { it.readText() } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text(stringResource(R.string.about_agpl_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.about_license_summary),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Text(license)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showLicense = false
                }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                onBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = LocalAudioPlayerBottomPadding.current)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SegmentedListItem(
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                    onClick = {
                        showLicense = true
                    },
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Gavel,
                            contentDescription = null,
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_view_full_license),
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null,
                        )
                    },
                )

                SegmentedListItem(
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/javierreansyah/pinecone".toUri(),
                        )
                        context.startActivity(intent)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Code,
                            contentDescription = null,
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_source_code),
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null,
                        )
                    },
                )

                SegmentedListItem(
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                    onClick = onNavigateToOpenSourceLicenses,
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Info,
                            contentDescription = null,
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_open_source_licenses),
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
