package app.gameport.core.sync

import java.io.File

/** Keeps what the run before left behind, when a new run begins: the files of a run are written over by the next. */
internal object RunArchive {
    private const val MARKER = "run.txt"

    /**
     * A run of the game, told by its process number, hands its files over. When it is not the run the files in [folder] are from, those files are
     * copied under their archive names ([archived], current name to kept name) before they are written over. Returns whether it did.
     */
    fun noteRun(folder: File, pid: Int, archived: Map<String, String>, discarded: (String) -> Boolean = { false }): Boolean {
        if (pid == 0) return false
        val marker = File(folder, MARKER)
        val before = runCatching { marker.readText().trim().toInt() }.getOrNull()
        var copied = false
        if (before != null && before != pid) {
            archived.forEach { (name, kept) ->
                File(folder, name).takeIf { it.isFile && it.length() > 0 }?.copyTo(File(folder, kept), overwrite = true)?.also { copied = true }
            }
            // What belongs to the run before and is not kept apart goes: a file left in place would be read, in the report of this run, as if this run had written it.
            folder.listFiles()?.filter { it.isFile && discarded(it.name) }?.forEach { it.delete() }
        }
        marker.writeText(pid.toString())
        return copied
    }
}
