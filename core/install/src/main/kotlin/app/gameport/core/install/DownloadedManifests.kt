package app.gameport.core.install

import java.io.File

/**
 * The manifests a download really holds. The downloader keeps one file per depot, named `<depot>_<manifest>.manifest`, in its own folder: that is the build that
 * was downloaded, whatever the list read earlier from Steam said (a list kept from before the game was updated names an older one). The build recorded for the
 * game is this one, so the game is not offered the same update again once it is installed.
 */
object DownloadedManifests {
    private val NAME = Regex("""^(\d+)_(\d+)\.manifest$""")

    fun read(downloadDirectory: File): Map<Int, Long> =
        File(downloadDirectory, ".DepotDownloader").listFiles().orEmpty().mapNotNull { file ->
            val match = NAME.matchEntire(file.name) ?: return@mapNotNull null
            val depot = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val manifest = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            depot to manifest
        }.toMap()
}
