package de.lattemacchiato.soundboardng

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import de.lattemacchiato.soundboardng.data.ThemeMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
            val dark = when (themeMode) {
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
 * Asks for POST_NOTIFICATIONS once on first launch (API 33+).
 *
 * Playback still works if the user declines — the foreground service starts
 * either way — but the notification, and with it the "Stop all" control, is
 * silently withheld by the system.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
