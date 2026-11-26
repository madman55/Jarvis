package com.joseph.jarvis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0)
    else -> "%.0f KB".format(b / 1024.0)
}

fun fmtDate(t: Long): String = SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date(t))

// ---------------------------------------------------------------- access

@Composable
fun AccessScreen(requestAccess: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Core(Modifier.size(200.dp)) { HudLabel("Locked", color = Hud.Red) }
        HudLabel("Access required", size = 14.sp)
        Text(
            "So it can find audiobook files left in Downloads and move them to its archive. " +
                "Tap below, switch on \"Allow access to manage all files\", then come back."
        )
        Button(onClick = requestAccess) { Text("Grant access") }
    }
}

// ---------------------------------------------------------------- clean up

@Composable
fun CleanupScreen(onNotice: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Item>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf(Grouping.Relevance) }
    var scanned by remember { mutableIntStateOf(0) }

    fun scan() {
        busy = true
        scope.launch {
            val downloads = File(Prefs.downloads(ctx))
            val lib = Prefs.library(ctx).takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
            scanned = 0
            items = withContext(Dispatchers.IO) { Scanner.scanDownloads(downloads, lib) { scanned = it } }
            selected = emptySet()
            busy = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    val list = items
    val chosen = list.orEmpty().filter { it.file.path in selected }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HudData(
                when {
                    busy -> "SCANNING…"
                    list == null -> ""
                    list.isEmpty() -> "Downloads is clear."
                    else -> "${list.size} audiobooks · ${fmtSize(list.sumOf { it.sizeBytes })}"
                },
                modifier = Modifier.weight(1f),
                size = 14.dp,
            )
            HudButton("Rescan", onClick = { scan() }, enabled = !busy)
        }

        if (busy) {
            RadarScan("Scanning Downloads", "$scanned folders checked")
        } else if (list != null && list.isNotEmpty()) {
            val biggest = list.maxOf { it.sizeBytes }.coerceAtLeast(1)
            GroupChips(group) { group = it }
            val groups = groupItems(list, group)

            LazyColumn(Modifier.weight(1f)) {
                groups.forEach { (title, subtitle, members) ->
                    item(key = "h-$title") {
                        val paths = members.map { it.file.path }
                        HudSection(
                            "$title · ${members.size} · ${fmtSize(members.sumOf { it.sizeBytes })}",
                            subtitle,
                            action = if (selected.containsAll(paths)) "Select none" else "Select all",
                            onAction = {
                                selected = if (selected.containsAll(paths)) selected - paths.toSet() else selected + paths
                            },
                        )
                    }
                    itemsIndexed(members, key = { _, it -> it.file.path }) { idx, item ->
                        ItemRow(item, item.file.path in selected, idx, item.sizeBytes.toFloat() / biggest) {
                            selected = if (item.file.path in selected) selected - item.file.path else selected + item.file.path
                        }
                    }
                }
            }

            HudActionBar {
                HudButton("Clear", onClick = { selected = emptySet() }, enabled = chosen.isNotEmpty())
                HudButton(
                    if (chosen.isEmpty()) "Select to archive" else "Archive ${chosen.size} · ${fmtSize(chosen.sumOf { it.sizeBytes })}",
                    onClick = { confirm = true },
                    enabled = chosen.isNotEmpty(),
                    filled = chosen.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Archive ${chosen.size} item(s)?") },
            text = {
                Text(
                    "They'll move to \"Jarvis Archive\" and be deleted for good after ${Archive.KEEP_DAYS} days. " +
                        "You can restore them from the Archive tab until then."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    scope.launch {
                        val failed = withContext(Dispatchers.IO) { chosen.count { !Archive.archive(ctx, it.file) } }
                        onNotice(
                            if (failed == 0) "Archived ${chosen.size} item(s), ${fmtSize(chosen.sumOf { it.sizeBytes })} freed from Downloads."
                            else "Archived ${chosen.size - failed}, but $failed couldn't be moved."
                        )
                        scan()
                    }
                }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String, onSelectAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onSelectAll) { Text("Select all") }
    }
}

@Composable
fun ItemRow(item: Item, checked: Boolean, index: Int, sizeFraction: Float, onToggle: () -> Unit) {
    HudCard(selected = checked, index = index, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudCheck(checked)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, color = Hud.Text, fontSize = 15.sp, maxLines = 2)
                Row {
                    HudData(fmtSize(item.sizeBytes), color = Hud.Cyan)
                    HudData("  ·  ${item.audioCount} file${if (item.audioCount == 1) "" else "s"}  ·  ${fmtDate(item.lastModified)}",
                        color = Hud.Text.copy(alpha = 0.6f))
                }
                SizeBar(sizeFraction, color = if (item.inLibrary) Hud.Cyan else Color(0xFFFFB347))
                HudData(item.file.parent?.removePrefix("/storage/emulated/0") ?: "", color = Hud.CyanDim, size = 10.dp)
            }
        }
    }
}

