package com.joseph.jarvis

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- music

@Composable
fun MusicScreen(onNotice: (String) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    var musicApp by remember { mutableStateOf<AppInfo?>(null) }

    LaunchedEffect(Unit) {
        musicApp = withContext(Dispatchers.IO) {
            val pkg = Prefs.musicApp(ctx)
            Apps.launchable(ctx).firstOrNull { it.packageName == pkg }
        }
        tracks = withContext(Dispatchers.IO) { Music.all(ctx) }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Song, artist or album") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        musicApp?.let { app ->
            HudButton(
                if (query.isBlank()) "Open ${app.label}" else "Play \"${query.trim()}\" in ${app.label}",
                onClick = {
                    if (!Apps.playInApp(ctx, app.packageName, query.trim())) onNotice("Couldn't open ${app.label}.")
                },
                filled = query.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }

        val list = tracks
        if (list == null) {
            RadarScan("Indexing music", "reading library…")
        } else {
            val shown = list.filter { Music.matches(it, query) }
            HudSection("On this phone · ${shown.size} of ${list.size}")
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(shown, key = { _, it -> it.id }) { i, t ->
                    HudCard(index = i, onClick = { if (!Music.play(ctx, t)) onNotice("No app on the phone can play that file.") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t.title, color = Hud.Text, fontSize = 15.sp, maxLines = 1)
                                HudData(
                                    listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString("  ·  "),
                                    color = Hud.Text.copy(alpha = 0.6f), size = 11.dp,
                                )
                            }
                            HudData(Music.fmtDuration(t.durationMs), color = Hud.Cyan, modifier = Modifier.padding(start = 8.dp))
                            HudLabel("▶", modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- apps

@Composable
fun AppsScreen(onNotice: (String) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { Apps.launchable(ctx) } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        val list = apps
        if (list == null) {
            RadarScan("Reading app registry", "")
        } else {
            val shown = list.filter { it.label.contains(query.trim(), ignoreCase = true) }
            HudSection("Installed · ${shown.size}")
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            ) {
                items(shown.size, key = { shown[it].packageName }) { i ->
                    val app = shown[i]
                    AppTile(app, i) { if (!Apps.open(ctx, app.packageName)) onNotice("Couldn't open ${app.label}.") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- settings: music app picker

@Composable
fun MusicAppPicker() {
    val ctx = LocalContext.current
    var options by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var capable by remember { mutableStateOf<Set<String>>(emptySet()) }
    var current by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        capable = withContext(Dispatchers.IO) { Apps.musicApps(ctx).map { it.packageName }.toSet() }
        options = withContext(Dispatchers.IO) { Apps.launchable(ctx) }
        current = withContext(Dispatchers.IO) { Prefs.musicApp(ctx) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Music app for \"Play\" requests", style = MaterialTheme.typography.titleSmall)
        Text(
            "Apps marked ✓ can search and play directly. Others will just open.",
            style = MaterialTheme.typography.bodySmall,
        )
        val shown = if (showAll) options
            else options.filter { it.packageName in capable || it.packageName == current || it.label.contains("tube", true) }
        shown.forEach { app ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    current = app.packageName
                    Prefs.setMusicApp(ctx, app.packageName)
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(
                    selected = current == app.packageName,
                    onClick = {
                        current = app.packageName
                        Prefs.setMusicApp(ctx, app.packageName)
                    },
                )
                Text(if (app.packageName in capable) "${app.label} ✓" else app.label)
            }
        }
        androidx.compose.material3.TextButton(onClick = { showAll = !showAll }) {
            Text(if (showAll) "Show music apps only" else "Show all apps")
        }
    }
}

/** An app as a hexagon-framed icon with its name underneath. */
@Composable
private fun AppTile(app: AppInfo, index: Int, onClick: () -> Unit) {
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(16) * 20).toLong())
        appear.animateTo(1f, androidx.compose.animation.core.tween(220))
    }
    Column(
        Modifier
            .padding(4.dp)
            .graphicsLayer { alpha = appear.value; scaleX = 0.8f + 0.2f * appear.value; scaleY = scaleX }
            .clickable { Fx.tap(); onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val r = size.minDimension / 2
                val path = Path()
                for (k in 0 until 6) {
                    val a = Math.toRadians(60.0 * k - 90)
                    val x = center.x + r * kotlin.math.cos(a).toFloat()
                    val y = center.y + r * kotlin.math.sin(a).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, Hud.Cyan.copy(alpha = 0.07f))
                drawPath(path, Hud.CyanDim, style = Stroke(1.5f))
            }
            app.icon?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(34.dp))
            }
        }
        Text(
            app.label, color = Hud.Text, fontSize = 11.sp, maxLines = 1,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
