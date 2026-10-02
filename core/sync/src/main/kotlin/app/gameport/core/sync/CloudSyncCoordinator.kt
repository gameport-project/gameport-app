package app.gameport.core.sync

import android.content.Context
import android.os.Build
import android.util.Log
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.AuthState
import app.gameport.core.model.SaveRule
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.cache.LibraryCacheStore
import app.gameport.core.steam.session.CloudUpload
import app.gameport.core.steam.session.SteamSession
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.downloadCloudFile
import app.gameport.core.steam.session.fetchCloud
import app.gameport.core.steam.session.uploadToCloud
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** How a game's launch-time sync starts. */
sealed interface BeginResult {
    /** Ready: the game should scan its save folders with [rules] and ask for a plan. */
    data class Ready(val rules: List<SaveRule>) : BeginResult

    /** No Steam session (offline, signed out): the game runs without syncing. */
    data object Offline : BeginResult

    /** The game is not one GamePort installed, or Steam has no Android save mapping for it. */
    data object Unsupported : BeginResult
}

/** A conflict waiting for the player, shown by the sync screen. */
data class PendingConflict(
    val packageName: String,
    val gameName: String,
    val local: SideSummary,
    val cloud: SideSummary,
    val choice: Side? = null,
    /** The player chose to decide later: the game continues and the question comes back next launch. */
    val deferred: Boolean = false,
)

/**
 * The GamePort side of the save sync. A patched game asks it, at launch and while playing, what to
 * copy in each direction; GamePort holds the Steam session and does everything Steam-related.
 * Nothing is ever overwritten silently: when both sides changed, the player chooses, and the
 * side that loses is backed up first.
 */
