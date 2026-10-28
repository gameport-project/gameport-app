package app.gameport.core.install

import java.io.File

/**
 * Keeps a game's expansion files out of the way while the game is installed over, then puts them back. Installing the patched APK over the game can leave its
 * `Android/obb/<package>` folder empty (seen on Puzzling Places after every patch again, while other games kept theirs): the game then finds no expansion file and starts
 * Unreal's own downloader, which cannot work here and stays on the loading screen. The files go to a folder GamePort owns; and what an interrupted patch left there is
 * put back the next time.
 *
 * The copy kept aside is the only one while the game is installed over, so it is never removed before the copy in the game's folder is checked to be the same
 * (names and sizes), a failure leaves it where it is, and only one stash or restore runs at a time (two at once removed each other's files).
 */
internal object ObbStash {
    private val lock = Any()
    private const val PARTIAL = ".gameport-partial"

    /** What a step did: how many entries, and for each one it could not handle the name and why. A failed entry stays where it was. */
    class Result(val moved: Int, val failures: List<String>)

    /** Moves everything in [obb] into [stash]. Nothing already in [stash] is overwritten. */
    fun stash(obb: File, stash: File): Result = synchronized(lock) {
        val entries = obb.listFiles().orEmpty().filter { !it.name.endsWith(PARTIAL) }
        if (entries.isEmpty()) return Result(0, emptyList())
        stash.mkdirs()
        var moved = 0
        val failures = mutableListOf<String>()
        for (entry in entries) {
            val destination = File(stash, entry.name)
            if (destination.exists()) {
                // Kept aside by an earlier try: the one in the game's folder is left alone, and nothing is lost either way.
                if (!sameContent(entry, destination)) failures += "${entry.name}: a different copy is already kept aside"
                continue
            }
            val error = move(entry, destination)
            if (error == null) moved++ else failures += "${entry.name}: $error"
        }
        Result(moved, failures)
    }

    /**
     * Puts back into [obb] what [stash] holds, opens the files to all, and removes [stash] once it is empty. A name the game's folder already has is compared: the copy
     * kept aside goes only when both are the same, otherwise it stays (the game's own is not replaced either).
     */
    fun restore(stash: File, obb: File): Result = synchronized(lock) {
        val entries = stash.listFiles().orEmpty()
        if (entries.isEmpty()) {
            stash.delete()
            return Result(0, emptyList())
        }
        obb.mkdirs()
        obb.listFiles().orEmpty().filter { it.name.endsWith(PARTIAL) }.forEach { it.deleteRecursively() }
        var back = 0
        val failures = mutableListOf<String>()
        for (entry in entries) {
            val destination = File(obb, entry.name)
            if (destination.exists()) {
                if (sameContent(entry, destination)) {
                    if (entry.deleteRecursively()) back++ else failures += "${entry.name}: cannot remove the copy kept aside"
                } else {
                    failures += "${entry.name}: the game's folder holds a different one, the copy kept aside stays"
                }
                continue
            }
            val error = move(entry, destination)
            if (error == null) back++ else failures += "${entry.name}: $error"
        }
        // What was copied back is GamePort's: its files and its folders are open to the game, or the game cannot enter them.
        ExpansionFiles.openToAll(obb)
        if (stash.list().isNullOrEmpty()) stash.delete()
        Result(back, failures)
    }

    /** Null when [source] is now at [destination] and no longer at [source]; otherwise why not. The original is removed only after the copy is checked. */
    private fun move(source: File, destination: File): String? {
        return try {
            if (source.renameTo(destination) && destination.exists() && !source.exists()) return null
            // Not a rename (another storage, or the system refused): copied beside the destination, checked, then put in place, and only then the original removed.
            val partial = File(destination.parentFile, destination.name + PARTIAL)
            partial.deleteRecursively()
            source.copyRecursively(partial, overwrite = true) { _, e -> throw e }
            if (!sameContent(source, partial)) {
                partial.deleteRecursively()
                return "the copy differs from the original, the original stays"
            }
            if (!partial.renameTo(destination)) {
                partial.deleteRecursively()
                return "cannot put the copy in place, the original stays"
            }
            if (!source.deleteRecursively()) return "copied, but the original cannot be removed"
            null
        } catch (e: Throwable) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /** Same names and sizes, folders included: a partial copy is always shorter, so the size is enough here. */
    private fun sameContent(a: File, b: File): Boolean {
        if (a.isFile != b.isFile) return false
        if (a.isFile) return a.length() == b.length()
        val left = a.walkTopDown().filter { it.isFile }.associate { it.relativeTo(a).path to it.length() }
        val right = b.walkTopDown().filter { it.isFile }.associate { it.relativeTo(b).path to it.length() }
        return left == right
    }
}
