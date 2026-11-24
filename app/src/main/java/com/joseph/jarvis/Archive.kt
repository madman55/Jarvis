package com.joseph.jarvis

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Something that was moved into the archive, and where it came from. */
data class ArchiveEntry(
    val archived: File,
    val original: File,
    val time: Long,
    val sizeBytes: Long,
)

/**
 * The safety net. Nothing is deleted straight away: it is moved to
 * "Jarvis Archive" on your phone's storage, and only removed for good
 * after 30 days (or when you choose to).
 */
object Archive {
    const val KEEP_DAYS = 30

    fun root() = File(Environment.getExternalStorageDirectory(), "Jarvis Archive")
    private fun manifest() = File(root(), ".manifest.json")

    fun list(): List<ArchiveEntry> = read().filter { it.archived.exists() }.sortedByDescending { it.time }

    fun archive(ctx: Context, file: File): Boolean {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
        val dest = freeName(File(File(root(), day), file.name))
        dest.parentFile?.mkdirs()
        val size = sizeOf(file)
        val oldPaths = allFiles(file)
        if (!move(file, dest)) return false
        write(read() + ArchiveEntry(dest, file, System.currentTimeMillis(), size))
        rescan(ctx, oldPaths + allFiles(dest))
        return true
    }

    fun restore(ctx: Context, entry: ArchiveEntry): Boolean {
        val dest = freeName(entry.original)
        dest.parentFile?.mkdirs()
        val oldPaths = allFiles(entry.archived)
        if (!move(entry.archived, dest)) return false
        write(read().filter { it.archived != entry.archived })
        rescan(ctx, oldPaths + allFiles(dest))
        return true
    }

    fun deleteForever(ctx: Context, entry: ArchiveEntry) {
        val oldPaths = allFiles(entry.archived)
        entry.archived.deleteRecursively()
        write(read().filter { it.archived != entry.archived })
        rescan(ctx, oldPaths)
        cleanEmptyDays()
    }

    /** Removes anything archived more than KEEP_DAYS ago. Returns how many went. */
    fun purgeOld(ctx: Context): Int {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000
        val old = list().filter { it.time < cutoff }
        old.forEach { deleteForever(ctx, it) }
        return old.size
    }

    // ---- helpers ----

    private fun move(from: File, to: File): Boolean {
        if (from.renameTo(to)) return true
        // Fallback if a straight rename isn't allowed: copy, then remove the original.
        return try {
            from.copyRecursively(to, overwrite = false) && from.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    /** "Book" becomes "Book (2)" if something with that name is already there. */
    private fun freeName(f: File): File {
        if (!f.exists()) return f
        val base = if (f.isDirectory) f.name else f.nameWithoutExtension
        val ext = if (f.isDirectory || f.extension.isEmpty()) "" else "." + f.extension
        return generateSequence(2) { it + 1 }
            .map { File(f.parentFile, "$base ($it)$ext") }
            .first { !it.exists() }
    }

    private fun sizeOf(f: File): Long = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun allFiles(f: File): List<String> = f.walkTopDown().filter { it.isFile }.map { it.path }.toList()

    /** Tells Android's media library that files moved, so music apps don't show ghosts. */
    private fun rescan(ctx: Context, paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null)
    }

    private fun cleanEmptyDays() {
        root().listFiles()?.forEach { if (it.isDirectory && it.list()?.isEmpty() == true) it.delete() }
    }

    private fun read(): List<ArchiveEntry> {
        val f = manifest()
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ArchiveEntry(
                    archived = File(o.getString("archived")),
                    original = File(o.getString("original")),
                    time = o.getLong("time"),
                    sizeBytes = o.optLong("size", 0),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(entries: List<ArchiveEntry>) {
        root().mkdirs()
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject()
                    .put("archived", it.archived.path)
                    .put("original", it.original.path)
                    .put("time", it.time)
                    .put("size", it.sizeBytes)
            )
        }
        manifest().writeText(arr.toString(2))
    }
}
