package de.lattemacchiato.soundboardng.ui.components

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.lattemacchiato.soundboardng.R
import de.lattemacchiato.soundboardng.SoundboardApp
import de.lattemacchiato.soundboardng.audio.PlaybackService
import de.lattemacchiato.soundboardng.audio.PlaybackStatus
import de.lattemacchiato.soundboardng.ui.navigation.AppNavHost
import de.lattemacchiato.soundboardng.ui.navigation.Destination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as SoundboardApp
    val locked by app.lockSettings.locked.collectAsState()
    val boards by app.repository.boards.collectAsState(initial = emptyList())
    val activeBoardId by app.repository.activeBoardId.collectAsState(initial = "default")
    val allowSimultaneousPlayback by app.repository.allowSimultaneousPlayback.collectAsState(initial = true)

    LaunchedEffect(allowSimultaneousPlayback) {
        app.engine.setAllowSimultaneousPlayback(allowSimultaneousPlayback)
    }
    var editor by remember { mutableStateOf<BoardEditor?>(null) }
    var deleteBoardId by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var boardSelectorOpenedFromTitle by remember { mutableStateOf(false) }
    val playbackStatuses by app.engine.statuses.collectAsState()

    fun exitAndStop() {
        // release() cancels fades and releases paused AND playing players.
        // Do not call releaseAll(): it permanently disables this Application engine.
        app.engine.statuses.value.keys
            .toList()
            .forEach(app.engine::release)
        context.stopService(Intent(context, PlaybackService::class.java))
        activity?.finish()
    }

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
            if (navController.currentDestination?.route in
                listOf(
                    Destination.BoardSelector.route,
                    Destination.BoardManagement.route,
                    Destination.Settings.route,
                    Destination.BackupRestore.route,
                )
            ) {
                navController.navigate(Destination.Soundboard.route) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    fun openDestination(destination: Destination) {
        if (locked && destination !in listOf(Destination.About, Destination.Licenses)) return

        if (destination == Destination.BoardSelector) {
            boardSelectorOpenedFromTitle = false
        }

        scope.launch { drawerState.close() }
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
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
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("Choose Board")
                            Text(
                                text =
                                    boards.firstOrNull { it.id == activeBoardId }?.name
                                        ?: "No board selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    badge = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    selected = false,
                    onClick = { openDestination(Destination.BoardSelector) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Manage board") },
                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                    selected = false,
                    onClick = { openDestination(Destination.BoardManagement) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = false,
                    onClick = { openDestination(Destination.Settings) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Backup & Restore") },
                    icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                    selected = false,
                    onClick = { openDestination(Destination.BackupRestore) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                listOf(Destination.About, Destination.Licenses).forEach { destination ->
                    NavigationDrawerItem(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            openDestination(destination)
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
            onChooseBoard = {
                if (!locked) {
                    boardSelectorOpenedFromTitle = true
                    navController.navigate(Destination.BoardSelector.route) {
                        launchSingleTop = true
                    }
                }
            },
            onSelectBoard = { id ->
                scope.launch {
                    switchToBoard(id)
                    navController.popBackStack()
                }
            },
            onCreateBoard = { if (!locked) editor = BoardEditor.Create },
            onRenameBoard = {
                if (!locked) {
                    editor =
                        BoardEditor.Rename(activeBoardId, boards.firstOrNull { it.id == activeBoardId }?.name.orEmpty())
                }
            },
            onDeleteBoard = { if (!locked && boards.size > 1) deleteBoardId = activeBoardId },
            onExitRequested = {
                if (playbackStatuses.values.any { it == PlaybackStatus.PLAYING || it == PlaybackStatus.PAUSED }) {
                    confirmExit = true
                } else {
                    exitAndStop()
                }
            },
            onReturnToDrawer = {
                val fromTitle =
                    currentRoute == Destination.BoardSelector.route &&
                        boardSelectorOpenedFromTitle

                if (navController.popBackStack() && !fromTitle) {
                    scope.launch { drawerState.open() }
                }

                boardSelectorOpenedFromTitle = false
            },
        )
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Exit Soundboard?") },
            text = { Text("One or more sounds are playing or paused. Exiting will stop all playback.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    exitAndStop()
                }) { Text("Exit & Stop All") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Cancel") }
            },
        )
    }

    if (!locked) {
        editor?.let { current ->
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
                                            if (id != null) {
                                                switchToBoard(id)
                                                if (navController.currentDestination?.route ==
                                                    Destination.BoardSelector.route
                                                ) {
                                                    navController.popBackStack()
                                                }
                                            }
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
    }
    if (!locked) {
        deleteBoardId?.let { id ->
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

                                navController.navigate(Destination.Soundboard.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }

                                drawerState.open()
                            }
                        }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteBoardId = null }) { Text("Cancel") } },
            )
        }
    }
}

private sealed interface BoardEditor {
    data object Create : BoardEditor

    data class Rename(
        val id: String,
        val name: String,
    ) : BoardEditor
}
