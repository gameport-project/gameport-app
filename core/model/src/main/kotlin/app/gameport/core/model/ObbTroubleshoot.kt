package app.gameport.core.model

/**
 * The checks of the expansion files of an Unreal game, as plain functions of what lies in its `Android/obb/<package>` folder. They serve the repair that runs by itself
 * (see [app.gameport.core.install.ExpansionFiles.align]) and the troubleshooting the player asks for.
 *
 * An Unreal game writes the version of each of its expansion files down when it is built and asks for exactly that name: `main.<version of its APK>.<package>.obb` and a patch
 * named `patch.0.<package>.obb`. An earlier GamePort renamed every file after the version code it had then, which sent such a game to its expansion downloader (it shows a
 * black screen and closes, as Red Matter 2 did). The renamings below put those files back, and only those: a number that is neither the version code the game has now nor
 * the one it came with is a number the game's own depot gave, and is left alone.
 */
object ObbTroubleshoot {
    /** A file to rename inside the game's folder: [from] is the name it has, [to] the one the game asks for. */
    data class Rename(val from: String, val to: String)

    private const val EXPECTED_PATCH_NUMBER = "0"

    private fun pattern(packageName: String, original: String?): Regex {
        val names = if (original == null || original == packageName) listOf(packageName) else listOf(packageName, original)
        return Regex("^(main|patch)\\.(\\d+)\\.(${names.joinToString("|") { Regex.escape(it) }})\\.obb$")
    }

    /**
     * The renamings that put an Unreal game's files back under the names it asks for, from the [files] of its folder (name to size). Nothing for a game that is not Unreal, or whose
     * version code it came with is not known. Never a name that is already there, and nothing when two files could take the same place: a doubt is left as it is.
     *
     * - A patch named after a version code (`patch.<code>.<package>.obb`, the code being the one the game has now or came with) becomes `patch.0.<package>.obb`.
     * - A main file named after the raised version code, when the game asks for the one it came with, becomes `main.<that one>.<package>.obb`.
     *
     * Run again on the result, it finds nothing more.
     */
    fun renames(files: Map<String, Long>, packageName: String, original: String?, installedCode: Long?, originalCode: Long?, unreal: Boolean): List<Rename> {
        if (!unreal || originalCode == null) return emptyList()
        val regex = pattern(packageName, original)
        val matches = files.keys.mapNotNull { name -> regex.matchEntire(name)?.let { name to it } }
        val taken = files.keys.toMutableSet()
        val result = mutableListOf<Rename>()

        fun add(from: String, to: String) {
            if (to in taken) return
            taken += to
            result += Rename(from, to)
        }

        val patches = matches.filter { it.second.groupValues[1] == "patch" }
        val strayPatches = patches.filter { (_, m) ->
            val number = m.groupValues[2]
            number != EXPECTED_PATCH_NUMBER && (number == originalCode.toString() || number == installedCode?.toString())
        }
        // A patch with the number the game asks for exists already (under either name of the package): the others are not touched.
        val hasPatchZero = patches.any { it.second.groupValues[2] == EXPECTED_PATCH_NUMBER }
        if (!hasPatchZero && strayPatches.size == 1 && patches.size == 1) add(strayPatches.single().first, "patch.$EXPECTED_PATCH_NUMBER.$packageName.obb")

        val mains = matches.filter { it.second.groupValues[1] == "main" }
        val wantedMain = originalCode.toString()
        val hasWantedMain = mains.any { it.second.groupValues[2] == wantedMain }
        val raised = installedCode?.takeIf { it != originalCode }?.toString()
        if (!hasWantedMain && raised != null && mains.size == 1 && mains.single().second.groupValues[2] == raised) add(mains.single().first, "main.$wantedMain.$packageName.obb")
        return result
    }

    /** Why an Unreal game's expansion files cannot be put right by renaming them: they are not there, or one is empty. */
    enum class ReinstallReason { NO_MAIN_FILE, EMPTY_FILE, REPORTED_MISSING }

    /**
     * Why the game has to be given its expansion files again, or null when nothing says so. [expectsExpansion] is true when the game has expansion files (what Steam lists for it,
     * or what was written down at its install). Only for an Unreal game: another engine's folder is not read here.
     */
    fun reinstallReason(files: Map<String, Long>, packageName: String, original: String?, unreal: Boolean, expectsExpansion: Boolean): ReinstallReason? {
        if (!unreal || !expectsExpansion) return null
        val regex = Regex("^(main|patch|overflow\\d+)(?:\\.(\\d+))?\\.(${listOfNotNull(packageName, original).distinct().joinToString("|") { Regex.escape(it) }})\\.obb$")
        val expansion = files.filterKeys { regex.matches(it) }
        if (expansion.keys.none { it.startsWith("main.") }) return ReinstallReason.NO_MAIN_FILE
        if (expansion.values.any { it == 0L }) return ReinstallReason.EMPTY_FILE
        return null
    }
}
