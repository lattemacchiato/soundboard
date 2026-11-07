package de.lattemacchiato.soundboardng

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import de.lattemacchiato.soundboardng.data.ThemeMode
import de.lattemacchiato.soundboardng.ui.components.AppScaffold
import de.lattemacchiato.soundboardng.ui.theme.SoundboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as SoundboardApp
            val themeMode by app.repository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val dark =
                when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> systemDark
                }
            SoundboardTheme(darkTheme = dark) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    // Also updates system-bar icon contrast on Android 8.1.
                    if (dark) {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.dark(background),
                            navigationBarStyle = SystemBarStyle.dark(background),
                        )
                    } else {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.light(background, background),
                            navigationBarStyle = SystemBarStyle.light(background, background),
                        )
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RequestNotificationPermission()
                    AppScaffold()
                }
            }
        }
    }
}

/**
 * Explains notification permission once before requesting it on Android 13+.
 * The decision is remembered even when the user declines.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val preferences =
        remember(context) {
            context.getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
        }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val showExplanation =
        remember {
            mutableStateOf(
                shouldExplainNotificationPermission(
                    apiLevel = Build.VERSION.SDK_INT,
                    permissionGranted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED,
                    explanationShown = preferences.getBoolean("explanation_shown", false),
                ),
            )
        }

    if (showExplanation.value) {
        AlertDialog(
            onDismissRequest = {
                preferences.edit().putBoolean("explanation_shown", true).apply()
                showExplanation.value = false
            },
            title = {
                Text(stringResource(R.string.notification_permission_title))
            },
            text = {
                Text(stringResource(R.string.notification_permission_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferences.edit().putBoolean("explanation_shown", true).apply()
                        showExplanation.value = false
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                ) {
                    Text(stringResource(R.string.notification_permission_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        preferences.edit().putBoolean("explanation_shown", true).apply()
                        showExplanation.value = false
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
