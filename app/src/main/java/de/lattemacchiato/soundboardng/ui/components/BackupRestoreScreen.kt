package de.lattemacchiato.soundboardng.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.lattemacchiato.soundboardng.SoundboardApp
import de.lattemacchiato.soundboardng.data.BackupPreview
import de.lattemacchiato.soundboardng.data.ConfigurationBackup
import de.lattemacchiato.soundboardng.data.FullBackup
import de.lattemacchiato.soundboardng.data.FullBackupPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SoundboardApp
    val backup = remember(app) { ConfigurationBackup(app, app.repository) }
    val fullBackup = remember(app) { FullBackup(app, app.repository) }
    val scope = rememberCoroutineScope()
    val locked by app.lockSettings.locked.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var fullPreview by remember { mutableStateOf<FullBackupPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && !locked) scope.launch {
            busy = true
            try { backup.export(uri); message = "Configuration exported successfully." }
            catch (e: Exception) { message = "Export failed: ${e.message}" }
            finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !locked) scope.launch {
            busy = true
            try { preview = backup.inspect(uri) }
            catch (e: Exception) { message = "Import failed: ${e.message}" }
            finally { busy = false }
        }
    }

    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && !locked) scope.launch {
            busy = true
            try { fullBackup.export(uri); message = "Full backup exported successfully." }
            catch (e: Exception) { message = "ZIP export failed: ${e.message}" }
            finally { busy = false }
        }
    }
    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !locked) scope.launch {
            busy = true
            try { fullPreview = fullBackup.inspect(uri) }
            catch (e: Exception) { message = "ZIP import failed: ${e.message}" }
            finally { busy = false }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Backup & Restore") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Export configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("Save all boards, sound settings and global preferences as JSON. Audio files are not included.")
            Button(enabled = !locked && !busy, onClick = { export.launch("soundboard-configuration.json") }) {
                Text("Export JSON")
            }
            HorizontalDivider()
            Text("Import configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("Replace the current configuration from a JSON backup. Audio file references must still be accessible on this device.")
            OutlinedButton(enabled = !locked && !busy, onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text("Choose JSON backup…")
            }
            HorizontalDivider()
            Text("Full backup (ZIP)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("Includes all boards, settings and audio files. Suitable for transferring to another device.")
            Button(enabled = !locked && !busy, onClick = { exportZip.launch("soundboard-full-backup.zip") }) {
                Text("Export full ZIP")
            }
            OutlinedButton(enabled = !locked && !busy, onClick = {
                importZip.launch(arrayOf("application/zip", "application/octet-stream"))
            }) { Text("Restore full ZIP") }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    preview?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text("Replace current configuration?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup contains ${candidate.boardCount} board(s) and ${candidate.soundCount} sound(s). All current boards and settings will be replaced.")
                    if (candidate.unavailableSounds.isNotEmpty()) {
                        Text("Warning: ${candidate.unavailableSounds.size} audio reference(s) are unavailable on this device. These sounds will not play until relinked.", color = MaterialTheme.colorScheme.error)
                        Text(candidate.unavailableSounds.take(5).joinToString(", ") + if (candidate.unavailableSounds.size > 5) "…" else "")
                    }
                    Text("A recovery copy of the previous configuration will be saved in private app storage before importing.")
                }
            },
            confirmButton = {
                TextButton(enabled = !locked && !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            app.engine.pauseAll()
                            app.repository.sounds.first().forEach { app.engine.release(it.id) }
                            backup.restore(candidate)
                            preview = null
                            message = "Configuration imported successfully."
                        } catch (e: Exception) { message = "Import failed: ${e.message}" }
                        finally { busy = false }
                    }
                }) { Text("Replace") }
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
                    Text("${candidate.boardCount} board(s), ${candidate.soundCount} sound(s), " +
                        "${candidate.audioCount} audio file(s).")
                    Text("All current boards and settings will be replaced. Audio will be copied into private app storage.")
                    Text("A recovery copy of the previous configuration will be saved.")
                }
            },
            confirmButton = {
                TextButton(enabled = !locked && !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            app.engine.pauseAll()
                            app.repository.sounds.first().forEach { app.engine.release(it.id) }
                            fullBackup.restore(candidate)
                            fullPreview = null
                            message = "Full backup restored successfully."
                        } catch (e: Exception) { message = "ZIP restore failed: ${e.message}" }
                        finally { busy = false }
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { fullPreview = null }) { Text("Cancel") }
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
