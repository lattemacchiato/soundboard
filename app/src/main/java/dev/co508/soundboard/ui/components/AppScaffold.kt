package dev.co508.soundboard.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.co508.soundboard.SoundboardApp
import dev.co508.soundboard.data.TapBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.co508.soundboard.R
import dev.co508.soundboard.ui.navigation.AppNavHost
import dev.co508.soundboard.ui.navigation.Destination
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Hosts the navigation drawer and the nav graph.
 *
 * The drawer is the way back to the soundboard from anywhere; system back also
 * works, because every destination is a real nav entry rather than a swapped
 * piece of local state.
 */
@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as SoundboardApp
    val locked by app.lockSettings.locked.collectAsState()
    val boards by app.repository.boards.collectAsState(initial = emptyList())
    val activeBoardId by app.repository.activeBoardId.collectAsState(initial = "default")
    val defaultTapBehavior by app.repository.defaultTapBehavior.collectAsState(initial = TapBehavior.PAUSE_RESUME)
    val defaultFadeInMs by app.repository.defaultFadeInMs.collectAsState(initial = 0)
    val defaultFadeOutMs by app.repository.defaultFadeOutMs.collectAsState(initial = 0)
    val allowSimultaneousPlayback by app.repository.allowSimultaneousPlayback.collectAsState(initial = true)

    LaunchedEffect(allowSimultaneousPlayback) {
        app.engine.setAllowSimultaneousPlayback(allowSimultaneousPlayback)
    }
    var editor by remember { mutableStateOf<BoardEditor?>(null) }
    var deleteBoardId by remember { mutableStateOf<String?>(null) }

    // Stop and release only the old board's players. releaseAll() would make
    // the shared engine unusable for the next board.
    suspend fun switchToBoard(id: String) {
        if (locked || id == activeBoardId) return
        val oldSounds = app.repository.sounds.first()
        app.engine.pauseAll()
        oldSounds.forEach { app.engine.release(it.id) }
        app.repository.selectBoard(id)
    }


    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(locked) {
        if (locked) {
            drawerState.close()
            editor = null
            deleteBoardId = null
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !locked,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Text(
                    text = "Soundboards",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
                boards.forEach { board ->
                    NavigationDrawerItem(
                        label = { Text(board.name) },
                        selected = activeBoardId == board.id,
                        onClick = {
                            if (!locked) scope.launch {
                                switchToBoard(board.id)
                                drawerState.close()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                TextButton(
                    onClick = { if (!locked) editor = BoardEditor.Create },
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) { Text("+ New board") }
                TextButton(
                    onClick = { if (!locked) editor = BoardEditor.Rename(activeBoardId, boards.firstOrNull { it.id == activeBoardId }?.name.orEmpty()) },
                    enabled = boards.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) { Text("Rename current board") }
                TextButton(
                    onClick = { if (!locked && boards.size > 1) deleteBoardId = activeBoardId },
                    enabled = boards.size > 1,
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) { Text("Delete current board") }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Text(
                    text = "Default tap behavior for new sounds",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
                listOf(
                    TapBehavior.PAUSE_RESUME to "Pause / Resume",
                    TapBehavior.STOP_RESTART to "Stop / Restart",
                ).forEach { (behavior, label) ->
                    NavigationDrawerItem(
                        label = { Text(label) },
                        selected = defaultTapBehavior == behavior,
                        onClick = {
                            if (!locked) scope.launch {
                                app.repository.setDefaultTapBehavior(behavior)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                DefaultFadeSlider("Default fade-in", defaultFadeInMs) { ms ->
                    if (!locked) scope.launch { app.repository.setDefaultFade(true, ms) }
                }
                DefaultFadeSlider("Default fade-out", defaultFadeOutMs) { ms ->
                    if (!locked) scope.launch { app.repository.setDefaultFade(false, ms) }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Allow simultaneous playback", modifier = Modifier.weight(1f))
                    Switch(
                        checked = allowSimultaneousPlayback,
                        onCheckedChange = { allow ->
                            if (!locked) scope.launch {
                                app.engine.setAllowSimultaneousPlayback(allow)
                                app.repository.setAllowSimultaneousPlayback(allow)
                            }
                        },
                        enabled = !locked,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Destination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(destination.route) {
                                // Keep a single entry per destination so the drawer
                                // can't stack ten copies of About behind each other.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        AppNavHost(
            navController = navController,
            onOpenDrawer = { if (!locked) scope.launch { drawerState.open() } },
        )
    }

    if (!locked) editor?.let { current ->
        var name by remember(current) {
            mutableStateOf(if (current is BoardEditor.Rename) current.name else "")
        }
        AlertDialog(
            onDismissRequest = { editor = null },
            title = { Text(if (current is BoardEditor.Create) "New board" else "Rename board") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Board name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        editor = null
                        scope.launch {
                            if (!app.lockSettings.locked.value) {
                                when (current) {
                                    is BoardEditor.Create -> {
                                        val id = app.repository.createBoard(name)
                                        if (id != null) switchToBoard(id)
                                    }
                                    is BoardEditor.Rename -> app.repository.renameBoard(current.id, name)
                                }
                                drawerState.close()
                            }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editor = null }) { Text("Cancel") } },
        )
    }
    if (!locked) deleteBoardId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteBoardId = null },
            title = { Text("Delete board?") },
            text = { Text("The board and its sound entries will be removed. Audio files will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteBoardId = null
                    scope.launch {
                        if (!app.lockSettings.locked.value && boards.size > 1) {
                            val oldSounds = app.repository.sounds.first()
                            app.engine.pauseAll()
                            oldSounds.forEach { app.engine.release(it.id) }
                            app.repository.deleteBoard(id)
                            drawerState.close()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteBoardId = null }) { Text("Cancel") } },
        )
    }
}

private sealed interface BoardEditor {
    data object Create : BoardEditor
    data class Rename(val id: String, val name: String) : BoardEditor
}

@Composable
private fun DefaultFadeSlider(label: String, durationMs: Int, onCommit: (Int) -> Unit) {
    var value by remember(durationMs) { mutableStateOf(durationMs.toFloat()) }
    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)) {
        Text("$label: " + if (value < 250f) "Off" else "%.1f s".format(java.util.Locale.US, value / 1000f))
        Slider(
            value = value,
            onValueChange = { value = (it / 500f).toInt().coerceIn(0, 10) * 500f },
            onValueChangeFinished = { onCommit(value.toInt()) },
            valueRange = 0f..5000f,
            steps = 9,
        )
    }
}
