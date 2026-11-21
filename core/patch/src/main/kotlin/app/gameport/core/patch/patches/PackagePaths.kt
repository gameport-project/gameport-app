package app.gameport.core.patch.patches

import java.io.InputStream
import java.io.OutputStream

/**
 * Makes the paths a game wrote down with a package name follow the name the game runs as.
 *
 * Some games keep the way to their own folders in their compiled code, built with the package name they were released under: Escape Simulator (Unity, IL2CPP) holds
 * `/storage/emulated/0/Android/obb/com.PineStudio.EscapeSimulator/assets` as a string, and loads its asset bundles from it. Under another name ([PackageRenamePatch]) the files of the game are
 * in the folder of the new name, the game looks in the old one, finds nothing, and starts without its asset bundles: no texts (the menu shows `%singleplayer%`), no rig. The old folder
 * cannot be given to the game instead: Android keeps an app out of the expansion folder of another package.
 *
 * The string is changed where it stands, **without changing the size of the file**. A compiled game keeps its strings in tables addressed by position and length (the string
 * literals of an IL2CPP game, the user strings of a Mono assembly): one byte more or less and every address after it is wrong. So the name is written over the old one and, since
 * a package name of GamePort's own is shorter than most, the difference is made of slashes put in front of it (`obb///////com.gameport.g1435790`): a path with repeated slashes
 * names the same folder on Android, in Java, in Mono and in the system calls under them.
 *
 * What is looked for is narrow on purpose. A name is changed only when it stands right after a folder that holds a package's own files (`Android/obb/`, `Android/data/`,
 * `data/data/`, `data/user/0/`), possibly with slashes between, and is not the beginning of a longer name. The same word anywhere else (a store identifier, a comparison with the
 * package name, a log line) is the game's own business and is left as it is.
 *
 * The slot a name takes (its slashes and its letters together) never changes, so the pass can be run again, or the other way when the player puts the game back under its own name:
 * the names it finds, the one the game came with and the one it carries now, are both written as the one wanted.
 */
internal object PackagePaths {
    /** What a pass over one file did. */
    data class Outcome(
        /** Paths rewritten. */
        val rewritten: Int,
        /** Paths that hold the old name but have no room for the new one (it is longer than what stands there): left as they were. */
        val tooShort: Int,
    )

    /** The folders under which the files of a package lie, named by their end. */
    private val FOLDERS = listOf("Android/obb/", "Android/data/", "data/data/", "data/user/0/")

    /** What a stream pass reads at a time, and how much of the end of a window is kept for the next one: a path that begins in it may end beyond. */
    private const val CHUNK = 4 * 1024 * 1024
    private const val OVERLAP = 512

    /**
     * Writes [target] over each of [names] found in a path of [content], in place, and returns what it did. [wide] looks for UTF-16 text (the user strings of a .NET assembly)
     * instead of UTF-8 / ASCII (the string literals of IL2CPP). A name equal to [target] is not looked for: there is nothing to change.
     */
    fun rewrite(content: ByteArray, names: Collection<String>, target: String, wide: Boolean = false): Outcome =
        rewriteRange(content, content.size, content.size, names, target, wide)

    /**
     * The same, from [input] to [output], a window at a time: the file is never held whole, whatever its size (the metadata of a large game is some tens of megabytes). The result is
     * the one [rewrite] gives on the whole file. A path longer than [OVERLAP] bytes in all that falls across two windows is not seen, which no game writes. [chunk] is the size of a window.
     */
    fun rewriteStream(input: InputStream, output: OutputStream, names: Collection<String>, target: String, wide: Boolean = false, chunk: Int = CHUNK): Outcome {
        val window = ByteArray(chunk + OVERLAP)
        var held = 0
        var rewritten = 0
        var tooShort = 0
        while (true) {
            var filled = held
            var ended = false
            while (filled < window.size) {
                val n = input.read(window, filled, window.size - filled)
                if (n < 0) {
                    ended = true
                    break
                }
                filled += n
            }
            // A path that begins before [limit] lies whole in the window; one that begins after it is looked at with the next window.
            val limit = if (ended) filled else filled - OVERLAP
            val outcome = rewriteRange(window, filled, limit, names, target, wide)
            rewritten += outcome.rewritten
            tooShort += outcome.tooShort
            if (ended) {
                output.write(window, 0, filled)
                return Outcome(rewritten, tooShort)
            }
            output.write(window, 0, limit)
            System.arraycopy(window, limit, window, 0, filled - limit)
            held = filled - limit
        }
    }

    /** Looks for paths that begin before [startLimit], in the first [end] bytes of [content]. */
    private fun rewriteRange(content: ByteArray, end: Int, startLimit: Int, names: Collection<String>, target: String, wide: Boolean): Outcome {
        val old = names.filter { it.isNotEmpty() && it != target }.distinct().sortedByDescending { it.length }
        if (old.isEmpty() || target.isEmpty()) return Outcome(0, 0)
        val step = if (wide) 2 else 1
        val prefixes = FOLDERS.map { encode(it, wide) }
        val encoded = old.map { encode(it, wide) }
        val slash = encode("/", wide)
        var rewritten = 0
        var tooShort = 0
        var i = 0
        while (i < startLimit) {
            val first = content[i]
            if (first != 'A'.code.toByte() && first != 'd'.code.toByte()) {
                i++
                continue
            }
            val prefix = prefixes.firstOrNull { matches(content, end, i, it) }
            if (prefix == null) {
                i++
                continue
            }
            val start = i + prefix.size
            var at = start
            var slashes = 0
            while (matches(content, end, at, slash)) {
                at += step
                slashes++
            }
            val found = encoded.indexOfFirst { matches(content, end, at, it) && !continuesName(content, end, at + it.size, wide) }
            if (found < 0) {
                i = start
                continue
            }
            val slot = slashes + old[found].length
            if (target.length > slot) {
                tooShort++
                i = at + encoded[found].size
                continue
            }
            val written = encode("/".repeat(slot - target.length) + target, wide)
            System.arraycopy(written, 0, content, start, written.size)
            rewritten++
            i = start + written.size
        }
        return Outcome(rewritten, tooShort)
    }

    private fun encode(text: String, wide: Boolean): ByteArray = text.toByteArray(if (wide) Charsets.UTF_16LE else Charsets.ISO_8859_1)

    private fun matches(content: ByteArray, end: Int, at: Int, pattern: ByteArray): Boolean {
        if (at < 0 || at + pattern.size > end) return false
        for (k in pattern.indices) if (content[at + k] != pattern[k]) return false
        return true
    }

    /** Whether the character at [at] goes on a package name (a letter, a digit, `_` or `.`): the name found is then the start of a longer one. */
    private fun continuesName(content: ByteArray, end: Int, at: Int, wide: Boolean): Boolean {
        if (at >= end) return false
        if (wide && (at + 1 >= end || content[at + 1].toInt() != 0)) return false
        val c = content[at].toInt().toChar()
        return c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '.'
    }
}
