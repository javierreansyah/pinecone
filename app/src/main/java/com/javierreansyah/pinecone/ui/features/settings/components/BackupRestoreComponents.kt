package com.javierreansyah.pinecone.ui.features.settings.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_right
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.repository.backup.BackupProgress
import com.javierreansyah.pinecone.ui.components.SegmentedListDefaults
import com.javierreansyah.pinecone.ui.features.settings.BackupFile
import com.javierreansyah.pinecone.ui.features.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun rememberBackupFolderPickerLauncher(
    onFolderSelected: (Uri) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                val intent = super.createIntent(context, input)
                input?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                return intent
            }
        },
    ) { uri ->
        uri?.let {
            val takeFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            onFolderSelected(it)
        }
    }

    return {
        val pineconeDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Pinecone",
        )
        if (!pineconeDir.exists()) {
            pineconeDir.mkdirs()
        }
        val initialUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents/Pinecone",
        )
        folderPickerLauncher.launch(initialUri)
    }
}

@Composable
fun rememberRestoreBackupAction(
    viewModel: SettingsViewModel = hiltViewModel(),
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val navRestoringBackupMsg = stringResource(R.string.nav_restoring_backup)
    val navRestoreSuccessMsg = stringResource(R.string.nav_restore_success)
    val navRestoreFailedMsg = stringResource(R.string.nav_restore_failed)
    val restorePartialMsg = stringResource(R.string.settings_restore_partial)
    val errorSetBackupLocationMsg = stringResource(R.string.settings_error_set_backup_location)

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val restoreProgress by viewModel.restoreProgress.collectAsStateWithLifecycle()
    val availableBackups by viewModel.availableBackups.collectAsStateWithLifecycle()

    var showRestoreBottomSheet by remember { mutableStateOf(value = false) }
    var showRestoreWarning by remember { mutableStateOf(value = false) }
    var selectedBackupToRestore by remember { mutableStateOf<BackupFile?>(null) }

    val hasPermission = settings.backupFolderUri.isNotEmpty() &&
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == settings.backupFolderUri
            }

    val launchFolderPicker = rememberBackupFolderPickerLauncher { uri ->
        scope.launch {
            viewModel.updateSettingsSuspended { current ->
                current.copy(backupFolderUri = uri.toString())
            }
            viewModel.loadBackupsSuspended()
            showRestoreBottomSheet = true
        }
    }

    if (showRestoreBottomSheet) {
        RestoreBottomSheet(
            availableBackups = availableBackups,
            onDismiss = { showRestoreBottomSheet = false },
            onSelectBackup = { backup ->
                selectedBackupToRestore = backup
                showRestoreBottomSheet = false
                showRestoreWarning = true
            },
        )
    }

    if (showRestoreWarning) {
        RestoreBackupWarningDialog(
            onDismiss = {
                showRestoreWarning = false
                selectedBackupToRestore = null
            },
        ) {
            showRestoreWarning = false
            selectedBackupToRestore?.uri?.let { uri ->
                viewModel.restoreFullBackup(
                    uri = uri,
                    onStart = {
                        Toast.makeText(context, navRestoringBackupMsg, Toast.LENGTH_SHORT).show()
                    },
                    onSuccess = {
                        Toast.makeText(context, navRestoreSuccessMsg, Toast.LENGTH_SHORT).show()
                    },
                    onWarning = {
                        Toast.makeText(context, restorePartialMsg, Toast.LENGTH_LONG).show()
                    },
                ) {
                    Toast.makeText(context, navRestoreFailedMsg, Toast.LENGTH_SHORT).show()
                }
            }
            selectedBackupToRestore = null
        }
    }

    if (isRestoring) {
        RestoreBackupProgressDialog(progress = restoreProgress)
    }

    return {
        if (!isRestoring) {
            if (!hasPermission) {
                Toast.makeText(context, errorSetBackupLocationMsg, Toast.LENGTH_LONG).show()
                launchFolderPicker()
            } else {
                scope.launch {
                    viewModel.loadBackupsSuspended()
                    showRestoreBottomSheet = true
                }
            }
        }
    }
}

@Composable
fun RestoreBackupWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nav_restore_backup)) },
        text = { Text(stringResource(R.string.settings_restore_backup_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_proceed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun RestoreBackupProgressDialog(
    progress: BackupProgress,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "restoreDialogProgressFraction",
    )

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(stringResource(R.string.nav_restoring_backup)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (progress.totalFiles == 0) {
                        stringResource(R.string.settings_restore_preparing)
                    } else {
                        stringResource(
                            R.string.settings_backup_progress,
                            progress.completedFiles,
                            progress.totalFiles,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearWavyProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBottomSheet(
    availableBackups: List<BackupFile>,
    onDismiss: () -> Unit,
    onSelectBackup: (BackupFile) -> Unit,
) {
    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_restore_backup_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (availableBackups.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_backups_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                val groupedBackups =
                    availableBackups.groupBy { backup -> backup.isManual }
                val groups = groupedBackups.entries.sortedByDescending { (_, entryValue) ->
                    entryValue.maxOfOrNull { backup -> backup.timestamp } ?: 0L
                }

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    groups.forEach { (isManual, backups) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isManual) stringResource(R.string.settings_backup_manual_label)
                                else stringResource(R.string.settings_backup_auto_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(SegmentedListDefaults.Gap)) {
                                backups.sortedByDescending { backup -> backup.timestamp }
                                    .forEachIndexed { index, backup ->
                                        SegmentedListItem(
                                            verticalAlignment = Alignment.CenterVertically,
                                            colors = ListItemDefaults.segmentedColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            ),
                                            shapes = SegmentedListDefaults.shapes(
                                                index = index,
                                                count = backups.size,
                                            ),
                                            onClick = {
                                                onSelectBackup(backup)
                                                scope.launch { bottomSheetState.hide() }
                                                    .invokeOnCompletion {
                                                        if (!bottomSheetState.isVisible) {
                                                            onDismiss()
                                                        }
                                                    }
                                            },
                                            leadingContent = {
                                                Icon(
                                                    imageVector = MaterialSymbols.Outlined.History,
                                                    contentDescription = null,
                                                )
                                            },
                                            content = {
                                                Text(
                                                    text = backup.formattedDate,
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
            }
        }
    }
}
