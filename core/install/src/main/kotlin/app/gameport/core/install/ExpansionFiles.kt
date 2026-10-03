package app.gameport.core.install

import app.gameport.core.model.ObbNames
import java.io.File

/**
 * Puts what a game came with besides its APK where the game looks for it: `Android/obb/<package>`. A game that has expansion files
 * (`.obb`), or data too large for its APK, expects them there; once the install is done the download folder is deleted, so whatever is
 * not moved is lost.
 */
internal object ExpansionFiles {
    /**
     * Moves every file of [downloaded] that belongs next to the game into [target] (see [ObbNames.placement]), and returns the paths it
     * placed, relative to [target]. A game whose download holds only its APK has nothing to move and [target] is not even created.
     * [skipped] names a folder of [downloaded] that holds the patched APKs and is left alone. A file is moved when it can be, so one of
     * several gigabytes is not copied while its download is still on the disk; it is copied and removed when it cannot.
     * One file that fails does not stop the others.
     */
    fun place(downloaded: File, target: File, packageName: String, skipped: String): List<String> {
        val files = downloaded.walkTopDown()
            .onEnter { it.name != skipped }
            .filter { it.isFile }
            .mapNotNull { file -> ObbNames.placement(file.relativeTo(downloaded).path, packageName)?.let { file to it } }
            .toList()
        if (files.isEmpty()) return emptyList()
        target.mkdirs()
        val placed = mutableListOf<String>()
        for ((file, relative) in files) {
            runCatching {
                val destination = File(target, relative).also { it.parentFile?.mkdirs() }
                if (destination.exists()) destination.delete()
                if (!file.renameTo(destination)) {
                    file.copyTo(destination, overwrite = true)
                    file.delete()
                }
            }.onSuccess { placed += relative }
        }
        return placed
    }
}
