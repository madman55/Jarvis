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
}
