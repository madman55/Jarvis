package com.joseph.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What Jarvis says back, and optionally a screen to jump to. */
data class Reply(val say: String, val go: Dest? = null)

/**
 * Turns what you said into actions.
 * Simple, clear commands ("open WhatsApp", "play Drake", "battery") are handled right here
 * on the phone, instantly and for free. Anything else goes to Claude, if you've added an
 * API key in Settings, which can use the same actions as tools.
 * Nothing destructive (archiving, deleting) can be triggered by voice.
 */
object Brain {
    private const val MODEL = "claude-haiku-5-5"
    private val history = mutableListOf<Pair<String, String>>() // role to text, last few turns

    /** Call from a background thread. */
    fun respond(ctx: Context, heard: String): Reply {
        val local = handleLocally(ctx, heard)
        val reply = when {
            local != null -> local
            Prefs.apiKey(ctx).isNotBlank() -> try {
                askClaude(ctx, heard)
            } catch (e: Exception) {
                Reply("I couldn't reach my servers, sir. ${e.message?.take(80) ?: ""}".trim())
            }
            else -> Reply(
                "I can open apps, play music and report on the phone, sir. " +
                    "For anything more, add a Claude API key in Settings."
            )
        }
        history += "user" to heard
        history += "assistant" to reply.say
        while (history.size > 12) history.removeAt(0)
        return reply
    }

    // ------------------------------------------------------------ local commands

    private fun handleLocally(ctx: Context, heard: String): Reply? {
        val t = heard.lowercase(Locale.UK).trim().trimEnd('.', '?', '!')

        Regex("^(?:jarvis[, ]*)?(?:open|launch|start|run)\\s+(.+)$").find(t)?.let { m ->
            val what = m.groupValues[1].removePrefix("the ").removeSuffix(" app")
            screenFor(what)?.let { return Reply("${it.label}, sir.", go = it) }
            return openApp(ctx, what)?.let { Reply(it) }
        }

        Regex("^(?:jarvis[, ]*)?play\\s+(.+?)(?:\\s+(?:on|in)\\s+(.+))?$").find(t)?.let { m ->
            return Reply(playMusic(ctx, m.groupValues[1], m.groupValues[2].ifBlank { null }))
        }

        if (Regex("\\b(what(?:'s| is) the time|what time is it)\\b").containsMatchIn(t)) {
            return Reply("It's ${SimpleDateFormat("h:mm a", Locale.UK).format(Date())}, sir.")
        }
        if ("battery" in t) {
            val s = readStats(ctx)
            return Reply("Battery is at ${s.battery} percent${if (s.charging) " and charging" else ""}, sir.")
        }
        if (Regex("\\b(storage|space)\\b").containsMatchIn(t) && !t.startsWith("why")) {
            val s = readStats(ctx)
            return Reply("You have %.1f gigabytes free, sir. Storage is %.0f percent full.".format(s.storageFreeGb, s.storageUsed * 100))
        }
        if (Regex("\\b(clean ?up|leftovers?|downloads)\\b").containsMatchIn(t)) {
            return Reply(leftoverSummary(ctx), go = Dest.Cleanup)
        }
        if (Regex("^(hi|hello|hey|evening|morning|good (morning|afternoon|evening))( jarvis)?$").matches(t)) {
            return Reply("At your service, sir.")
        }
        return null
    }

    private fun screenFor(what: String): Dest? = when (what.trim()) {
        "clean up", "cleanup", "clean-up" -> Dest.Cleanup
        "archive", "the archive" -> Dest.Archive
        "music", "my music" -> Dest.Music
        "apps", "my apps" -> Dest.Apps
        "jarvis settings", "your settings" -> Dest.Settings
        else -> null
    }

    // ------------------------------------------------------------ actions (shared with Claude's tools)

    private fun norm(s: String) = s.lowercase(Locale.UK).filter { it.isLetterOrDigit() }

    /** Opens the best-matching app. Returns what to say, or null if nothing matched. */
    fun openApp(ctx: Context, name: String): String? {
        val n = norm(name)
        if (n.isEmpty()) return null
        val apps = Apps.launchable(ctx)
        val app = apps.firstOrNull { norm(it.label) == n }
            ?: apps.firstOrNull { norm(it.label).startsWith(n) }
            ?: apps.firstOrNull { norm(it.label).contains(n) }
            ?: apps.firstOrNull { n.contains(norm(it.label)) && norm(it.label).length >= 4 }
            ?: return "I can't find an app called $name, sir."
        return if (Apps.open(ctx, app.packageName)) "Opening ${app.label}." else "${app.label} wouldn't open, sir."
    }

    fun playMusic(ctx: Context, query: String, inApp: String? = null): String {
        // "play X on YouTube Music": send straight to that app
        if (inApp != null && norm(inApp) !in setOf("phone", "myphone", "thephone")) {
            val app = Apps.launchable(ctx).firstOrNull { norm(it.label).contains(norm(inApp)) }
            if (app != null) {
                Apps.playInApp(ctx, app.packageName, query)
                return "Playing $query on ${app.label}."
            }
        }
        // Otherwise, a song saved on the phone if there's a match...
        val tracks = Music.all(ctx)
        val q = query.lowercase(Locale.UK)
        val hit = tracks.firstOrNull { it.title.lowercase(Locale.UK) == q }
            ?: tracks.firstOrNull { it.title.lowercase(Locale.UK).contains(q) }
            ?: tracks.firstOrNull { it.artist.lowercase(Locale.UK).contains(q) }
        if (hit != null) {
            Music.play(ctx, hit)
            return "Playing ${hit.title}${if (hit.artist.isNotBlank()) " by ${hit.artist}" else ""}."
        }
        // ...else hand it to the chosen music app.
        val pkg = Prefs.musicApp(ctx) ?: return "I couldn't find $query, and no music app is set, sir."
        Apps.playInApp(ctx, pkg, query)
        val label = Apps.launchable(ctx).firstOrNull { it.packageName == pkg }?.label ?: "your music app"
        return "Searching $label for $query."
    }

