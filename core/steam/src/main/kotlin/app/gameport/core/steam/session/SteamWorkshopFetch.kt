package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.steam.cdn.Client
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout

/**
 * Downloads the files of one depot at one manifest into a folder, chunk by chunk, with the account's own session. A Workshop item is the depot of its game at the manifest its
 * record names; the downloader of the library asks Steam for the item's details first, with calls that are not answered here, so the manifest is read the way
 * [SteamInspector] reads it and the chunks are fetched from the same servers.
 */
object SteamWorkshopFetch {
    /** Each step with Steam gets this long; a step that does not answer in time ends the download, and the player is told. */
    private const val STEP_TIMEOUT_MS = 30_000L

    suspend fun fetch(session: SteamSession, appId: Int, depotId: Int, manifestId: Long, directory: File, onProgress: (Float) -> Unit) = coroutineScope {
        val content = session.client.getHandler(SteamContent::class.java) ?: throw IOException("No content handler")
        val key = withTimeout(STEP_TIMEOUT_MS) { session.apps.getDepotDecryptionKey(depotId, appId).await().depotKey }
        val servers = withTimeout(STEP_TIMEOUT_MS) { content.getServersForSteamPipe(null, 8, this@coroutineScope).await() }
        if (servers.isEmpty()) throw IOException("Steam named no server to download from")
        val code = withTimeout(STEP_TIMEOUT_MS) { content.getManifestRequestCode(depotId, appId, manifestId, "public", null, this@coroutineScope).await() }
        // Not closed: the client shares the session's HTTP client, and closing it would stop every request GamePort makes afterwards.
        val cdn = Client(session.client)
        var manifest: `in`.dragonbra.javasteam.types.DepotManifest? = null
        var failure: Throwable? = null
        for (server in servers) {
            try {
                manifest = withTimeout(STEP_TIMEOUT_MS) { cdn.downloadManifestFuture(depotId, manifestId, code, server, key).await() }
                break
            } catch (e: Exception) {
                failure = e
            }
        }
        if (manifest == null) throw IOException("The manifest could not be read: ${failure?.message}")

        val root = directory.canonicalFile
        val files = manifest.files
        val total = files.sumOf { it.totalSize }.coerceAtLeast(1L)
        var done = 0L
        for (file in files) {
            val target = File(root, file.fileName.replace('\\', '/')).canonicalFile
            // A name that leads out of the folder is never written.
            if (target.path != root.path && !target.path.startsWith(root.path + File.separator)) throw IOException("A file of the item leaves its folder: ${file.fileName}")
            if (file.flags.contains(EDepotFileFlag.Directory)) {
                target.mkdirs()
                continue
            }
            target.parentFile?.mkdirs()
            RandomAccessFile(target, "rw").use { out ->
                out.setLength(file.totalSize)
                for (chunk in file.chunks) {
                    // The library decrypts in place: the buffer must hold the compressed size as well as the final one.
                    val buffer = ByteArray(maxOf(chunk.compressedLength, chunk.uncompressedLength) + 64)
                    var written = -1
                    var chunkFailure: Throwable? = null
                    for (server in servers.take(4)) {
                        try {
                            written = withTimeout(STEP_TIMEOUT_MS) { cdn.downloadDepotChunkFuture(depotId, chunk, server, buffer, key).await() }
                            break
                        } catch (e: Exception) {
                            chunkFailure = e
                        }
                    }
                    if (written < 0) throw IOException("A part of ${file.fileName} could not be downloaded: ${chunkFailure?.message}")
                    out.seek(chunk.offset)
                    out.write(buffer, 0, written)
                    done += written
                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
    }
}
