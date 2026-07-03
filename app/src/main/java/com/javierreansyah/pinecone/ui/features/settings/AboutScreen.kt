package com.javierreansyah.pinecone.ui.features.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.javierreansyah.pinecone.ui.components.SegmentedColumn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                onBack = onNavigateBack,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SegmentedColumn(modifier = Modifier.padding(top = 16.dp)) {
                item(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/javierreansyah/pinecone/blob/main/COPYING".toUri()
                        )
                        context.startActivity(intent)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Gavel,
                            contentDescription = null
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_view_full_license),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null
                        )
                    }
                )

                item(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/javierreansyah/pinecone".toUri()
                        )
                        context.startActivity(intent)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Code,
                            contentDescription = null
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_source_code),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null
                        )
                    }
                )

                item(
                    onClick = onNavigateToOpenSourceLicenses,
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Info,
                            contentDescription = null
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_open_source_licenses),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Keyboard_arrow_right,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}
