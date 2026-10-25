package app.gameport.core.install

/**
 * Files a developer's computer leaves next to the real ones and a depot sometimes publishes by mistake: the `._name` companion that macOS writes beside each file
 * on a drive it cannot store attributes on, the `__MACOSX` folder of a zip, `.DS_Store`. They are not what their name or extension says (a `._game.apk` is not an APK),
 * so they are never installed, patched or placed on the device.
 */
internal object DepotJunk {
    /** True for a path, relative to the download, that is one of those files or inside one of those folders. */
    fun isJunk(relativePath: String): Boolean = relativePath.split('/', '\\').any { it.startsWith("._") || it == "__MACOSX" || it == ".DS_Store" }
}
