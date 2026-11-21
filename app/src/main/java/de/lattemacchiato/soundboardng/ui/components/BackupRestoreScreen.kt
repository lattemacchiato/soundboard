package de.lattemacchiato.soundboardng.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.lattemacchiato.soundboardng.SoundboardApp
import de.lattemacchiato.soundboardng.data.BackupPreview
import de.lattemacchiato.soundboardng.data.BoardImportMerger
import de.lattemacchiato.soundboardng.data.ConfigurationBackup
import de.lattemacchiato.soundboardng.data.FullBackup
import de.lattemacchiato.soundboardng.data.FullBackupPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SoundboardApp
    val backup = remember(app) { ConfigurationBackup(app, app.repository) }
    val fullBackup = remember(app) { FullBackup(app, app.repository) }
    val scope = rememberCoroutineScope()
    val locked by app.lockSettings.locked.collectAsState()
    val boards by app.repository.boards.collectAsState(initial = emptyList())
    var showExportBoardDialog by remember { mutableStateOf(false) }
    var selectedExportBoardIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingExportBoardIds by remember { mutableStateOf<Set<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var fullPreview by remember { mutableStateOf<FullBackupPreview?>(null) }
    var selectedImportBoardIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    var importMode by remember {
        mutableStateOf(BoardImportMerger.Mode.ADD)
    }
    var showReplaceConfirmation by remember {
        mutableStateOf(false)
    }
    var message by remember { mutableStateOf<String?>(null) }
    var useExternalStorage by remember { mutableStateOf(false) }
    var pendingExternalRestore by remember { mutableStateOf<FullBackupPreview?>(null) }
    var pendingExternalBoardIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    var pendingExternalImportMode by remember {
        mutableStateOf(BoardImportMerger.Mode.ADD)
    }

    fun startInternalZipImport(
        candidate: FullBackupPreview,
        selectedBoardIds: Set<String>,
        mode: BoardImportMerger.Mode,
    ) {
        scope.launch {
            busy = true
            try {
                if (mode == BoardImportMerger.Mode.REPLACE) {
                    app.engine.pauseAll()
                    app.repository.sounds
                        .first()
                        .forEach { app.engine.release(it.id) }
                }

                fullBackup.restore(
                    preview = candidate,
                    selectedBoardIds = selectedBoardIds,
                    mode = mode,
                )

                fullPreview = null
                message = "ZIP import completed successfully."
            } catch (e: Exception) {
                message = "ZIP import failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }
    val selectImportFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val candidate = pendingExternalRestore
            pendingExternalRestore = null

            if (uri == null && candidate != null) {
                fullPreview = candidate
            }

            if (uri != null && candidate != null && !locked) {
                try {
                    require(
                        uri.scheme == "content" &&
                            uri.authority == "com.android.externalstorage.documents",
                    ) {
                        "Only local device storage or SD cards are supported for audio import"
                    }
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    scope.launch {
                        busy = true
                        try {
                            if (pendingExternalImportMode == BoardImportMerger.Mode.REPLACE) {
                                app.engine.pauseAll()
                                app.repository.sounds
                                    .first()
                                    .forEach { app.engine.release(it.id) }
                            }
                            fullBackup.restore(
                                preview = candidate,
                                externalFolderUri = uri,
                                selectedBoardIds = pendingExternalBoardIds,
                                mode = pendingExternalImportMode,
                            )
                            fullPreview = null
                            message = "Full backup restored successfully."
                        } catch (e: Exception) {
                            message = "Full backup restore failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                } catch (e: Exception) {
                    message = "Cannot access selected folder: ${e.message}"
                }
            }
        }

    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null && !locked) {
                scope.launch {
                    busy = true
                    try {
                        backup.export(uri)
                        message = "Configuration exported successfully."
                    } catch (
                        e: Exception,
                    ) {
                        message = "Export failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !locked) {
                scope.launch {
                    busy = true
                    try {
                        preview = backup.inspect(uri)
                    } catch (
                        e: Exception,
                    ) {
                        message = "Import failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        }

    val exportZip =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null && !locked) {
                scope.launch {
                    busy = true
                    try {
                        fullBackup.export(
                            destination = uri,
                            selectedBoardIds = pendingExportBoardIds,
                        )
                        message = "Full backup exported successfully."
                    } catch (
                        e: Exception,
                    ) {
                        message = "ZIP export failed: ${e.message}"
                    } finally {
                        pendingExportBoardIds = null
                        busy = false
                    }
                }
            }
        }
    val importZip =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !locked) {
                scope.launch {
                    busy = true
                    try {
                        val inspected = fullBackup.inspect(uri)
                        selectedImportBoardIds = inspected.boards.map { it.id }.toSet()
                        importMode = BoardImportMerger.Mode.ADD
                        fullPreview = inspected
                    } catch (
                        e: Exception,
                    ) {
                        message = "ZIP import failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Backup & Restore") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Export configuration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Save all boards, sound settings and global preferences as JSON. Audio files are not included.")
            Button(enabled = !locked && !busy, onClick = { export.launch("soundboard-configuration.json") }) {
                Text("Export JSON")
            }
            HorizontalDivider()
            Text(
                "Import configuration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Replace the current configuration from a JSON backup. Audio file references must still be accessible on this device.",
            )
            OutlinedButton(enabled = !locked && !busy, onClick = {
                import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }) {
                Text("Choose JSON backup…")
            }
            HorizontalDivider()
            Text(
                "Full backup (ZIP)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Includes all boards, settings and audio files. Suitable for transferring to another device.")
            Button(enabled = !locked && !busy, onClick = { exportZip.launch("soundboard-full-backup.zip") }) {
                Text("Export full ZIP")
            }
            OutlinedButton(
                enabled = !locked && !busy && boards.isNotEmpty(),
                onClick = {
                    selectedExportBoardIds = boards.map { it.id }.toSet()
                    showExportBoardDialog = true
                },
            ) {
                Text("Export selected boards")
            }
            OutlinedButton(enabled = !locked && !busy, onClick = {
                importZip.launch(arrayOf("application/zip", "application/octet-stream"))
            }) { Text("Import ZIP") }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    if (showExportBoardDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportBoardDialog = false
            },
            title = {
                Text("Select boards to export")
            },
            text = {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    boards.forEach { board ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = board.id in selectedExportBoardIds,
                                onCheckedChange = { checked ->
                                    selectedExportBoardIds =
                                        if (checked) {
                                            selectedExportBoardIds + board.id
                                        } else {
                                            selectedExportBoardIds - board.id
                                        }
                                },
                            )
                            Text(board.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedExportBoardIds.isNotEmpty(),
                    onClick = {
                        pendingExportBoardIds = selectedExportBoardIds
                        showExportBoardDialog = false
                        exportZip.launch("soundboard-selected-boards.zip")
                    },
                ) {
                    Text("Export ZIP")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportBoardDialog = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    preview?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text("Replace current configuration?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Backup contains ${candidate.boardCount} board(s) and ${candidate.soundCount} sound(s). All current boards and settings will be replaced.",
                    )
                    if (candidate.unavailableSounds.isNotEmpty()) {
                        Text(
                            "Warning: ${candidate.unavailableSounds.size} audio reference(s) are unavailable on this device. These sounds will not play until relinked.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            candidate.unavailableSounds.take(5).joinToString(", ") +
                                if (candidate.unavailableSounds.size > 5) "…" else "",
                        )
                    }
                    Text(
                        "A recovery copy of the previous configuration will be saved in private app storage before importing.",
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !locked && !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            app.engine.pauseAll()
                            app.repository.sounds
                                .first()
                                .forEach { app.engine.release(it.id) }
                            backup.restore(candidate)
                            preview = null
                            message = "Configuration imported successfully."
                        } catch (e: Exception) {
                            message = "Import failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text("Cancel") } },
        )
    }
    fullPreview?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!busy) fullPreview = null },
            title = { Text("Restore full backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${candidate.boardCount} board(s), ${candidate.soundCount} sound(s), " +
                            "${candidate.audioCount} audio file(s).",
                    )
                    Text(
                        "Select boards to import:",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Column(
                        modifier =
                            Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        candidate.boards.forEach { board ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = board.id in selectedImportBoardIds,
                                    onCheckedChange = { checked ->
                                        selectedImportBoardIds =
                                            if (checked) {
                                                selectedImportBoardIds + board.id
                                            } else {
                                                selectedImportBoardIds - board.id
                                            }
                                    },
                                    enabled = !busy,
                                )
                                Text("${board.name} (${board.sounds.size} sounds)")
                            }
                        }
                    }
                    Text(
                        "Import mode:",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = importMode == BoardImportMerger.Mode.ADD,
                            onClick = { importMode = BoardImportMerger.Mode.ADD },
                            enabled = !busy,
                        )
                        Text("ADD – Keep existing boards and settings")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = importMode == BoardImportMerger.Mode.REPLACE,
                            onClick = { importMode = BoardImportMerger.Mode.REPLACE },
                            enabled = !busy,
                        )
                        Text("REPLACE – Replace all boards and settings")
                    }
                    Text(
                        if (importMode == BoardImportMerger.Mode.ADD) {
                            "Selected boards will be added. Existing boards and settings " +
                                "will be preserved. Duplicate board names will be renamed."
                        } else {
                            "WARNING: All existing boards and settings will be replaced " +
                                "by the selected boards and the backup settings."
                        },
                        color =
                            if (importMode == BoardImportMerger.Mode.REPLACE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Text(
                        if (useExternalStorage) {
                            "Audio will be imported into a folder you select. Existing files will not be overwritten."
                        } else {
                            "Audio will be copied into private app storage."
                        },
                    )
                    Text("A recovery copy of the previous configuration will be saved.")

                    Text(
                        "Audio storage location:",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = !useExternalStorage,
                            onClick = { useExternalStorage = false },
                            enabled = !busy,
                        )
                        Text("Internal app storage (default)")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = useExternalStorage,
                            onClick = { useExternalStorage = true },
                            enabled = !busy,
                        )
                        Text("Selected folder (external storage)")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !locked && !busy && selectedImportBoardIds.isNotEmpty(),
                    onClick = {
                        if (importMode == BoardImportMerger.Mode.REPLACE) {
                            showReplaceConfirmation = true
                        } else if (useExternalStorage) {
                            pendingExternalRestore = candidate
                            pendingExternalBoardIds = selectedImportBoardIds.toSet()
                            pendingExternalImportMode = importMode
                            fullPreview = null
                            selectImportFolder.launch(null)
                        } else {
                            startInternalZipImport(
                                candidate = candidate,
                                selectedBoardIds = selectedImportBoardIds.toSet(),
                                mode = importMode,
                            )
                        }
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { fullPreview = null }) { Text("Cancel") }
            },
        )
    }
    if (showReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) showReplaceConfirmation = false
            },
            title = {
                Text("Replace existing boards?")
            },
            text = {
                Text(
                    "WARNING: All existing boards and settings will be replaced " +
                        "by the selected boards and the backup settings.\n\n" +
                        "This action cannot be undone directly. " +
                        "A recovery copy of the previous configuration will be saved.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && !locked,
                    onClick = {
                        val candidate = fullPreview
                        showReplaceConfirmation = false

                        if (candidate != null && selectedImportBoardIds.isNotEmpty()) {
                            if (useExternalStorage) {
                                pendingExternalRestore = candidate
                                pendingExternalBoardIds = selectedImportBoardIds.toSet()
                                pendingExternalImportMode = BoardImportMerger.Mode.REPLACE
                                fullPreview = null
                                selectImportFolder.launch(null)
                            } else {
                                startInternalZipImport(
                                    candidate = candidate,
                                    selectedBoardIds = selectedImportBoardIds.toSet(),
                                    mode = BoardImportMerger.Mode.REPLACE,
                                )
                            }
                        }
                    },
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showReplaceConfirmation = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Backup & Restore") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}
