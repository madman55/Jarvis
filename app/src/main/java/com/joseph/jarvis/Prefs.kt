package com.joseph.jarvis

import android.content.Context
import android.os.Environment
import java.io.File

/** Remembers which folders Jarvis should look at. */
object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun downloads(ctx: Context): String =
        sp(ctx).getString("downloads", null)
            ?: File(Environment.getExternalStorageDirectory(), "Download").path

    fun setDownloads(ctx: Context, path: String) = sp(ctx).edit().putString("downloads", path.trim()).apply()

    /** Your audiobook player's library folder. Guessed on first run if not set. */
    fun library(ctx: Context): String =
        sp(ctx).getString("library", null)
            ?: Scanner.findLibraryCandidates().firstOrNull()?.path
            ?: ""

    fun setLibrary(ctx: Context, path: String) = sp(ctx).edit().putString("library", path.trim()).apply()

    /** The music app Jarvis sends "play X" requests to. Guesses one with "tube" in its name at first. */
    fun musicApp(ctx: Context): String? =
        sp(ctx).getString("musicApp", null)
            ?: Apps.musicApps(ctx).firstOrNull { it.label.contains("tube", ignoreCase = true) }?.packageName
            ?: Apps.launchable(ctx).firstOrNull { it.label.contains("tube", ignoreCase = true) }?.packageName
            ?: Apps.musicApps(ctx).firstOrNull()?.packageName

    fun setMusicApp(ctx: Context, pkg: String) = sp(ctx).edit().putString("musicApp", pkg).apply()

    /** Sounds and vibrations on taps. */
    fun effects(ctx: Context): Boolean = sp(ctx).getBoolean("effects", true)
    fun setEffects(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("effects", on).apply()

    /** Optional Claude API key: makes voice understand anything, not just simple commands. */
    fun apiKey(ctx: Context): String = sp(ctx).getString("apiKey", null) ?: ""
    fun setApiKey(ctx: Context, key: String) = sp(ctx).edit().putString("apiKey", key.trim()).apply()
}
