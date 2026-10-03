package app.gameport.core.model

/**
 * The names of a game's expansion files. A file is named after the version code of the APK it belongs to
 * (`main.<code>.<package>.obb`, `patch.<code>...`, and for the largest Unreal games `overflow1.<code>...`, `overflow2...`), and the game
 * looks for exactly that name.
 */
object ObbNames {
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
