package app.gameport.core.install

import java.io.File

/**
 * Keeps a game's expansion files out of the way while the game is installed over, then puts them back. Installing the patched APK over the game can leave its
 * `Android/obb/<package>` folder empty (seen on Puzzling Places after every patch again, while other games kept theirs): the game then finds no expansion file and starts
 * Unreal's own downloader, which cannot work here and stays on the loading screen. The files go to a folder GamePort owns, on the same storage, so that moving them is a
 * rename and not a copy of gigabytes; and what an interrupted patch left there is put back the next time.
 */
internal object ObbStash {
    /** Moves everything in [obb] into [stash]; returns how many entries were moved. Nothing already in [stash] is overwritten. */
    fun stash(obb: File, stash: File): Int {
        val entries = obb.listFiles().orEmpty()
        if (entries.isEmpty()) return 0
        stash.mkdirs()
        var moved = 0
        for (entry in entries) {
            val destination = File(stash, entry.name)
            if (destination.exists()) continue
            if (move(entry, destination)) moved++
        }
        return moved
    }

    /**
     * Moves back into [obb] what [stash] holds and [obb] does not (a name that is already there stays as it is), opens the files to all, and removes [stash] once it is
     * empty. Returns how many entries were put back.
     */
    fun restore(stash: File, obb: File): Int {
        val entries = stash.listFiles().orEmpty()
        if (entries.isEmpty()) {
            stash.delete()
            return 0
        }
        obb.mkdirs()
        var back = 0
        for (entry in entries) {
            val destination = File(obb, entry.name)
            if (destination.exists()) {
                // The game's own folder already has it (the update kept it, or a new one was placed): the copy kept aside is not needed.
                entry.deleteRecursively()
                continue
            }
            if (move(entry, destination)) back++
        }
        obb.walkTopDown().filter { it.isFile }.forEach { it.setReadable(true, false) }
        if (stash.list().isNullOrEmpty()) stash.delete()
        return back
    }

    private fun move(source: File, destination: File): Boolean = runCatching {
        if (source.renameTo(destination)) return true
        // Not on the same storage: copied, and the original removed only when the copy is whole.
        source.copyRecursively(destination, overwrite = false)
        source.deleteRecursively()
        true
    }.getOrDefault(false)
}
