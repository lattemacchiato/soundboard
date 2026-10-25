package dev.co508.soundboard.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.co508.soundboard.SoundboardApp
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
            if (navController.currentDestination?.route in listOf(
                    Destination.BoardSelector.route,
                    Destination.BoardManagement.route,
                    Destination.Settings.route,
                    Destination.BackupRestore.route,
                )) {
                navController.navigate(Destination.Soundboard.route) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    fun openDestination(destination: Destination) {
        if (locked && destination !in listOf(Destination.About, Destination.Licenses)) return
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
                Text(
                    text = "CURRENT BOARD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
                )
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(boards.firstOrNull { it.id == activeBoardId }?.name ?: "No board selected")
                            Text(
                                "Choose board",
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
                    label = { Text("Manage boards") },
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
            onSelectBoard = { id ->
                scope.launch {
                    switchToBoard(id)
                    navController.popBackStack()
                }
            },
            onCreateBoard = { if (!locked) editor = BoardEditor.Create },
            onRenameBoard = { if (!locked) editor = BoardEditor.Rename(activeBoardId, boards.firstOrNull { it.id == activeBoardId }?.name.orEmpty()) },
            onDeleteBoard = { if (!locked && boards.size > 1) deleteBoardId = activeBoardId },
            onOpenBackup = { if (!locked) navController.navigate(Destination.BackupRestore.route) },
            onReturnToDrawer = {
                if (navController.popBackStack()) {
                    scope.launch { drawerState.open() }
                }
            },
        )
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
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
                                        if (id != null) {
                                            switchToBoard(id)
                                            if (navController.currentDestination?.route == Destination.BoardSelector.route) {
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

