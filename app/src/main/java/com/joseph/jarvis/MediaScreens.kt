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
            Button(
                onClick = {
                    if (!Apps.playInApp(ctx, app.packageName, query.trim())) onNotice("Couldn't open ${app.label}.")
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(if (query.isBlank()) "Open ${app.label}" else "Play \"${query.trim()}\" in ${app.label}")
            }
        }

        val list = tracks
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val shown = list.filter { Music.matches(it, query) }
            Text(
                "On this phone: ${shown.size} of ${list.size} song(s)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { t ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { if (!Music.play(ctx, t)) onNotice("No app on the phone can play that file.") }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            listOf(t.artist, t.album, Music.fmtDuration(t.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    HorizontalDivider()
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
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val shown = list.filter { it.label.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { if (!Apps.open(ctx, app.packageName)) onNotice("Couldn't open ${app.label}.") }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        app.icon?.let {
                            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
                        } ?: Box(Modifier.size(40.dp))
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    }
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