// ---------------------------------------------------------------- archive

@Composable
fun ArchiveScreen(onNotice: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            entries = withContext(Dispatchers.IO) { Archive.list() }
            selected = selected.filter { p -> entries.any { it.archived.path == p } }.toSet()
        }
    }
    LaunchedEffect(Unit) { load() }

    val chosen = entries.filter { it.archived.path in selected }
    val allOn = entries.isNotEmpty() && chosen.size == entries.size

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                HudData(
                    if (entries.isEmpty()) "ARCHIVE EMPTY"
                    else "${entries.size} items · ${fmtSize(entries.sumOf { it.sizeBytes })}",
                    size = 14.dp,
                )
                HudData("Auto-purge after ${Archive.KEEP_DAYS} days", color = Hud.CyanDim, size = 10.dp)
            }
            if (entries.isNotEmpty()) {
                HudButton(if (allOn) "None" else "All", onClick = {
                    selected = if (allOn) emptySet() else entries.map { it.archived.path }.toSet()
                })
            }
        }

        if (busy) RadarScan("Working", "${chosen.size} item(s)")

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(entries, key = { _, it -> it.archived.path }) { idx, e ->
                val on = e.archived.path in selected
                val daysLeft = (Archive.KEEP_DAYS - ((System.currentTimeMillis() - e.time) / 86_400_000L).toInt()).coerceAtLeast(0)
                HudCard(selected = on, index = idx, accent = if (daysLeft <= 3) Hud.Red else Hud.Cyan, onClick = {
                    selected = if (on) selected - e.archived.path else selected + e.archived.path
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HudCheck(on)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(e.archived.name, color = Hud.Text, fontSize = 15.sp, maxLines = 2)
                            Row {
                                HudData(fmtSize(e.sizeBytes), color = Hud.Cyan)
                                HudData("  ·  archived ${fmtDate(e.time)}", color = Hud.Text.copy(alpha = 0.6f))
                            }
                            HudData(e.original.parent?.removePrefix("/storage/emulated/0") ?: "", color = Hud.CyanDim, size = 10.dp)
                        }
                        CountdownRing(daysLeft, Archive.KEEP_DAYS)
                    }
                }
            }
        }

        if (entries.isNotEmpty()) {
            HudActionBar {
                HudButton(
                    "Restore",
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val failed = withContext(Dispatchers.IO) { chosen.count { !Archive.restore(ctx, it) } }
                            onNotice(if (failed == 0) "Restored ${chosen.size} item(s)." else "Restored ${chosen.size - failed}; $failed failed.")
                            selected = emptySet()
                            busy = false
                            load()
                        }
                    },
                )
                HudButton(
                    if (chosen.isEmpty()) "Select to delete" else "Delete ${chosen.size} · ${fmtSize(chosen.sumOf { it.sizeBytes })}",
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = { confirmDelete = true },
                    danger = true,
                    filled = chosen.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${chosen.size} item(s) for good?") },
            text = { Text("This frees ${fmtSize(chosen.sumOf { it.sizeBytes })} and can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    busy = true
                    val doomed = chosen
                    scope.launch {
                        withContext(Dispatchers.IO) { doomed.forEach { Archive.deleteForever(ctx, it) } }
                        onNotice("Deleted ${doomed.size} item(s), ${fmtSize(doomed.sumOf { it.sizeBytes })} freed.")
                        selected = emptySet()
                        busy = false
                        load()
                    }
                }) { Text("Delete", color = Hud.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var downloads by remember { mutableStateOf(Prefs.downloads(ctx)) }
    var library by remember { mutableStateOf(Prefs.library(ctx)) }
    var candidates by remember { mutableStateOf<List<File>>(emptyList()) }
    var effects by remember { mutableStateOf(Prefs.effects(ctx)) }
    var apiKey by remember { mutableStateOf(Prefs.apiKey(ctx)) }
    var showKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        HudSection("Interface")
        HudCard(selected = effects, onClick = { effects = !effects; Prefs.setEffects(ctx, effects) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudCheck(effects)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Sounds and vibration", color = Hud.Text, fontSize = 15.sp)
                    HudData("Beeps and haptic ticks on taps", color = Hud.CyanDim, size = 11.dp)
                }
            }
        }

        HudSection(
            "Voice",
            "Simple commands (open an app, play a song, battery, storage, clean up) work without this. " +
                "Add a Claude API key from console.anthropic.com and Jarvis understands anything and can chat. " +
                "Each request costs a fraction of a penny.",
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; Prefs.setApiKey(ctx, it) },
                label = { Text("Claude API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = { Text(if (apiKey.isBlank()) "Not set: local commands only" else "Voice link: ONLINE") },
                modifier = Modifier.fillMaxWidth(),
            )
            HudButton(if (showKey) "Hide key" else "Show key", onClick = { showKey = !showKey })
        }

        HudSection("Folders")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = downloads,
                onValueChange = { downloads = it; Prefs.setDownloads(ctx, it) },
                label = { Text("Downloads folder") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = library,
                onValueChange = { library = it; Prefs.setLibrary(ctx, it) },
                label = { Text("Audiobook library (Smart AudioBook Player)") },
                supportingText = {
                    Text(
                        if (library.isBlank()) "Not set: nothing will be marked as already in your library."
                        else if (File(library).isDirectory) "Found ✓" else "Folder not found"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HudButton("Find audiobook folders", onClick = {
                scope.launch { candidates = withContext(Dispatchers.IO) { Scanner.findLibraryCandidates() } }
            })
        }
        candidates.forEachIndexed { i, c ->
            HudCard(index = i, selected = c.path == library, onClick = { library = c.path; Prefs.setLibrary(ctx, c.path) }) {
                HudData(c.path.removePrefix("/storage/emulated/0"), size = 13.dp)
            }
        }
        HudData(
            "Smart AudioBook Player shows its library (\"root\") folder in its own settings.",
            color = Hud.CyanDim, size = 11.dp, modifier = Modifier.padding(16.dp),
        )

        HudSection("Music app")
        Box(Modifier.padding(horizontal = 16.dp)) { MusicAppPicker() }
    }
}

// ---------------------------------------------------------------- grouping

enum class Grouping(val label: String) { Relevance("Relevance"), Date("Date"), Size("Size"), Name("A–Z") }

data class Group(val title: String, val subtitle: String, val items: List<Item>)

/** Splits the scan results into headed groups, depending on the chosen grouping. */
fun groupItems(list: List<Item>, by: Grouping): List<Group> = when (by) {
    Grouping.Relevance -> listOf(
        Group("Already in your library", "Copies in Downloads you probably don't need.", list.filter { it.inLibrary }),
        Group("Not in your library", "Leftovers from deleted books, or ones you haven't added yet. Check before archiving.",
            list.filter { !it.inLibrary }),
    ).filter { it.items.isNotEmpty() }

    Grouping.Date -> list.sortedByDescending { it.lastModified }
        .groupBy { SimpleDateFormat("MMMM yyyy", Locale.UK).format(Date(it.lastModified)) }
        .map { (month, items) -> Group(month, "Downloaded or last changed this month.", items) }

    Grouping.Size -> listOf(
        Group("Over 1 GB", "The biggest wins.", list.filter { it.sizeBytes >= 1L shl 30 }),
        Group("250 MB to 1 GB", "", list.filter { it.sizeBytes in (250L shl 20) until (1L shl 30) }),
        Group("Under 250 MB", "", list.filter { it.sizeBytes < 250L shl 20 }),
    ).map { g -> g.copy(items = g.items.sortedByDescending { it.sizeBytes }) }.filter { it.items.isNotEmpty() }

    Grouping.Name -> list.sortedBy { it.name.lowercase() }
        .groupBy { it.name.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }
        .map { (letter, items) -> Group(letter.toString(), "", items) }
}

@Composable
fun GroupChips(current: Grouping, onPick: (Grouping) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudLabel("Group", size = 10.sp, color = Hud.CyanDim)
        Grouping.values().forEach { g ->
            val on = g == current
            Box(
                Modifier
                    .border(1.dp, if (on) Hud.Cyan else Hud.CyanFaint, PanelShape)
                    .background(if (on) Hud.Cyan.copy(alpha = 0.18f) else Color.Transparent, PanelShape)
                    .clickable { onPick(g) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                HudLabel(g.label, size = 10.sp, color = if (on) Hud.Cyan else Hud.CyanDim)
            }
        }
    }
}
