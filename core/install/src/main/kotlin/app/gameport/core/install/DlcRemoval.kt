package app.gameport.core.install

import app.gameport.core.model.ObbNames

/**
 * Which files taking an extra content off the device may delete, worked out from the lists of files Steam publishes (one per depot) and nothing else. A file is only
 * deleted when it is in the list of the extra content, and in no list of what stays (the game itself, the other extra content): so a file that the game also holds is never
 * touched. Each file is given the path it has on the device (see [ObbNames.placement]), with the size the list says, to tell the same file from another of the same name.
 */
internal object DlcRemoval {
    data class Planned(val relativePath: String, val size: Long)

    /**
     * The files of [dlcFiles] (path in the depot, size) to take off: those that are placed on the device ([packageName] decides how), and are not placed under the same name by
     * [keptFiles] (the paths of every depot that stays) or [keptPlaced] (paths already on the device that belong to what stays).
     */
    fun plan(dlcFiles: List<Pair<String, Long>>, keptFiles: Collection<String>, keptPlaced: Collection<String>, packageName: String): List<Planned> {
        val kept = keptFiles.mapNotNull { ObbNames.placement(it, packageName) }.toHashSet() + keptPlaced
        return dlcFiles.mapNotNull { (path, size) -> ObbNames.placement(path, packageName)?.let { Planned(it, size) } }
            .filter { it.relativePath !in kept }
            .distinctBy { it.relativePath }
    }
}
