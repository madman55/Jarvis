package com.joseph.jarvis

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

/** A song (or audiobook file) stored on the phone. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

object Music {
    /** Every music file Android knows about on the phone, A–Z by title. */
    fun all(ctx: Context): List<Track> {
        val cols = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val out = mutableListOf<Track>()
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            cols,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += Track(
                    id = c.getLong(0),
                    title = c.getString(1) ?: "Unknown",
                    artist = c.getString(2)?.takeUnless { it == "<unknown>" } ?: "",
                    album = c.getString(3) ?: "",
                    durationMs = c.getLong(4),
                    path = c.getString(5) ?: "",
                )
            }
        }
        return out
    }

    fun matches(t: Track, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.lowercase()
        return t.title.lowercase().contains(q) || t.artist.lowercase().contains(q) || t.album.lowercase().contains(q)
    }

    /** Opens the song in your default music player. */
    fun play(ctx: Context, t: Track): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(t.uri, "audio/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(intent); true } catch (e: Exception) { false }
    }

    fun fmtDuration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }
}
