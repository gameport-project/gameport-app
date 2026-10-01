package app.gameport.core.steam.session

import java.io.File
import java.io.IOException
import java.util.Date
import java.util.zip.ZipInputStream
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_ClientDeleteFile_Request
import `in`.dragonbra.javasteam.rpc.service.Cloud
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A local file to send to Steam Cloud under [cloudName]. */
class CloudUpload(val cloudName: String, val file: File, val sha1: ByteArray, val timestampMillis: Long)

private const val USER_AGENT = "Valve/Steam HTTP Client 1.0"

private fun url(https: Boolean, host: String, path: String) = "${if (https) "https" else "http"}://$host$path"

/** Downloads one cloud file to [target] and gives it the timestamp Steam holds. False on any failure. */
suspend fun SteamSession.downloadCloudFile(appId: Int, cloudName: String, target: File): Boolean =
    withContext(Dispatchers.IO) {
        val info = runCatching { cloud.clientFileDownload(appId, cloudName).await() }.getOrNull() ?: return@withContext false
        if (info.urlHost.isEmpty()) return@withContext false

        val request = Request.Builder()
            .url(url(info.useHttps, info.urlHost, info.urlPath))
            .headers(Headers.headersOf(*info.requestHeaders.flatMap { listOf(it.name, it.value) }.toTypedArray()))
            .build()
        try {
            client.configuration.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                target.parentFile?.mkdirs()
                // When the sizes differ Steam sends the file as a one-entry zip.
                val written = body.byteStream().use { raw ->
                    val input = if (info.fileSize != info.rawFileSize) ZipInputStream(raw).also { it.nextEntry } else raw
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                if (written != info.rawFileSize.toLong()) {
                    target.delete()
                    return@withContext false
                }
                target.setLastModified(info.timestamp.time)
                true
            }
        } catch (e: IOException) {
            target.delete()
            false
        }
    }

/**
 * Sends [uploads] (and removes [deletes]) as one Steam Cloud batch. Returns the app's new change
 * number, or null if any part failed, in which case Steam is told the batch failed.
 */
suspend fun SteamSession.uploadToCloud(
    appId: Int,
    uploads: List<CloudUpload>,
    deletes: List<String>,
    machineName: String,
    clientId: Long,
): Long? = withContext(Dispatchers.IO) {
    val batch = cloud.beginAppUploadBatch(
        appId = appId,
        machineName = machineName,
        filesToUpload = uploads.map { it.cloudName },
        filesToDelete = deletes,
        clientId = clientId,
        appBuildId = 0,
    ).await()

    var success = true
    // Naming a file in the batch is not enough for Steam to drop it: each deletion is also
    // requested on its own, inside the batch.
    if (deletes.isNotEmpty()) {
        val cloudService = client.getHandler(SteamUnifiedMessages::class.java)!!.createService<Cloud>()
        for (name in deletes) {
            val request = CCloud_ClientDeleteFile_Request.newBuilder()
                .setAppid(appId)
                .setFilename(name)
                .setIsExplicitDelete(true)
                .setUploadBatchId(batch.batchID)
                .build()
            val deleted = runCatching { cloudService.clientDeleteFile(request).await() }.getOrNull()
            if (deleted == null || deleted.result != EResult.OK) success = false
        }
    }
    for (upload in uploads) {
        val size = upload.file.length().toInt()
        val info = cloud.beginFileUpload(
            appId = appId,
            fileSize = size,
            rawFileSize = size,
            fileSha = upload.sha1,
            timestamp = Date(upload.timestampMillis),
            filename = upload.cloudName,
            uploadBatchId = batch.batchID,
        ).await()

        var transferred = true
        upload.file.inputStream().use { input ->
            for (block in info.blockRequests) {
                val bytes = ByteArray(block.blockLength)
                input.channel.position(block.blockOffset)
                var read = 0
                while (read < bytes.size) {
                    val n = input.read(bytes, read, bytes.size - read)
                    if (n < 0) break
                    read += n
                }
                val contentType = block.requestHeaders.firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }?.value
                val request = Request.Builder()
                    .url(url(block.useHttps, block.urlHost, block.urlPath))
                    .put(bytes.toRequestBody((contentType ?: "application/octet-stream").toMediaTypeOrNull()))
                    .headers(Headers.headersOf(*block.requestHeaders.flatMap { listOf(it.name, it.value) }.toTypedArray()))
                    .header("User-Agent", USER_AGENT)
                    .build()
                val ok = try {
                    client.configuration.httpClient.newCall(request).execute().use { it.isSuccessful }
                } catch (e: IOException) {
                    false
                }
                if (!ok) transferred = false
            }
        }
        val committed = cloud.commitFileUpload(transferred, appId, upload.sha1, upload.cloudName).await()
        if (!transferred || !committed) success = false
    }

    cloud.completeAppUploadBatch(appId, batch.batchID, if (success) EResult.OK else EResult.Fail).await()
    batch.appChangeNumber.takeIf { success }
}
