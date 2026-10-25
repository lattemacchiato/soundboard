package de.lattemacchiato.soundboardng.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.lattemacchiato.soundboardng.SoundboardApp
import de.lattemacchiato.soundboardng.data.TapBehavior
import de.lattemacchiato.soundboardng.data.ThemeMode
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenBackup: () -> Unit) {
    val app = LocalContext.current.applicationContext as SoundboardApp
    val scope = rememberCoroutineScope()
    val locked by app.lockSettings.locked.collectAsState()
    val tap by app.repository.defaultTapBehavior.collectAsState(initial = TapBehavior.PAUSE_RESUME)
    val fadeIn by app.repository.defaultFadeInMs.collectAsState(initial = 0)
    val fadeOut by app.repository.defaultFadeOutMs.collectAsState(initial = 0)
    val simultaneous by app.repository.allowSimultaneousPlayback.collectAsState(initial = true)
    val theme by app.repository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    var editingFadeIn by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSectionTitle("Default tap behavior for new sounds")
            var tapMenuExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = tapMenuExpanded,
                onExpandedChange = { if (!locked) tapMenuExpanded = !tapMenuExpanded },
            ) {
                OutlinedTextField(
                    value = when (tap) {
                        TapBehavior.PAUSE_RESUME -> "Pause / Resume"
                        TapBehavior.STOP_RESTART -> "Stop / Restart"
                    },
                    onValueChange = {},
                    readOnly = true,
                    enabled = !locked,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tapMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = tapMenuExpanded,
                    onDismissRequest = { tapMenuExpanded = false },
                ) {
                    listOf(
                        TapBehavior.PAUSE_RESUME to "Pause / Resume",
                        TapBehavior.STOP_RESTART to "Stop / Restart",
                    ).forEach { (behavior, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                tapMenuExpanded = false
                                if (!locked) scope.launch { app.repository.setDefaultTapBehavior(behavior) }
                            },
                        )
                    }
                }
            }

            HorizontalDivider()
            SettingsSectionTitle("Playback")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Allow simultaneous playback", modifier = Modifier.weight(1f))
                Switch(checked = simultaneous, enabled = !locked, onCheckedChange = { allow ->
                    scope.launch {
                        app.engine.setAllowSimultaneousPlayback(allow)
                        app.repository.setAllowSimultaneousPlayback(allow)
                    }
                })
            }

            HorizontalDivider()
            SettingsSectionTitle("Default fade for new sounds")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FadeSettingCard("Fade-in", fadeIn, !locked, Modifier.weight(1f)) { editingFadeIn = true }
                FadeSettingCard("Fade-out", fadeOut, !locked, Modifier.weight(1f)) { editingFadeIn = false }
            }

            HorizontalDivider()
            SettingsSectionTitle("Appearance")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                ).forEach { (mode, label) ->
                    val selected = theme == mode
                    if (selected) {
                        Button(
                            onClick = { if (!locked) scope.launch { app.repository.setThemeMode(mode) } },
                            enabled = !locked,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text(label, maxLines = 1) }
                    } else {
                        OutlinedButton(
                            onClick = { if (!locked) scope.launch { app.repository.setThemeMode(mode) } },
                            enabled = !locked,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }
            HorizontalDivider()
            SettingsSectionTitle("Backup & Restore")
            OutlinedButton(onClick = onOpenBackup, enabled = !locked) {
                Text("Open Backup & Restore")
            }
        }
    }

    val fadeBeingEdited = editingFadeIn
    if (fadeBeingEdited != null && !locked) {
        FadeSettingDialog(
            label = if (fadeBeingEdited) "Fade-in" else "Fade-out",
            durationMs = if (fadeBeingEdited) fadeIn else fadeOut,
            onDismiss = { editingFadeIn = null },
            onConfirm = { duration ->
                editingFadeIn = null
                scope.launch { app.repository.setDefaultFade(fadeBeingEdited, duration) }
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FadeSettingCard(
    label: String,
    durationMs: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(formatFade(durationMs), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FadeSettingDialog(
    label: String,
    durationMs: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(durationMs) { mutableFloatStateOf(durationMs.coerceIn(0, 5000).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                Text(formatFade(value.toInt()), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = value,
                    onValueChange = { value = (it / 500f).toInt().coerceIn(0, 10) * 500f },
                    valueRange = 0f..5000f,
                    steps = 9,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.toInt()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatFade(durationMs: Int): String =
    if (durationMs == 0) "Off" else "%.1f s".format(Locale.US, durationMs / 1000f)
