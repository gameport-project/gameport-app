package app.gameport.core.steam

import java.io.File

interface GameDownloader {
    /**
     * Downloads exactly the given depots of [appId] (its Android build) into [directory]. [depotBytes] is what each depot weighs (its download size), so the 0..1 progress follows the bytes and not the number of depots; it is
     * the same weight for every depot when it is not given. [onProgress] gets the 0..1 progress and the bytes received from the network so far in this run. Throws if the download fails; cancelling the
     * calling coroutine stops it.
     */
    /**
     * The files Steam publishes for [manifestId] of [depotId] (path and size, directories left out), or null when they cannot be read (offline, not signed in, a depot the
     * account has no right to). The same list the download reads, so what a depot holds is known without fetching any of it.
     */
    suspend fun manifestFiles(appId: Int, depotId: Int, manifestId: Long): List<Pair<String, Long>>? = null

    suspend fun download(
        appId: Int,
        depotIds: List<Int>,
        directory: File,
        depotBytes: Map<Int, Long> = emptyMap(),
        onProgress: (fraction: Float, bytesReceived: Long) -> Unit,
    )

    /**
     * Downloads the Workshop item [itemId] of [appId] into [directory]: an item is the depot of its game (the depot has the game's number) at the manifest [manifestId] that the item's record gives. [onProgress] gets the
     * 0..1 progress. Throws if the download fails; cancelling the calling coroutine stops it.
     */
    suspend fun downloadWorkshopItem(appId: Int, itemId: Long, manifestId: Long, directory: File, onProgress: (fraction: Float) -> Unit) {
        error("Workshop items cannot be downloaded here")
    }

    /** The Workshop items of [appId] the signed-in account is subscribed to (read only), or null when they cannot be read. */
    suspend fun subscribedWorkshopItems(appId: Int): List<Long>? = null
}
