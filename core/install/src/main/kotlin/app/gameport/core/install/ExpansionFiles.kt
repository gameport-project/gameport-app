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
     * One file that fails does not stop the others. Each file is made readable by all. What went wrong is added to [problems], one line each (the file and the
     * error), so a report can say why nothing was placed instead of saying there was nothing to place.
     */
    fun place(
        downloaded: File,
        target: File,
        packageName: String,
        skipped: String,
        problems: MutableList<String> = mutableListOf(),
        /** False for extra content added to a game: what it brings does not replace the game's own files. */
        replaceOlder: Boolean = true,
    ): List<String> {
        val files = select(downloaded, packageName, skipped)
        if (files.isEmpty()) return emptyList()
        if (!target.mkdirs() && !target.isDirectory) problems += "the folder ${target.path} cannot be created (exists=${target.exists()}, parent writable=${target.parentFile?.canWrite()})"
        // A new version of the game replaces the expansion files of the old one, whatever code they were renamed to.
        if (replaceOlder) ObbNames.superseded(target.list().orEmpty().toList(), files.map { it.second }, packageName).forEach { File(target, it).delete() }
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
            }.onSuccess { placed += relative }.onFailure { problems += "$relative (${file.length()} bytes): ${it.javaClass.simpleName}: ${it.message}" }
        }
        // The folders made on the way are open to the game too, not only the files in them.
        openToAll(target)
        return placed
    }

    /** The files of [downloaded] that go to the device, each with the path it takes under the game's folder. */
    fun select(downloaded: File, packageName: String, skipped: String): List<Pair<File, String>> {
        val files = downloaded.walkTopDown().onEnter { it.name != skipped }.filter { it.isFile }.associateBy { it.relativeTo(downloaded).path }.filterKeys { !DepotJunk.isJunk(it) }
        return ObbNames.selectPlaced(files.keys.toList(), packageName).map { (path, placed) -> files.getValue(path) to placed }
    }

    /** What [place] is about to put on the device, in bytes: measured before the files move, since afterwards the download no longer holds them. */
    fun bytesToPlace(downloaded: File, packageName: String, skipped: String): Long = select(downloaded, packageName, skipped).sumOf { it.first.length() }

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
        openToAll(directory)
        return renamed
    }

    /**
     * Opens what is under [directory] to every app: a file to be read, a folder to be read and entered. What GamePort makes there is GamePort's, with a mode that keeps everyone
     * else out, so a game cannot open a file of its own expansion that lies in a folder GamePort made (`assets/` of Escape Simulator): it finds the folder shut, its asset bundles
     * "cannot be opened", and starts without its VR rig and its texts. [directory] itself is the game's own folder, made by the system, and is left as it is. Safe to run again.
     */
    fun openToAll(directory: File) {
        directory.walkTopDown().forEach { entry ->
            when {
                entry == directory -> Unit
                entry.isDirectory -> if (!entry.canEnterByAll()) {
                    entry.setReadable(true, false)
                    entry.setExecutable(true, false)
                }
                entry.isFile -> if (!entry.canReadByAll()) entry.setReadable(true, false)
            }
        }
    }

    private fun File.canReadByAll(): Boolean = runCatching {
        java.nio.file.attribute.PosixFilePermission.OTHERS_READ in java.nio.file.Files.getPosixFilePermissions(toPath())
    }.getOrDefault(true)

    private fun File.canEnterByAll(): Boolean = runCatching {
        val modes = java.nio.file.Files.getPosixFilePermissions(toPath())
        java.nio.file.attribute.PosixFilePermission.OTHERS_READ in modes && java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE in modes
    }.getOrDefault(true)
}
