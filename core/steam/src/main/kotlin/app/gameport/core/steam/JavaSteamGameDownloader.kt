package app.gameport.core.steam

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
import kotlinx.coroutines.withContext

@Singleton
class JavaSteamGameDownloader @Inject constructor(
    private val sessions: SteamSessionHolder,
) : GameDownloader {
    override suspend fun download(appId: Int, depotIds: List<Int>, directory: File, onProgress: (Float, Long) -> Unit) {
        withContext(Dispatchers.IO) {
            val session = sessions.current.value ?: error("Not signed in to Steam")
            val startBytes = session.receivedBytes(depotIds)
            directory.mkdirs()
            val failure = AtomicReference<Throwable?>()
            // Each depot reports the fraction of its own download; the overall figure is their mean.
            val fractionByDepot = ConcurrentHashMap<Int, Float>()

            session.newDepotDownloader().use { downloader ->
                downloader.addListener(object : IDownloadListener {
                    override fun onChunkCompleted(
                        depotId: Int,
                        depotPercentComplete: Float,
                        compressedBytes: Long,
                        uncompressedBytes: Long,
                    ) {
                        fractionByDepot[depotId] = depotPercentComplete
                        // What came over the network, so files checked on disk (a resumed download) do not count.
                        onProgress(fractionByDepot.values.average().toFloat().coerceIn(0f, 1f), session.receivedBytes(depotIds) - startBytes)
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
                downloader.getCompletion().await()
            }
            failure.get()?.let { throw it }
        }
    }
}
