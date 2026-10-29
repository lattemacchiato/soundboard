package de.lattemacchiato.soundboardng.ui.components

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
    val defaultRowHeightLevel by app.repository.defaultRowHeightLevel
        .collectAsState(initial = 1)
    var rowHeightSlider by remember {
        mutableFloatStateOf(defaultRowHeightLevel.toFloat())
    }

    LaunchedEffect(defaultRowHeightLevel) {
        rowHeightSlider = defaultRowHeightLevel.toFloat()
    }

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

            FadeSettingSlider(
                label = "Fade-in",
                durationMs = fadeIn,
                enabled = !locked,
                onDurationChange = { duration ->
                    scope.launch { app.repository.setDefaultFade(true, duration) }
                },
            )

            FadeSettingSlider(
                label = "Fade-out",
                durationMs = fadeOut,
                enabled = !locked,
                onDurationChange = { duration ->
                    scope.launch { app.repository.setDefaultFade(false, duration) }
                },
            )

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

            Text(
                text = "Default sound button size",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Level ${rowHeightSlider.toInt().coerceIn(1, 5)} of 5",
                style = MaterialTheme.typography.labelMedium,
            )

            Slider(
                value = rowHeightSlider,
                onValueChange = { value ->
                    rowHeightSlider = value
                },
                onValueChangeFinished = {
                    if (!locked) {
                        val level = rowHeightSlider.toInt().coerceIn(1, 5)
                        scope.launch {
                            app.repository.setDefaultRowHeightLevel(level)
                        }
                    }
                },
                valueRange = 1f..5f,
                steps = 3,
                enabled = !locked,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SettingsSectionTitle("Backup & Restore")
            OutlinedButton(onClick = onOpenBackup, enabled = !locked) {
                Text("Open Backup & Restore")
            }
        }
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
private fun FadeSettingSlider(
    label: String,
    durationMs: Int,
    enabled: Boolean,
    onDurationChange: (Int) -> Unit,
) {
    var sliderValue by remember {
        mutableFloatStateOf(durationMs.toFloat())
    }

    LaunchedEffect(durationMs) {
        sliderValue = durationMs.toFloat()
    }

    Column {
        Text(
            text = "$label: ${formatFade(sliderValue.toInt())}",
            style = MaterialTheme.typography.bodyMedium,
        )

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val duration = ((sliderValue / 500f).toInt() * 500)
                    .coerceIn(0, 5000)
                onDurationChange(duration)
            },
            valueRange = 0f..5000f,
            steps = 9,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatFade(durationMs: Int): String =
    if (durationMs == 0) "Off" else "%.1f s".format(Locale.US, durationMs / 1000f)
