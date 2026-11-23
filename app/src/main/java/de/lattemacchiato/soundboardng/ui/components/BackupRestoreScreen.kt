package de.lattemacchiato.soundboardng.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.lattemacchiato.soundboardng.R
import de.lattemacchiato.soundboardng.SoundboardApp
import de.lattemacchiato.soundboardng.data.BackupPreview
import de.lattemacchiato.soundboardng.data.BoardImportMerger
import de.lattemacchiato.soundboardng.data.ConfigurationBackup
import de.lattemacchiato.soundboardng.data.FullBackup
import de.lattemacchiato.soundboardng.data.FullBackupPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class ExportFormat {
    ZIP,
    JSON,
}

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
    var exportFormat by remember { mutableStateOf(ExportFormat.ZIP) }
    var selectedExportBoardIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingExportBoardIds by remember { mutableStateOf<Set<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var selectedJsonImportBoardIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    var jsonImportMode by remember {
        mutableStateOf(BoardImportMerger.Mode.ADD)
    }
    var showJsonReplaceConfirmation by remember {
        mutableStateOf(false)
    }
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
                message = app.getString(R.string.backup_zip_import_success)
            } catch (e: Exception) {
                message = app.getString(R.string.backup_zip_import_error, e.message)
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
                        app.getString(R.string.backup_local_storage_only)
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
                            message = app.getString(R.string.backup_restore_success)
                        } catch (e: Exception) {
                            message = app.getString(R.string.backup_restore_error, e.message)
                        } finally {
                            busy = false
                        }
                    }
                } catch (e: Exception) {
                    message = app.getString(R.string.backup_folder_error, e.message)
                }
            }
        }

    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val boardIds = pendingExportBoardIds
            pendingExportBoardIds = null

            if (uri != null && !locked) {
                scope.launch {
                    busy = true
                    try {
                        backup.export(
                            destination = uri,
                            selectedBoardIds = boardIds,
                        )
                        message = app.getString(R.string.backup_config_export_success)
                    } catch (
                        e: Exception,
                    ) {
                        message = app.getString(R.string.backup_export_error, e.message)
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
                        val candidate = backup.inspect(uri)

                        selectedJsonImportBoardIds =
                            candidate.library.boards
                                .map { it.id }
                                .toSet()
                        jsonImportMode = BoardImportMerger.Mode.ADD
                        showJsonReplaceConfirmation = false

                        preview = candidate
                    } catch (
                        e: Exception,
                    ) {
                        message = app.getString(R.string.backup_import_error, e.message)
                    } finally {
                        busy = false
                    }
                }
            }
        }

    val exportZip =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val boardIds = pendingExportBoardIds
            pendingExportBoardIds = null

            if (uri != null && !locked) {
                scope.launch {
                    busy = true
                    try {
                        fullBackup.export(
                            destination = uri,
                            selectedBoardIds = boardIds,
                        )
                        message = app.getString(R.string.backup_zip_export_success)
                    } catch (
                        e: Exception,
                    ) {
                        message = app.getString(R.string.backup_zip_export_error, e.message)
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
                        message = app.getString(R.string.backup_zip_import_error, e.message)
                    } finally {
                        busy = false
                    }
                }
            }
        }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.nav_backup_restore)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
            }
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
                stringResource(R.string.backup_boards_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.backup_boards_description),
            )
            Button(
                enabled = !locked && !busy && boards.isNotEmpty(),
                onClick = {
                    exportFormat = ExportFormat.ZIP
                    selectedExportBoardIds = boards.map { it.id }.toSet()
                    showExportBoardDialog = true
                },
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_export_boards))
            }
            OutlinedButton(
                enabled = !locked && !busy,
                onClick = {
                    importZip.launch(arrayOf("application/zip"))
                },
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_import_boards))
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.backup_configuration_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.backup_configuration_description),
            )
            Button(
                enabled = !locked && !busy,
                onClick = {
                    exportFormat = ExportFormat.JSON
                    selectedExportBoardIds = boards.map { it.id }.toSet()
                    showExportBoardDialog = true
                },
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_export_configuration))
            }
            OutlinedButton(
                enabled = !locked && !busy,
                onClick = {
                    import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_import_configuration))
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    if (showExportBoardDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportBoardDialog = false
            },
            title = {
                Text(stringResource(R.string.backup_select_export_boards))
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

                        when (exportFormat) {
                            ExportFormat.ZIP ->
                                exportZip.launch("soundboard-boards.zip")

                            ExportFormat.JSON ->
                                export.launch("soundboard-configuration.json")
                        }
                    },
                ) {
                    Text(
                        when (exportFormat) {
                            ExportFormat.ZIP -> stringResource(R.string.backup_export_boards)
                            ExportFormat.JSON -> stringResource(R.string.backup_export_configuration)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportBoardDialog = false
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    preview?.takeUnless { showJsonReplaceConfirmation }?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text(stringResource(R.string.backup_import_configuration)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.backup_json_import_description),
                    )
                    Text(
                        stringResource(
                            R.string.backup_selected_boards,
                            selectedJsonImportBoardIds.size,
                            candidate.boardCount,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        candidate.library.boards.forEach { board ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = board.id in selectedJsonImportBoardIds,
                                    onCheckedChange = { checked ->
                                        selectedJsonImportBoardIds =
                                            if (checked) {
                                                selectedJsonImportBoardIds + board.id
                                            } else {
                                                selectedJsonImportBoardIds - board.id
                                            }
                                    },
                                )
                                Text(board.name)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.backup_import_mode),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = jsonImportMode == BoardImportMerger.Mode.ADD,
                            onClick = {
                                jsonImportMode = BoardImportMerger.Mode.ADD
                            },
                        )
                        Text(stringResource(R.string.backup_add_boards))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = jsonImportMode == BoardImportMerger.Mode.REPLACE,
                            onClick = {
                                jsonImportMode = BoardImportMerger.Mode.REPLACE
                            },
                        )
                        Text(stringResource(R.string.backup_replace_all_boards))
                    }

                    Text(
                        if (jsonImportMode == BoardImportMerger.Mode.ADD) {
                            stringResource(R.string.backup_json_add_description)
                        } else {
                            stringResource(R.string.backup_json_replace_description)
                        },
                    )
                    if (candidate.unavailableSounds.isNotEmpty()) {
                        val unavailableSelectedSounds =
                            candidate.unavailableSounds.filter { reference ->
                                reference.boardId in selectedJsonImportBoardIds
                            }

                        if (unavailableSelectedSounds.isNotEmpty()) {
                            Text(
                                stringResource(R.string.backup_missing_audio_warning, unavailableSelectedSounds.size),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                unavailableSelectedSounds
                                    .take(5)
                                    .joinToString(", ") { reference ->
                                        "${reference.boardName}: ${reference.soundName}"
                                    } +
                                    if (unavailableSelectedSounds.size > 5) "…" else "",
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.backup_json_recovery_notice),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !locked && !busy && selectedJsonImportBoardIds.isNotEmpty(),
                    onClick = {
                        if (jsonImportMode == BoardImportMerger.Mode.REPLACE) {
                            showJsonReplaceConfirmation = true
                        } else {
                            scope.launch {
                                busy = true
                                try {
                                    backup.restore(
                                        preview = candidate,
                                        selectedBoardIds = selectedJsonImportBoardIds,
                                        mode = BoardImportMerger.Mode.ADD,
                                    )
                                    preview = null
                                    message = app.getString(R.string.backup_config_import_success)
                                } catch (e: Exception) {
                                    message = app.getString(R.string.backup_import_error, e.message)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.backup_import_button))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { preview = null },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showJsonReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) showJsonReplaceConfirmation = false
            },
            title = {
                Text(stringResource(R.string.backup_json_replace_title))
            },
            text = {
                Text(
                    stringResource(R.string.backup_json_replace_confirmation),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !locked && !busy,
                    onClick = {
                        val candidate = preview
                        val boardIds = selectedJsonImportBoardIds

                        if (candidate != null && boardIds.isNotEmpty()) {
                            scope.launch {
                                busy = true
                                try {
                                    app.engine.pauseAll()
                                    app.repository.sounds
                                        .first()
                                        .forEach { app.engine.release(it.id) }

                                    backup.restore(
                                        preview = candidate,
                                        selectedBoardIds = boardIds,
                                        mode = BoardImportMerger.Mode.REPLACE,
                                    )

                                    preview = null
                                    showJsonReplaceConfirmation = false
                                    message = app.getString(R.string.backup_config_replace_success)
                                } catch (e: Exception) {
                                    message = app.getString(R.string.backup_config_replace_error, e.message)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.backup_replace_button))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showJsonReplaceConfirmation = false
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    fullPreview?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!busy) fullPreview = null },
            title = { Text(stringResource(R.string.backup_import_boards)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.backup_zip_summary,
                            candidate.boardCount,
                            candidate.soundCount,
                            candidate.audioCount,
                        ),
                    )
                    Text(
                        stringResource(R.string.backup_select_import_boards),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Column(
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
                                Text(stringResource(R.string.backup_board_sound_count, board.name, board.sounds.size))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.backup_import_mode),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = importMode == BoardImportMerger.Mode.ADD,
                            onClick = { importMode = BoardImportMerger.Mode.ADD },
                            enabled = !busy,
                        )
                        Text(stringResource(R.string.backup_zip_add_option))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = importMode == BoardImportMerger.Mode.REPLACE,
                            onClick = { importMode = BoardImportMerger.Mode.REPLACE },
                            enabled = !busy,
                        )
                        Text(stringResource(R.string.backup_zip_replace_option))
                    }
                    Text(
                        if (importMode == BoardImportMerger.Mode.ADD) {
                            stringResource(R.string.backup_zip_add_description)
                        } else {
                            stringResource(R.string.backup_zip_replace_description)
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
                            stringResource(R.string.backup_external_storage_description)
                        } else {
                            stringResource(R.string.backup_internal_storage_description)
                        },
                    )
                    Text(stringResource(R.string.backup_zip_recovery_notice))

                    Text(
                        stringResource(R.string.backup_audio_storage_location),
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
                        Text(stringResource(R.string.backup_internal_storage_option))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = useExternalStorage,
                            onClick = { useExternalStorage = true },
                            enabled = !busy,
                        )
                        Text(stringResource(R.string.backup_external_storage_option))
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
                ) { Text(stringResource(R.string.backup_restore_button)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { fullPreview = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) showReplaceConfirmation = false
            },
            title = {
                Text(stringResource(R.string.backup_zip_replace_title))
            },
            text = {
                Text(
                    stringResource(R.string.backup_zip_replace_confirmation),
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
                    Text(stringResource(R.string.backup_replace_button))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showReplaceConfirmation = false
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(stringResource(R.string.nav_backup_restore)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = { message = null },
                ) { Text(stringResource(R.string.backup_ok_button)) }
            },
        )
    }
}