@Singleton
class CloudSyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SteamSessionHolder,
    private val auth: SteamAuthRepository,
    private val library: LibraryCacheStore,
    private val installed: InstalledGames,
    private val baselines: BaselineStore,
    private val syncStatus: SyncStatusStore,
    private val snapshots: SaveSnapshotStore,
    private val pending: PendingSyncStore,
) {
    private class Active(
        val packageName: String,
        val appId: Int,
        val gameName: String,
        val session: SteamSession,
        val paths: CloudPaths,
        var cloud: Map<String, CloudFile>,
        var changeNumber: Long,
    ) {
        var local: Map<String, LocalFile> = emptyMap()
        var plan: SyncAction = SyncAction.None
        val pushed = ConcurrentHashMap<String, File>()
        val accountId: Long get() = session.accountId
    }

    private val active = ConcurrentHashMap<String, Active>()
    private val _conflicts = MutableStateFlow<Map<String, PendingConflict>>(emptyMap())

    /** Conflicts waiting for a decision, by package name. */
    val conflicts: StateFlow<Map<String, PendingConflict>> = _conflicts.asStateFlow()

    /** Starts a sync for [packageName]: finds the session, the game's rules and the cloud files. */
    /**
     * A Steam session ticket for the game, made with the signed-in account, or null when GamePort is
     * offline or Steam gives none. The game's own servers verify it (login to their online services).
     */
    suspend fun authTicket(packageName: String): ByteArray? {
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return null
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return null
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() } ?: return null
        return runCatching { session.authSessionTicket(appId) }
            .onFailure { android.util.Log.w("GPSync", "no session ticket for $packageName", it) }
            .getOrNull()
    }

    /**
     * Keeps GamePort's connection to Steam up while a game runs: a session ticket is only useful if
     * it can be made in a moment when the game asks for it, and Android stops idle processes.
     */
    suspend fun warmUp() {
        runCatching { auth.restoreSession() }
    }

    /**
     * The game's rules plus the files it keeps through Steam's cloud API: the shim stores them in
     * its `remote` folder, and their cloud names are the plain names the game chose.
     */
    private fun rulesFor(packageName: String, appId: Int, published: List<SaveRule>): List<SaveRule> = published + SaveRule(
        localDir = "Android/data/$packageName/files/gameport/Goldberg SteamEmu Saves/$appId/remote",
        pattern = "*",
        recursive = true,
        cloudPrefix = "",
    )

    /** Looks at Steam Cloud for the saves page, without touching the game. False when offline. */
    suspend fun refreshCloudSnapshot(appId: Int): Boolean {
        val packageName = installed.all()[appId] ?: return false
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return false
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() } ?: return false
        val account = (auth.authState.value as? AuthState.SignedIn)?.account ?: return false
        val game = library.load(session.accountId)?.games?.firstOrNull { it.appId == appId } ?: return false
        val paths = CloudPaths(rulesFor(packageName, appId, game.saveRules.map { SaveRule(it.localDir, it.pattern, it.recursive, it.cloudPrefix) }), account.steamId, session.accountId)
        val snapshot = runCatching { session.fetchCloud(appId) }.getOrNull() ?: return false
        snapshots.saveCloud(
            appId,
            snapshot.files.filterNot { it.deleted || OwnFiles.isOwn(it.name) }.mapNotNull { file ->
                paths.toLocal(file.name)?.let { rel -> SnapFile(rel, file.sizeBytes.toLong(), file.timestampMillis, file.sha1) }
            },
        )
        return true
    }

    suspend fun begin(packageName: String): BeginResult {
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return BeginResult.Unsupported
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return BeginResult.Offline.also { syncStatus.record(appId, SyncStatus.OFFLINE) }
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() }
            ?: return BeginResult.Offline.also { syncStatus.record(appId, SyncStatus.OFFLINE) }
        val account = (auth.authState.value as? AuthState.SignedIn)?.account
            ?: return BeginResult.Offline.also { syncStatus.record(appId, SyncStatus.OFFLINE) }
        val game = library.load(session.accountId)?.games?.firstOrNull { it.appId == appId } ?: return BeginResult.Unsupported
        val rules = rulesFor(packageName, appId, game.saveRules.map { SaveRule(it.localDir, it.pattern, it.recursive, it.cloudPrefix) })

        val paths = CloudPaths(rules, account.steamId, session.accountId)
        val snapshot = try {
            session.fetchCloud(appId)
        } catch (e: Exception) {
            Log.w(TAG, "cloud listing failed", e)
            syncStatus.record(appId, SyncStatus.OFFLINE)
            return BeginResult.Offline
        }
        val (stray, real) = snapshot.files.filterNot { it.deleted }.partition { OwnFiles.isOwn(it.name) }
        val cloud = real.mapNotNull { file ->
            paths.toLocal(file.name)?.let { rel -> rel to CloudFile(file.name, rel, file.sha1, file.sizeBytes.toLong(), file.timestampMillis) }
        }.toMap()
        var changeNumber = snapshot.changeNumber
        if (stray.isNotEmpty()) {
            Log.i(TAG, "removing ${stray.size} stray GamePort file(s) from the cloud")
            session.uploadToCloud(appId, emptyList(), stray.map { it.name }, "GamePort (${Build.MODEL})", clientId())
                .also { Log.i(TAG, "stray file cleanup returned change number $it") }
                ?.let { changeNumber = it }
        }
        active[packageName] = Active(packageName, appId, game.name, session, paths, cloud, changeNumber)
        snapshots.saveCloud(appId, cloud.values.map { SnapFile(it.rel, it.size, it.timestamp, it.sha1) })
        syncStatus.record(appId, SyncStatus.OK)
        return BeginResult.Ready(paths.expandedRules())
    }

    /** Compares the game's [local] files with the cloud. [force] applies the player's choice. */
    fun plan(packageName: String, local: List<LocalFile>, force: Side? = null): SyncAction {
        val sync = active[packageName] ?: return SyncAction.None
        // A game patched by an early version still lists GamePort's own folders; they are not saves.
        sync.local = local.filterNot { OwnFiles.isOwn("/" + it.rel) }.associateBy { it.rel }
        snapshots.saveLocal(sync.appId, sync.local.values.map { SnapFile(it.rel, it.size, it.mtime, it.sha1) })
        val baseline = baselines.load(sync.accountId, sync.appId)
        // The player's choice on the saves page counts as one made at the conflict screen.
        val action = SyncPlanner.plan(sync.local, sync.cloud, sync.changeNumber, baseline, force ?: pending.take(sync.appId))
        sync.plan = action
        when (action) {
            is SyncAction.Conflict -> _conflicts.update {
                it + (packageName to PendingConflict(packageName, sync.gameName, action.local, action.cloud, it[packageName]?.choice))
            }
            SyncAction.None -> recordAgreement(sync)
            else -> Unit
        }
        return action
    }

    /** The answer to a pending conflict: PENDING while open, then LOCAL, CLOUD or LATER. */
    fun answerFor(packageName: String): String {
        val conflict = _conflicts.value[packageName] ?: return "PENDING"
        return when {
            conflict.deferred -> "LATER"
            conflict.choice != null -> conflict.choice.name
            else -> "PENDING"
        }
    }

    fun defer(packageName: String) {
        _conflicts.update { current -> current[packageName]?.let { current + (packageName to it.copy(deferred = true)) } ?: current }
    }

    fun resolve(packageName: String, side: Side) {
        _conflicts.update { current -> current[packageName]?.let { current + (packageName to it.copy(choice = side)) } ?: current }
    }

    /** Called when the game is done with a conflict (or gave up waiting for it). */
    fun clearConflict(packageName: String) {
        _conflicts.update { it - packageName }
    }

    /** Downloads one cloud file for the game to copy into place. Null if it could not be fetched intact. */
    suspend fun fetch(packageName: String, cloudName: String): File? {
        val sync = active[packageName] ?: return null
        val expected = sync.cloud.values.firstOrNull { it.name == cloudName } ?: return null
        val target = File(context.cacheDir, "cloud-fetch/${sync.appId}/${sha1Hex(cloudName.toByteArray())}")
        if (!sync.session.downloadCloudFile(sync.appId, cloudName, target)) return null
        if (target.sha1() != expected.sha1) {
            target.delete()
            return null
        }
        return target
    }

    /** The game applied a download; [local] is what its folders hold now. */
    fun acknowledgeDownload(packageName: String, local: List<LocalFile>) {
        val sync = active[packageName] ?: return
        sync.local = local.filterNot { OwnFiles.isOwn("/" + it.rel) }.associateBy { it.rel }
        baselines.save(sync.accountId, sync.appId, Baseline(sync.changeNumber, sync.local.mapValues { it.value.sha1 }))
        snapshots.saveLocal(sync.appId, sync.local.values.map { SnapFile(it.rel, it.size, it.mtime, it.sha1) })
        sync.plan = SyncAction.None
        clearConflict(packageName)
    }

    /** A file the game will write into, to be sent to Steam on [commitUpload]. */
    fun pushTarget(packageName: String, rel: String): File? {
        val sync = active[packageName] ?: return null
        val upload = sync.plan as? SyncAction.Upload ?: return null
        if (upload.files.none { it.rel == rel }) return null
        val target = File(context.cacheDir, "cloud-push/${sync.appId}/${sha1Hex(rel.toByteArray())}")
        target.parentFile?.mkdirs()
        sync.pushed[rel] = target
        return target
    }

    /** Sends everything the plan called for. Whatever the cloud holds that this replaces is backed up first. */
    suspend fun commitUpload(packageName: String): Boolean {
        val sync = active[packageName] ?: return false
        val plan = sync.plan as? SyncAction.Upload ?: return false
        val planned = plan.files
        if (planned.any { file -> sync.pushed[file.rel]?.takeIf { it.isFile && it.sha1() == file.sha1 } == null }) return false

        backupCloudFiles(sync, planned.mapNotNull { sync.cloud[it.rel] } + plan.deleteCloud)

        val uploads = planned.map { file ->
            CloudUpload(
                cloudName = sync.cloud[file.rel]?.name ?: sync.paths.toCloud(file.rel) ?: return false,
                file = sync.pushed.getValue(file.rel),
                sha1 = file.sha1.hexToBytes(),
                timestampMillis = file.mtime,
            )
        }
        val newChange = sync.session.uploadToCloud(
            appId = sync.appId,
            uploads = uploads,
            deletes = plan.deleteCloud.map { it.name },
            machineName = "GamePort (${Build.MODEL})",
            clientId = clientId(),
        ) ?: return false.also { syncStatus.record(sync.appId, SyncStatus.FAILED) }

        syncStatus.record(sync.appId, SyncStatus.OK)
        // The cloud now holds what this device does.
        snapshots.saveCloud(sync.appId, sync.local.values.map { SnapFile(it.rel, it.size, it.mtime, it.sha1) })
        sync.changeNumber = newChange
        baselines.save(sync.accountId, sync.appId, Baseline(newChange, sync.local.mapValues { it.value.sha1 }))
        sync.pushed.values.forEach { it.delete() }
        sync.pushed.clear()
        sync.plan = SyncAction.None
        clearConflict(packageName)
        return true
    }

    /**
     * Forgets what was synced (used when signing out). Safety copies of overwritten saves are kept:
     * they are the player's data, not GamePort's bookkeeping.
     */
    fun forgetSyncState() {
        active.clear()
        _conflicts.value = emptyMap()
        baselines.clear()
        syncStatus.clear()
        snapshots.clear()
        pending.clearAll()
        File(context.cacheDir, "cloud-fetch").deleteRecursively()
        File(context.cacheDir, "cloud-push").deleteRecursively()
    }

    /** The game is done (or gone) for this launch. */
    fun end(packageName: String) {
        active.remove(packageName)?.pushed?.values?.forEach { it.delete() }
        clearConflict(packageName)
    }

    /** Both sides were already identical or unchanged: remember it, so a later change is seen as one. */
    private fun recordAgreement(sync: Active) {
        if (sync.local.isEmpty() && sync.cloud.isEmpty()) return
        val identical = sync.local.keys == sync.cloud.keys && sync.local.all { (rel, file) -> sync.cloud.getValue(rel).sha1 == file.sha1 }
        if (identical) baselines.save(sync.accountId, sync.appId, Baseline(sync.changeNumber, sync.local.mapValues { it.value.sha1 }))
    }

    private suspend fun backupCloudFiles(sync: Active, files: List<CloudFile>) {
        if (files.isEmpty()) return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val root = File(context.filesDir, "cloud-backups/${sync.appId}/$stamp")
        files.distinctBy { it.name }.forEach { file ->
            if (!sync.session.downloadCloudFile(sync.appId, file.name, File(root, file.rel.replaceFirst(Regex("^Android/data/[^/]+/files/"), "")))) {
                Log.w(TAG, "could not back up ${file.name}")
            }
        }
    }

    private fun clientId(): Long {
        val prefs = context.getSharedPreferences("gameport_cloud", Context.MODE_PRIVATE)
        return prefs.getLong("client_id", 0L).takeIf { it != 0L }
            ?: (java.security.SecureRandom().nextLong() and Long.MAX_VALUE).also { prefs.edit().putLong("client_id", it).apply() }
    }

    private companion object {
        const val TAG = "GPSync"
        const val SESSION_WAIT_MS = 25_000L
    }
}

private fun File.sha1(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-1")
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

private fun sha1Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

private fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
