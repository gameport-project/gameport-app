package app.gameport.core.install

import java.io.File

/**
 * Keeps the figure of a download honest. The downloader's own figure runs ahead of what is on the device (seen on a resumed download: 99 % with 88 % of the bytes
 * written, then 96 % with 74 %). What the device holds is a ceiling for what is done, so the figure shown never goes above it, give or take a small
 * margin. A device that says nothing about it (0) leaves the downloader's figure as it is, and so does one that reserves each file in full (the ceiling is then the
 * whole and never lowers anything).
 */
internal object DownloadFigure {
    /** How far above the disk the downloader's figure may be before the disk's figure is shown instead: room for the blocks still in flight. */
    private const val TOLERANCE = 0.02f

    /** True when [claimed] is above what [onDisk] says of [total] bytes by more than the margin. A disk that says nothing (0) or a total that is not known never contradicts. */
    fun contradicts(claimed: Float, onDisk: Long, total: Long): Boolean {
        if (total <= 0 || onDisk <= 0) return false
        return claimed - fractionOf(onDisk, total) > TOLERANCE
    }

    fun fractionOf(onDisk: Long, total: Long): Float = (onDisk.toDouble() / total).toFloat().coerceIn(0f, 1f)

    /** The bytes the files under [directory] really take on the device (the blocks they hold, not their length, which a reserved file has in full), or 0 when that cannot be read. */
    fun allocatedBytes(directory: File, skippedFolders: Set<String>): Long = runCatching {
        directory.walkTopDown().onEnter { it.name !in skippedFolders }.filter { it.isFile && !it.name.startsWith(".") }.sumOf { file ->
            android.system.Os.stat(file.path).st_blocks * BLOCK_BYTES
        }
    }.getOrDefault(0L)

    private const val BLOCK_BYTES = 512L
}
