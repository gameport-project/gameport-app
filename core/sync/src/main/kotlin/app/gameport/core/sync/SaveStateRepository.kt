package app.gameport.core.sync

import app.gameport.core.model.SaveDirection
import app.gameport.core.model.SaveFileInfo
import app.gameport.core.model.SaveFileState
import app.gameport.core.model.PcSaves
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
    private val spaces: SaveSpaceStore,
    private val pcBackups: PcSaveBackups,
    private val cleaner: LocalSavesCleaner,
) {
    fun observe(appId: Int): Flow<SaveOverview> = combine(
        snapshots.observe(appId),
        pending.observe(appId),
        spaces.observe(appId),
        pcBackups.observeLatest(appId),
    ) { snapshot, choice, space, backup ->
        val local = snapshot.local.associateBy { it.rel }
        val cloud = snapshot.cloud.associateBy { it.rel }
        val pc = snapshot.pc?.associateBy { it.rel }
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
            pc = pc?.let {
                PcSaves(
                    files = (local.keys + it.keys).sorted().map { path -> SaveFileState(path, local[path]?.info(), it[path]?.info()) },
                    usePc = space.usesPc,
                    sendPc = space == SaveSpaceMode.USE_PC_AND_SEND,
                    backupMillis = backup?.createdAt ?: 0,
                    backupFiles = backup?.files?.size ?: 0,
                )
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

    /** Puts the saves on this device aside, now, without launching the game; nothing is removed from Steam. */
    suspend fun clearLocal(appId: Int): LocalSavesCleaner.Outcome = cleaner.clear(appId)

    /** Each start, the Windows saves feed this device; this device feeds the Android saves as usual. Nothing is written to the Windows saves. */
    fun setUsePc(appId: Int, on: Boolean) = coordinator.setUsePc(appId, on)

    /** This device also writes to the Windows saves, after a clean copy of them. False when the copy could not be made (nothing changed). */
    suspend fun linkPc(appId: Int): Boolean = coordinator.linkPcSpace(appId)

    fun unlinkPc(appId: Int) = coordinator.unlinkPcSpace(appId)

    /** Put the Windows saves back as the last clean copy held them. False when that could not be done. */
    suspend fun restorePc(appId: Int): Boolean = coordinator.restorePcSpace(appId)

    private fun SnapFile.info() = SaveFileInfo(size, time, sha1)
}
