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

    fun scan() {
        busy = true
        scope.launch {
            val downloads = File(Prefs.downloads(ctx))
            val lib = Prefs.library(ctx).takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
            items = withContext(Dispatchers.IO) { Scanner.scanDownloads(downloads, lib) }
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
            Text(
                when {
                    busy -> "Scanning…"
                    list == null -> ""
                    list.isEmpty() -> "No audiobook files left in Downloads."
                    else -> "${list.size} audiobook(s) in Downloads · ${fmtSize(list.sumOf { it.sizeBytes })}"
                },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { scan() }, enabled = !busy) { Text("Rescan") }
        }

        if (busy) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (list != null && list.isNotEmpty()) {
            GroupChips(group) { group = it }
            val groups = groupItems(list, group)

            LazyColumn(Modifier.weight(1f)) {
                groups.forEach { (title, subtitle, members) ->
                    item(key = "h-$title") {
                        SectionHeader(
                            "$title · ${members.size} · ${fmtSize(members.sumOf { it.sizeBytes })}",
                            subtitle,
                            onSelectAll = {
                                val paths = members.map { it.file.path }
                                selected = if (selected.containsAll(paths)) selected - paths.toSet() else selected + paths
                            },
                        )
                    }
                    items(members, key = { it.file.path }) { item ->
                        ItemRow(item, item.file.path in selected) {
                            selected = if (item.file.path in selected) selected - item.file.path else selected + item.file.path
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { selected = emptySet() }, enabled = chosen.isNotEmpty()) { Text("Clear") }
                Button(onClick = { confirm = true }, enabled = chosen.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(if (chosen.isEmpty()) "Select items to archive" else "Archive ${chosen.size} · ${fmtSize(chosen.sumOf { it.sizeBytes })}")
                }
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
fun ItemRow(item: Item, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                "${fmtSize(item.sizeBytes)} · ${item.audioCount} file(s) · ${fmtDate(item.lastModified)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                item.file.parent ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
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
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (entries.isEmpty()) "The archive is empty."
                else "${entries.size} item(s) · ${fmtSize(entries.sumOf { it.sizeBytes })}\n" +
                    "Auto-deleted ${Archive.KEEP_DAYS} days after archiving.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = {
                    selected = if (allOn) emptySet() else entries.map { it.archived.path }.toSet()
                }) { Text(if (allOn) "Select none" else "Select all") }
            }
        }

        if (busy) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(entries, key = { it.archived.path }) { e ->
                val on = e.archived.path in selected
                val toggle = { selected = if (on) selected - e.archived.path else selected + e.archived.path }
                val daysLeft = Archive.KEEP_DAYS - ((System.currentTimeMillis() - e.time) / 86_400_000L).toInt()
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = toggle).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = { toggle() })
                    Column(Modifier.weight(1f)) {
                        Text(e.archived.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                        Text(
                            "${fmtSize(e.sizeBytes)} · archived ${fmtDate(e.time)} · $daysLeft day(s) left",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "From: ${e.original.parent}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (entries.isNotEmpty()) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
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
                ) { Text("Restore") }
                Button(
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = { confirmDelete = true },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Hud.Red, contentColor = Hud.Bg),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (chosen.isEmpty()) "Select to delete" else "Delete ${chosen.size} · ${fmtSize(chosen.sumOf { it.sizeBytes })}")
                }
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
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = downloads,
            onValueChange = { downloads = it; Prefs.setDownloads(ctx, it) },
            label = { Text("Downloads folder") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = library,
            onValueChange = { library = it; Prefs.setLibrary(ctx, it) },
            label = { Text("Audiobook library folder (Smart AudioBook Player)") },
            supportingText = {
                Text(
                    if (library.isBlank()) "Not set: nothing will be marked as already in your library."
                    else if (File(library).isDirectory) "Found ✓" else "Folder not found"
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = {
            scope.launch { candidates = withContext(Dispatchers.IO) { Scanner.findLibraryCandidates() } }
        }) { Text("Find audiobook folders") }
        candidates.forEach { c ->
            TextButton(onClick = { library = c.path; Prefs.setLibrary(ctx, c.path) }) { Text(c.path) }
        }
        HorizontalDivider()
        MusicAppPicker()
        Spacer(Modifier.height(8.dp))
        Text(
            "Tip: Smart AudioBook Player shows which folder it uses as its library (its \"root\" folder) in its own settings.",
            style = MaterialTheme.typography.bodySmall,
        )
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
