package app.gameport.core.model

/**
 * The names of a game's expansion files. A file is named after the version code of the APK it belongs to
 * (`main.<code>.<package>.obb`, `patch.<code>...`, and for the largest Unreal games `overflow1.<code>...`, `overflow2...`), and the game
 * looks for exactly that name.
 */
object ObbNames {
    private fun expansion(packageName: String) = Regex("^(main|patch|overflow\\d+)\\.(\\d+)\\.${Regex.escape(packageName)}\\.obb$")

    /**
     * The expansion files in [existing] that a new download replaces: those that play the same part (main, patch, or the same overflow
     * file) as one of the [placed] files under another version code. Left there, they would be mistaken for the new file when it is
     * renamed to follow the patched version, or would take room for nothing.
     */
    fun superseded(existing: Collection<String>, placed: Collection<String>, packageName: String): List<String> {
        val pattern = expansion(packageName)
        val roles = placed.mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1) }.toSet()
        if (roles.isEmpty()) return emptyList()
        return existing.filter { name ->
            val match = pattern.matchEntire(name) ?: return@filter false
            match.groupValues[1] in roles && name !in placed
        }
    }

    /**
     * The name [fileName] must have to belong to the APK of version [code], or null when it is not an expansion file of [packageName],
     * or already has that name. Patching raises the version code of the APK, so its expansion files follow it.
     */
    fun aligned(fileName: String, packageName: String, code: Long): String? {
        val match = Regex("^(main|patch|overflow\\d+)\\.(\\d+)\\.${Regex.escape(packageName)}\\.obb$").matchEntire(fileName) ?: return null
        if (match.groupValues[2] == code.toString()) return null
        return "${match.groupValues[1]}.$code.$packageName.obb"
    }

    /**
     * Where a file of a downloaded game goes under `Android/obb/<package>`, as a path relative to that folder, or null when it does
     * not belong there (an APK, or what the downloader keeps for itself). A game that has data too large for its APK expects it
     * there, under the name it was published with (`main_assets_all.bundle`, for one); an expansion file (`.obb`) goes at the top.
     * The depot may spell the way to the folder out (`Android/obb/<package>/…`, `obb/…`), which is not repeated.
     */
    /**
     * True when the depot itself puts [relativePath] in an obb folder (`obb/…`, `Android/obb/…`, or under the package's own folder). A depot
     * that has such a folder says what belongs there: the rest of it (the unpacked APK, the debug files of the build) is not for the device.
     */
    fun inObbFolder(relativePath: String, packageName: String): Boolean {
        val segments = relativePath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return false
        return packageName in segments.dropLast(1) ||
            (segments.size > 2 && segments[0].equals("Android", ignoreCase = true) && segments[1].equals("obb", ignoreCase = true)) ||
            segments[0].equals("obb", ignoreCase = true)
    }

    fun placement(relativePath: String, packageName: String): String? {
        val segments = relativePath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty() || segments.any { it.startsWith(".") }) return null
        val name = segments.last()
        if (name.endsWith(".apk", ignoreCase = true)) return null
        if (name.endsWith(".obb", ignoreCase = true)) return name
        val inside = when {
            packageName in segments -> segments.drop(segments.indexOf(packageName) + 1)
            segments.size > 1 && segments[0].equals("Android", ignoreCase = true) && segments[1].equals("obb", ignoreCase = true) -> segments.drop(2)
            segments[0].equals("obb", ignoreCase = true) -> segments.drop(1)
            else -> segments
        }
        return inside.takeIf { it.isNotEmpty() }?.joinToString("/")
    }
}
