package com.joseph.jarvis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val hasAccess = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fx.init(this)
        setContent {
            JarvisTheme {
                App(hasAccess = hasAccess.value, requestAccess = ::openAccessSettings)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-checked every time you come back, e.g. after granting access in Settings.
        hasAccess.value = Environment.isExternalStorageManager()
    }

    private fun openAccessSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

/** Cyan-on-black for every built-in control (buttons, checkboxes, text fields) to match the HUD. */
@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Hud.Cyan,
        onPrimary = Hud.Bg,
        secondary = Hud.CyanDim,
        background = Hud.Bg,
        onBackground = Hud.Text,
        surface = Hud.BgLight,
        onSurface = Hud.Text,
        surfaceVariant = Hud.BgLight,
        onSurfaceVariant = Hud.Text.copy(alpha = 0.7f),
        outline = Hud.CyanDim,
        outlineVariant = Hud.CyanFaint,
        error = Hud.Red,
        surfaceContainerHigh = Hud.BgLight,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(hasAccess: Boolean, requestAccess: () -> Unit) {
    val ctx = LocalContext.current
    var dest by remember { mutableStateOf<Dest?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var booted by remember { mutableStateOf(false) }
    val setNotice: (String) -> Unit = { notice = it }
    val voice = rememberVoiceSession(onOpen = { dest = it })

    // Tidy the archive each time the app opens.
    LaunchedEffect(hasAccess) {
        if (hasAccess) {
            val n = withContext(Dispatchers.IO) { Archive.purgeOld(ctx) }
            if (n > 0) notice = "Removed $n archived item(s) older than ${Archive.KEEP_DAYS} days."
        }
    }

    // Phone back button returns to the core.
    BackHandler(enabled = dest != null) { dest = null }

    Box(Modifier.fillMaxSize()) {
        HudBackground()
        // Transparent surface so the background shows through, but all text defaults to HUD colour.
        Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = Hud.Text) {
        if (!booted) {
            BootSequence(onDone = { booted = true })
        } else if (!hasAccess) {
            AccessScreen(requestAccess)
        } else when (val d = dest) {
            null -> HomeScreen(notice, voice) { dest = it }
            else -> HudScreen(d.label, onBack = { dest = null }) {
                Column(Modifier.fillMaxSize()) {
                    notice?.let {
                        Text(
                            "› $it",
                            color = Hud.Cyan, fontFamily = Hud.Mono, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    when (d) {
                        Dest.Music -> MusicScreen(setNotice)
                        Dest.Apps -> AppsScreen(setNotice)
                        Dest.Cleanup -> CleanupScreen(setNotice)
                        Dest.Archive -> ArchiveScreen(setNotice)
                        Dest.Settings -> SettingsScreen()
                    }
                }
            }
        }
        }
    }
}