    fun leftoverSummary(ctx: Context): String {
        val lib = Prefs.library(ctx).takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
        val items = Scanner.scanDownloads(File(Prefs.downloads(ctx)), lib)
        return if (items.isEmpty()) "Downloads is clear of audiobooks, sir."
        else "There are ${items.size} audiobooks in Downloads, taking ${fmtSize(items.sumOf { it.sizeBytes })}. Here they are."
    }

    fun statusReport(ctx: Context): String {
        val s = readStats(ctx)
        return "Battery ${s.battery}%${if (s.charging) " (charging)" else ""}; " +
            "storage %.0f%% full, %.1f GB free; memory %.0f%% used; time %s.".format(
                s.storageUsed * 100, s.storageFreeGb, s.ramUsed * 100,
                SimpleDateFormat("EEEE d MMMM, HH:mm", Locale.UK).format(Date()),
            )
    }

    // ------------------------------------------------------------ Claude

    private val SYSTEM =
        "You are JARVIS, the AI assistant running on Joseph's Android phone (a Pixel 8 Pro). " +
            "You are calm, capable and dryly witty, with a British manner, and you address him as \"sir\". " +
            "Everything you write is spoken aloud by text-to-speech, so reply in one or two short sentences of plain speech: " +
            "no lists, no markdown, no emoji. Use your tools to act on the phone. Only say you did something if a tool did it. " +
            "You cannot archive or delete files by voice; if asked, open the Clean up or Archive screen so he can do it himself."

    private fun tools(): JSONArray = JSONArray().apply {
        put(tool("open_app", "Open an app installed on the phone by its name.",
            props("name" to "The app's name, e.g. WhatsApp"), listOf("name")))
        put(tool("play_music", "Play music: a song saved on the phone if one matches, otherwise search the user's music app.",
            props("query" to "Song, artist or album", "app" to "Optional app to play it in, e.g. 'YouTube Music'"), listOf("query")))
        put(tool("get_status", "Battery, storage, memory, date and time.", JSONObject(), listOf()))
        put(tool("audiobook_leftovers", "Count audiobooks left in Downloads and how much space they take.", JSONObject(), listOf()))
        put(tool("show_screen", "Open one of Jarvis's own screens.",
            JSONObject().put("screen", JSONObject().put("type", "string")
                .put("enum", JSONArray(listOf("music", "apps", "cleanup", "archive", "settings")))), listOf("screen")))
    }

    private fun tool(name: String, desc: String, properties: JSONObject, required: List<String>) = JSONObject()
        .put("name", name).put("description", desc)
        .put("input_schema", JSONObject().put("type", "object").put("properties", properties).put("required", JSONArray(required)))

    private fun props(vararg p: Pair<String, String>) = JSONObject().apply {
        p.forEach { (k, d) -> put(k, JSONObject().put("type", "string").put("description", d)) }
    }

    private fun askClaude(ctx: Context, heard: String): Reply {
        val messages = JSONArray()
        history.forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text)) }
        messages.put(JSONObject().put("role", "user").put("content", heard))
        var go: Dest? = null

        repeat(4) {
            val resp = post(ctx, JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 300)
                .put("system", SYSTEM)
                .put("tools", tools())
                .put("messages", messages))
            val content = resp.getJSONArray("content")
            if (resp.optString("stop_reason") != "tool_use") {
                val text = (0 until content.length()).map { content.getJSONObject(it) }
                    .filter { it.optString("type") == "text" }.joinToString(" ") { it.getString("text") }.trim()
                return Reply(text.ifBlank { "Done, sir." }, go)
            }
            messages.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") != "tool_use") continue
                val input = block.optJSONObject("input") ?: JSONObject()
                val out = when (block.getString("name")) {
                    "open_app" -> openApp(ctx, input.optString("name")) ?: "No app name given."
                    "play_music" -> playMusic(ctx, input.optString("query"), input.optString("app").ifBlank { null })
                    "get_status" -> statusReport(ctx)
                    "audiobook_leftovers" -> leftoverSummary(ctx)
                    "show_screen" -> {
                        go = when (input.optString("screen")) {
                            "music" -> Dest.Music; "apps" -> Dest.Apps; "cleanup" -> Dest.Cleanup
                            "archive" -> Dest.Archive; else -> Dest.Settings
                        }
                        "Screen opened."
                    }
                    else -> "Unknown tool."
                }
                results.put(JSONObject().put("type", "tool_result").put("tool_use_id", block.getString("id")).put("content", out))
            }
            messages.put(JSONObject().put("role", "user").put("content", results))
        }
        return Reply("Done, sir.", go)
    }

    private fun post(ctx: Context, body: JSONObject): JSONObject {
        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", Prefs.apiKey(ctx))
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = try { JSONObject(text).getJSONObject("error").getString("message") } catch (e: Exception) { "error $code" }
            throw RuntimeException(msg)
        }
        return JSONObject(text)
    }
}
