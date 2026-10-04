package app.gameport.core.steam

import java.io.File

/**
 * What the downloader remembers about a folder it downloaded into: which manifest of each depot it already has, and a copy of that manifest.
 * It trusts that memory: a depot it believes it has is not checked again, whatever the files hold. A download that was interrupted (the
 * application killed, a pause, the network lost) leaves the files at their full size, written only in part, so a resumed download would
 * declare itself done on files that are mostly empty.
 */
internal object DownloadState {
    /** The folder the downloader keeps its memory in (`DepotDownloader.CONFIG_DIR`). */
    const val FOLDER = ".DepotDownloader"

    /**
     * Forgets that memory when [directory] already holds files, so that the downloader checks each of them against the manifest, chunk by
     * chunk, and fetches again what is missing or wrong. Returns whether it did. A folder with nothing in it has nothing to be wrong about.
     */
    fun forgetForResume(directory: File): Boolean {
        val state = File(directory, FOLDER)
        if (!state.exists()) return false
        val holdsFiles = directory.walkTopDown().onEnter { it.name != FOLDER }.any { it.isFile }
        if (!holdsFiles) return false
        return state.deleteRecursively()
    }
}
