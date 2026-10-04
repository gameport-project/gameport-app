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
     * One file that fails does not stop the others. Each file is made readable by all.
     */
    fun place(downloaded: File, target: File, packageName: String, skipped: String): List<String> {
        val files = downloaded.walkTopDown()
            .onEnter { it.name != skipped }
            .filter { it.isFile }
            .mapNotNull { file -> ObbNames.placement(file.relativeTo(downloaded).path, packageName)?.let { file to it } }
            .toList()
            .let { all ->
                // A depot with an obb folder says what goes to the device: that folder, and the expansion files wherever they are.
                val inFolder = all.filter { (file, _) -> ObbNames.inObbFolder(file.relativeTo(downloaded).path, packageName) }
                if (inFolder.isEmpty()) all else all.filter { (file, _) -> file in inFolder.map { it.first } || file.name.endsWith(".obb", ignoreCase = true) }
            }
        if (files.isEmpty()) return emptyList()
        target.mkdirs()
        // A new version of the game replaces the expansion files of the old one, whatever code they were renamed to.
        ObbNames.superseded(target.list().orEmpty().toList(), files.map { it.second }, packageName).forEach { File(target, it).delete() }
        val placed = mutableListOf<String>()
        for ((file, relative) in files) {
            runCatching {
                val destination = File(target, relative).also { it.parentFile?.mkdirs() }
                if (destination.exists()) destination.delete()
                if (!file.renameTo(destination)) {
                    file.copyTo(destination, overwrite = true)
                    file.delete()
                }
                // A file that GamePort puts there is GamePort's, with a mode that keeps everyone else out; the game is not its owner, and
                // can only read it when it is open to all (Android treats the `.obb` apart, which is why those were never a problem).
                destination.setReadable(true, false)
            }.onSuccess { placed += relative }
        }
        return placed
    }

    /**
     * Renames the expansion files of [directory] to follow [code], the version code of the APK installed now (see [ObbNames.aligned]).
     * A file is not renamed over another that already has the name; when that other is the same size it is the same file twice, and the
     * old name is removed. What is there is also opened to all, so a game that was placed before that was done can read it.
     * Returns the names it changed, and does nothing when the folder is not there.
     */
    fun align(directory: File, packageName: String, code: Long): List<String> {
        val renamed = mutableListOf<String>()
        directory.listFiles()?.forEach { file ->
            val name = ObbNames.aligned(file.name, packageName, code) ?: return@forEach
            val destination = File(directory, name)
            when {
                !destination.exists() -> if (file.renameTo(destination)) renamed += name
                file.length() == destination.length() -> file.delete()
            }
        }
        directory.walkTopDown().filter { it.isFile && !it.canReadByAll() }.forEach { it.setReadable(true, false) }
        return renamed
    }

    private fun File.canReadByAll(): Boolean = runCatching {
        java.nio.file.attribute.PosixFilePermission.OTHERS_READ in java.nio.file.Files.getPosixFilePermissions(toPath())
    }.getOrDefault(true)
}
