package app.gameport.core.sync

import app.gameport.core.model.SaveDirection
import app.gameport.core.model.SaveFileInfo
import app.gameport.core.model.SaveFileState
import app.gameport.core.model.SaveOverview
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What the saves page of a game shows and does: the two sides side by side, and the player's choices. */
@Singleton
class SaveStateRepository @Inject constructor(
    private val snapshots: SaveSnapshotStore,
    private val pending: PendingSyncStore,
    private val coordinator: CloudSyncCoordinator,
) {
    fun observe(appId: Int): Flow<SaveOverview> = combine(snapshots.observe(appId), pending.observe(appId)) { snapshot, choice ->
        val local = snapshot.local.associateBy { it.rel }
        val cloud = snapshot.cloud.associateBy { it.rel }
        SaveOverview(
            files = (local.keys + cloud.keys).sorted().map { path ->
                SaveFileState(path, local[path]?.info(), cloud[path]?.info())
            },
            localSeenMillis = snapshot.localAt,
            cloudSeenMillis = snapshot.cloudAt,
            pending = when (choice) {
                Side.CLOUD -> SaveDirection.RESTORE_FROM_CLOUD
                Side.LOCAL -> SaveDirection.SEND_TO_CLOUD
                null -> null
            },
        )
    }

    /** Looks at Steam Cloud again; false when GamePort is offline. */
    suspend fun refreshCloud(appId: Int): Boolean = coordinator.refreshCloudSnapshot(appId)

    /** Use the cloud's saves the next time the game starts (this device's are backed up first). */
    fun requestRestore(appId: Int) = pending.set(appId, Side.CLOUD)

    /** Send this device's saves to the cloud the next time the game starts (the cloud's are backed up first). */
    fun requestSend(appId: Int) = pending.set(appId, Side.LOCAL)

    fun cancel(appId: Int) = pending.clear(appId)

    private fun SnapFile.info() = SaveFileInfo(size, time, sha1)
}
