package app.gameport.core.model

/**
 * The names of a game's expansion files. A file is named after the version code of the APK it belongs to
 * (`main.<code>.<package>.obb`, `patch.<code>...`, and for the largest Unreal games `overflow1.<code>...`, `overflow2...`), and the game
 * looks for exactly that name.
 *
 * A game installed under a name of GamePort's own ([RenamedPackages]) is the exception: the files a depot publishes are named after the package the
 * game's APK declares, and the game asks for the name it carries now. The functions that read what a depot or a folder holds take that other name
 * ([original]) so both are recognised, and everything is written under the installed one.
 */
object ObbNames {
    /** The parts an expansion file can play. */
    private const val ROLES = "main|patch|overflow\\d+"

    /** The names a game's own folder and expansion files may carry: the one it is installed under, and the one its APK declares when they differ. */
    private fun namesOf(packageName: String, original: String?): List<String> =
        if (original == null || original == packageName) listOf(packageName) else listOf(packageName, original)

    private fun expansion(names: List<String>) = Regex("^($ROLES)\\.(\\d+)\\.(${names.joinToString("|") { Regex.escape(it) }})\\.obb$")

    /** The same, with the version code left out when the game's file has none (`overflow1.<package>.obb`, as Red Matter 2 publishes it). */
    private fun expansionOrUnversioned(names: List<String>) = Regex("^($ROLES)(?:\\.(\\d+))?\\.(${names.joinToString("|") { Regex.escape(it) }})\\.obb$")

    /** True when one of [names] is an expansion file named after a version code (`main.<code>.<package>.obb` and its kind): the ones that the raised version of a patched game can leave unfound. */
    fun hasVersioned(names: Collection<String>, packageName: String): Boolean {
        val pattern = expansion(listOf(packageName))
        return names.any { pattern.matches(it) }
    }

    /**
     * The expansion files in [existing] that a new download replaces: those that play the same part (main, patch, or the same overflow
     * file) as one of the [placed] files under another version code. Left there, they would be mistaken for the new file when it is
     * renamed to follow the patched version, or would take room for nothing.
     */
    fun superseded(existing: Collection<String>, placed: Collection<String>, packageName: String, original: String? = null): List<String> {
        val pattern = expansion(namesOf(packageName, original))
        val roles = placed.mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1) }.toSet()
        if (roles.isEmpty()) return emptyList()
        return existing.filter { name ->
            val match = pattern.matchEntire(name) ?: return@filter false
            match.groupValues[1] in roles && name !in placed
        }
    }

    /**
     * The version code the expansion files must be named after: the one of the APK installed now, which patching raises (Unity reads that one), except for an
     * Unreal game, whose own code writes the version of its expansion file down when it is built and asks for exactly that, so its files keep the version the game
     * had before patching. Null when that version is not known: nothing is renamed then.
     */
    fun versionFor(installed: Long, original: Long?, unreal: Boolean): Long? = if (unreal) original else installed

    /**
     * The name [fileName] must have to belong to the APK of version [code] and to the package [packageName], or null when it is not an expansion file of the
     * game, or already has that name. [original] is the package the game's APK declares, when it is installed under another name: its files are recognised
     * under either, and renamed after the installed one.
     */
    fun aligned(fileName: String, packageName: String, code: Long, original: String? = null, keepNumbers: Boolean = false): String? {
        val match = expansionOrUnversioned(namesOf(packageName, original)).matchEntire(fileName)
        if (match != null) {
            val role = match.groupValues[1]
            val number = match.groupValues[2]
            // A file the game publishes with no version code is looked for without one: only the package changes. With [keepNumbers] (an Unreal game, whose own code writes the
            // version of each of its files down when it is built: `patch.0` stays `patch.0`) the number of a file is left as it is and only the package changes.
            val name = if (number.isEmpty()) "$role.$packageName.obb" else if (keepNumbers) "$role.$number.$packageName.obb" else "$role.$code.$packageName.obb"
            return name.takeIf { it != fileName }
        }
        if (match == null) {
            // A depot may publish the file under a name of its own (`<game>.main.obb`, for one), which the engine never looks for.
            val role = Regex("^(?:.*[._-])?(main|patch)\\.obb$", RegexOption.IGNORE_CASE).matchEntire(fileName)?.groupValues?.get(1) ?: return null
            return "${role.lowercase()}.$code.$packageName.obb"
        }
        if (match.groupValues[2] == code.toString() && match.groupValues[3] == packageName) return null
        return "${match.groupValues[1]}.$code.$packageName.obb"
    }

    /**
     * True when the depot itself puts [relativePath] in an obb folder (`obb/…`, `Android/obb/…`, or under the package's own folder). A depot
     * that has such a folder says what belongs there: the rest of it (the unpacked APK, the debug files of the build) is not for the device.
     */
    fun inObbFolder(relativePath: String, packageName: String, original: String? = null): Boolean {
        val segments = relativePath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return false
        return namesOf(packageName, original).any { it in segments.dropLast(1) } ||
            (segments.size > 2 && segments[0].equals("Android", ignoreCase = true) && segments[1].equals("obb", ignoreCase = true)) ||
            segments[0].equals("obb", ignoreCase = true)
    }

    /**
     * Of the [paths] of a depot or of a download, the ones that go to the device and what each is called there. A depot with an obb folder says what goes to the device:
     * that folder, and the expansion files wherever they are; without one, everything [placement] keeps.
     */
    fun selectPlaced(paths: List<String>, packageName: String, original: String? = null): List<Pair<String, String>> {
        val all = paths.mapNotNull { path -> placement(path, packageName, original)?.let { path to it } }
        val inFolder = all.filter { (path, _) -> inObbFolder(path, packageName, original) }.map { it.first }.toSet()
        return if (inFolder.isEmpty()) all else all.filter { (path, _) -> path in inFolder || path.endsWith(".obb", ignoreCase = true) }
    }

    /**
     * Where a file of a downloaded game goes under `Android/obb/<package>`, as a path relative to that folder, or null when it does
     * not belong there (an APK, or what the downloader keeps for itself). A game that has data too large for its APK expects it
     * there, under the name it was published with (`main_assets_all.bundle`, for one); an expansion file (`.obb`) goes at the top.
     * The depot may spell the way to the folder out (`Android/obb/<package>/…`, `obb/…`), which is not repeated.
     */
    fun placement(relativePath: String, packageName: String, original: String? = null): String? {
        val segments = relativePath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty() || segments.any { it.startsWith(".") }) return null
        val name = segments.last()
        if (name.endsWith(".apk", ignoreCase = true)) return null
        if (name.endsWith(".obb", ignoreCase = true)) return name
        val names = namesOf(packageName, original)
        val own = segments.indexOfFirst { it in names }
        val inside = when {
            own >= 0 -> segments.drop(own + 1)
            segments.size > 1 && segments[0].equals("Android", ignoreCase = true) && segments[1].equals("obb", ignoreCase = true) -> segments.drop(2)
            segments[0].equals("obb", ignoreCase = true) -> segments.drop(1)
            else -> segments
        }
        return inside.takeIf { it.isNotEmpty() }?.joinToString("/")
    }
}
