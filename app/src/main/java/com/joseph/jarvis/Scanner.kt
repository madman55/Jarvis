package com.joseph.jarvis

import android.os.Environment
import java.io.File

/** One audiobook found in Downloads: either a folder of audio files or a single audio file. */
data class Item(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val audioCount: Int,
    val inLibrary: Boolean,
)

object Scanner {
    val AUDIO_EXT = setOf("mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wav", "wma")

    fun isAudio(f: File) = f.isFile && f.extension.lowercase() in AUDIO_EXT

    /** Lower-case letters and digits only, so "The Hobbit - Part 1" matches "the_hobbit_part_1". */
    fun normalise(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Walks Downloads and returns every audiobook left there.
     * A folder that directly holds audio files counts as one book.
     * Loose audio files sitting straight in Downloads count one each.
     */
    fun scanDownloads(downloads: File, library: File?): List<Item> {
        val libraryNames = library?.let { indexLibrary(it) } ?: emptySet()
        val skip = listOfNotNull(library?.canonicalPath, Archive.root().canonicalPath)
        val found = mutableListOf<Item>()

        fun walk(dir: File) {
            if (dir.canonicalPath in skip) return
            val children = dir.listFiles() ?: return
            val audio = children.filter { isAudio(it) }

            if (dir == downloads) {
                for (f in audio) {
                    found += Item(
                        file = f,
                        name = f.nameWithoutExtension,
                        sizeBytes = f.length(),
                        lastModified = f.lastModified(),
                        audioCount = 1,
                        inLibrary = matches(f.nameWithoutExtension, libraryNames),
                    )
                }
            } else if (audio.isNotEmpty()) {
                found += Item(
                    file = dir,
                    name = dir.name,
                    sizeBytes = children.filter { it.isFile }.sumOf { it.length() },
                    lastModified = audio.maxOf { it.lastModified() },
                    audioCount = audio.size,
                    inLibrary = matches(dir.name, libraryNames),
                )
            }

            for (child in children) {
                if (child.isDirectory && !child.name.startsWith(".")) walk(child)
            }
        }

        walk(downloads)
        return found.sortedByDescending { it.sizeBytes }
    }

    /** Collects the names of every folder and audio file in the audiobook library. */
    private fun indexLibrary(library: File): Set<String> {
        val names = mutableSetOf<String>()
        fun walk(dir: File, depth: Int) {
            if (depth > 4) return
            for (child in dir.listFiles() ?: return) {
                if (child.isDirectory) {
                    names += normalise(child.name)
                    walk(child, depth + 1)
                } else if (isAudio(child)) {
                    names += normalise(child.nameWithoutExtension)
                }
            }
        }
        walk(library, 0)
        names.remove("")
        return names
    }

    private fun matches(name: String, library: Set<String>): Boolean {
        val n = normalise(name)
        if (n.isEmpty()) return false
        if (n in library) return true
        // Allow for small naming differences, e.g. "(Unabridged)" added on one side.
        return n.length >= 8 && library.any { it.length >= 8 && (it.contains(n) || n.contains(it)) }
    }

    /** Folders near the top of storage with "audiobook" in the name: likely your player's library. */
    fun findLibraryCandidates(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val out = mutableListOf<File>()
        fun look(dir: File, depth: Int) {
            for (child in dir.listFiles() ?: return) {
                if (!child.isDirectory || child.name.startsWith(".") || child.name == "Android") continue
                val n = child.name.lowercase().replace(" ", "")
                if ("audiobook" in n) out += child
                if (depth < 1) look(child, depth + 1)
            }
        }
        look(root, 0)
        return out
    }
}
