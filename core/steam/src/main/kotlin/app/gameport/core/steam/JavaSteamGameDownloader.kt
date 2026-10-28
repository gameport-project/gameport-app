package app.gameport.core.steam

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import app.gameport.core.steam.session.SteamSessionHolder
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class JavaSteamGameDownloader @Inject constructor(
    private val sessions: SteamSessionHolder,
) : GameDownloader {
    override suspend fun manifestFiles(appId: Int, depotId: Int, manifestId: Long): List<Pair<String, Long>>? = withContext(Dispatchers.IO) {
        val session = sessions.current.value ?: return@withContext null
        app.gameport.core.steam.session.SteamInspector.manifestFiles(session, appId, depotId, manifestId)
    }

    override suspend fun download(appId: Int, depotIds: List<Int>, directory: File, depotBytes: Map<Int, Long>, onProgress: (Float, Long) -> Unit) {
        withContext(Dispatchers.IO) {
            // Right after GamePort starts, the connection to Steam may not be made yet (a download that goes on by itself starts as soon as the account is known): it is waited for.
            val session = sessions.current.value
                ?: kotlinx.coroutines.withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() }
                ?: error("Not signed in to Steam")
            val startBytes = session.receivedBytes(depotIds)
            directory.mkdirs()
            // Resuming: the downloader must check what is already there, not believe its own notes (see [DownloadState]).
            DownloadState.forgetForResume(directory)
            val failure = AtomicReference<Throwable?>()
            // Each depot reports the fraction of its own download; the overall figure weighs each by its size.
            val fractionByDepot = ConcurrentHashMap<Int, Float>()

            // The speed shown is worked out from what was received over time, so it is told at a steady pace and not only when a chunk completes.
            val latest = java.util.concurrent.atomic.AtomicReference(NO_PROGRESS_YET)
            var ticks = 0
            val ticker = launch {
                while (true) {
                    delay(PROGRESS_TICK_MS)
                    if (++ticks % THREAD_REFRESH_TICKS == 0) DownloadThreads.refresh()
                    onProgress(latest.get(), session.receivedBytes(depotIds) - startBytes)
                }
            }
            session.newDepotDownloader().use { downloader ->
                downloader.addListener(object : IDownloadListener {
                    override fun onChunkCompleted(
                        depotId: Int,
                        depotPercentComplete: Float,
                        compressedBytes: Long,
                        uncompressedBytes: Long,
                    ) {
                        fractionByDepot[depotId] = depotPercentComplete
                        // What came over the network, so files checked on disk (a resumed download) do not count. Told by the ticker below, twice a second: telling it at every chunk
                        // (dozens a second on a fast link) made the screens redraw as often, and the app lagged while a game downloaded.
                        latest.set(DownloadProgress.overall(depotIds, fractionByDepot, depotBytes))
                    }

                    override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                        failure.set(error)
                    }
                })
                // Named depots only. Filtering by operating system would also pick up untagged
                // depots, which are the Windows build.
                require(depotIds.isNotEmpty()) { "No Android depot to download." }
                downloader.add(AppItem(appId, installDirectory = directory.absolutePath, depot = depotIds))
                downloader.finishAdding()
                // The worker threads run at background priority while this goes on, and only then (see [DownloadThreads]).
                DownloadThreads.begin()
                try {
                    downloader.getCompletion().await()
                } finally {
                    ticker.cancel()
                    DownloadThreads.end()
                }
            }
            failure.get()?.let { throw it }
        }
    }

    override suspend fun subscribedWorkshopItems(appId: Int): List<Long>? = withContext(Dispatchers.IO) {
        val session = sessions.current.value ?: return@withContext null
        app.gameport.core.steam.session.SteamInspector.subscribedWorkshopIds(session, appId)
    }

    override suspend fun downloadWorkshopItem(appId: Int, itemId: Long, manifestId: Long, directory: File, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val session = sessions.current.value
                ?: kotlinx.coroutines.withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() }
                ?: error("Not signed in to Steam")
            require(manifestId > 0L) { "Steam gave no content for this item" }
            directory.mkdirs()
            android.util.Log.i("GPWorkshop", "item $itemId: fetching depot $appId at manifest $manifestId")
            app.gameport.core.steam.session.SteamWorkshopFetch.fetch(session, appId, appId, manifestId, directory, onProgress)
            android.util.Log.i("GPWorkshop", "item $itemId: fetched")
        }
    }
}

/** How often the progress and the received bytes are told to the install: the downloader reports at every chunk, which is far too often for a screen. */
private const val PROGRESS_TICK_MS = 500L

/** The pool starts threads as it needs them: the ones it started since are lowered every this many ticks. */
private const val THREAD_REFRESH_TICKS = 4

/** Told before any chunk is complete: the progress is not known yet, and the one the install already shows (a resumed download) stays. */
const val NO_PROGRESS_YET = -1f

private const val SESSION_WAIT_MS = 30_000L

/** A Workshop item that has shown nothing for this long is given up. */
private const val WORKSHOP_STALL_MS = 30_000L
