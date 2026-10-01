package app.gameport.core.steam

import java.io.File

interface GameDownloader {
    /**
     * Downloads exactly the given depots of [appId] (its Android build) into [directory]. [onProgress] gets the 0..1 progress and the bytes received from the network so far in this run. Throws if the download fails; cancelling the
     * calling coroutine stops it.
     */
    suspend fun download(appId: Int, depotIds: List<Int>, directory: File, onProgress: (fraction: Float, bytesReceived: Long) -> Unit)
}
