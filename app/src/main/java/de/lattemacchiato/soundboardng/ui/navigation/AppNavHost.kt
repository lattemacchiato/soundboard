package de.lattemacchiato.soundboardng.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import de.lattemacchiato.soundboardng.SoundboardApp
import de.lattemacchiato.soundboardng.ui.components.BoardSelectorScreen
import de.lattemacchiato.soundboardng.ui.components.BoardManagementScreen
import de.lattemacchiato.soundboardng.ui.components.SettingsScreen
import de.lattemacchiato.soundboardng.ui.components.BackupRestoreScreen
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.lattemacchiato.soundboardng.ui.SoundboardScreen
import de.lattemacchiato.soundboardng.ui.about.AboutScreen
import de.lattemacchiato.soundboardng.ui.about.LicensesScreen
import kotlinx.coroutines.launch

/**
 * Three flat destinations, each owning its own `Scaffold`.
 *
 * Unlike the sibling app, the top bar is not hoisted into `AppScaffold`: the
 * soundboard needs a FAB and a "stop all" action that the other two screens
 * have no use for, so hoisting would mean threading its ViewModel up past
 * screens that don't want it. Each screen instead receives [onOpenDrawer] and
 * renders its own bar.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    onSelectBoard: (String) -> Unit,
    onCreateBoard: () -> Unit,
    onRenameBoard: () -> Unit,
    onDeleteBoard: () -> Unit,
    onChooseBoard: () -> Unit,
    onReturnToDrawer: () -> Unit,
    onOpenBackup: () -> Unit,
    onExitRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as SoundboardApp
    val boards by app.repository.boards.collectAsState(initial = emptyList())
    val activeBoardId by app.repository.activeBoardId.collectAsState(initial = "default")

    val defaultRowHeightLevel by app.repository.defaultRowHeightLevel
        .collectAsState(initial = 1)

    val scope = rememberCoroutineScope()

    val locked by app.lockSettings.locked.collectAsState()
    NavHost(
        navController = navController,
        startDestination = Destination.Soundboard.route,
        modifier = modifier,
    ) {
        composable(Destination.Soundboard.route) {
            BackHandler(onBack = onExitRequested)
            SoundboardScreen(
                onOpenDrawer = onOpenDrawer,
                onChooseBoard = onChooseBoard,
            )
        }
        composable(Destination.About.route) {
            BackHandler(onBack = onReturnToDrawer)
            AboutScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenLicenses = { navController.navigate(Destination.Licenses.route) },
            )
        }
        composable(Destination.Licenses.route) {
            BackHandler(onBack = onReturnToDrawer)
            LicensesScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Destination.BoardSelector.route) {
            BackHandler(onBack = onReturnToDrawer)
            BoardSelectorScreen(boards, activeBoardId, locked, onSelectBoard, onCreateBoard, onBack = onReturnToDrawer)
        }
        composable(Destination.BoardManagement.route) {
            BackHandler(onBack = onReturnToDrawer)
            BoardManagementScreen(
                boards = boards,
                activeBoardId = activeBoardId,
                locked = locked,
                onCreate = onCreateBoard,
                onRename = onRenameBoard,
                onDelete = onDeleteBoard,
                onBack = onReturnToDrawer,
                defaultRowHeightLevel = defaultRowHeightLevel,
                onRowHeightLevelChange = { level ->
                    if (!locked) {
                        scope.launch {
                            app.repository.setBoardRowHeightLevel(activeBoardId, level)
                        }
                    }
                },
            )
        }
        composable(Destination.BackupRestore.route) {
            BackupRestoreScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.Settings.route) {
            BackHandler(onBack = onReturnToDrawer)
            SettingsScreen(onBack = onReturnToDrawer, onOpenBackup = onOpenBackup)
        }
    }
}
