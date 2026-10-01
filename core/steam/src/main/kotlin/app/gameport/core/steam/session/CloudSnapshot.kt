package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.protobufs.steamclient.Enums.ECloudStoragePersistState
import kotlinx.coroutines.future.await

/** One file as Steam Cloud lists it for an app. [name] is the cloud name, prefix included. */
data class CloudFileEntry(
    val name: String,
    val sha1: String,
    val timestampMillis: Long,
    val sizeBytes: Int,
    /** Steam keeps a record of deleted files; such a file no longer exists. */
    val deleted: Boolean = false,
    /** Steam's raw storage state (0 persisted, 1 forgotten, 2 deleted), for diagnostics. */
    val state: Int = 0,
)

/** What Steam Cloud holds for an app at one moment. */
data class CloudSnapshot(
    val changeNumber: Long,
    val files: List<CloudFileEntry>,
)

/**
 * Lists the app's cloud files. [sinceChange] 0 asks for everything; the cloud names carry a root
 * placeholder such as `%WinAppDataLocalLow%` in front of the path, exactly as the game's `ufs`
 * configuration declares it.
 */
suspend fun SteamSession.fetchCloud(appId: Int, sinceChange: Long = 0): CloudSnapshot {
    val list = cloud.getAppFileListChange(appId, sinceChange).await()
    val files = list.files.map { file ->
        val prefix = list.pathPrefixes.getOrNull(file.pathPrefixIndex).orEmpty()
        CloudFileEntry(
            name = prefix + file.filename,
            sha1 = file.shaFile.joinToString("") { "%02x".format(it) },
            timestampMillis = file.timestamp.time,
            sizeBytes = file.rawFileSize,
            deleted = file.persistState == ECloudStoragePersistState.k_ECloudStoragePersistStateDeleted,
            state = file.persistState.number,
        )
    }
    return CloudSnapshot(list.currentChangeNumber, files)
}
