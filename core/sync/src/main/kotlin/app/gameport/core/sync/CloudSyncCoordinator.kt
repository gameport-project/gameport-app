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
    data class Ready(val rules: List<SaveRule>, val more: Boolean = false) : BeginResult

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
    private val gate: app.gameport.core.install.UpdateGate,
    private val decisions: PlayDecisions,
    private val events: app.gameport.core.install.GameEventLog,
    private val spaces: SaveSpaceStore,
    private val pcBackups: PcSaveBackups,
) {
    private class Active(
        val packageName: String,
        val appId: Int,
        val gameName: String,
        val session: SteamSession,
        val paths: CloudPaths,
        var cloud: Map<String, CloudFile>,
        var changeNumber: Long,
        /** Which place of Steam Cloud this pass looks at (see [SavePass]). */
        val pass: SavePass = SavePass.ANDROID,
    ) {
        /** The Windows saves have a record of their own: what was agreed with them says nothing about the Android saves, and the other way round. */
        val baselineKey: Int get() = if (pass.isPc) -appId else appId
        var local: Map<String, LocalFile> = emptyMap()
        var plan: SyncAction = SyncAction.None
        val pushed = ConcurrentHashMap<String, File>()
        val accountId: Long get() = session.accountId
    }

    private val active = ConcurrentHashMap<String, Active>()

    // Games whose process GamePort started itself, only to send their saves (see [SaveCatchUp]): the hook asks before it starts.
    private val catchUps = ConcurrentHashMap<String, Long>()

    /** GamePort is about to start [packageName]'s process to send its saves, not to play it. */
    fun requestCatchUp(packageName: String) {
        catchUps[packageName] = System.currentTimeMillis()
    }

    /** True while [packageName]'s process was started by GamePort for [requestCatchUp]; a game the player launches is never one. */
    fun isCatchUp(packageName: String): Boolean = catchUps[packageName]?.let { System.currentTimeMillis() - it < CATCH_UP_WINDOW_MS } == true

    fun finishCatchUp(packageName: String) {
        catchUps.remove(packageName)
    }
    private val _conflicts = MutableStateFlow<Map<String, PendingConflict>>(emptyMap())

    /** Conflicts waiting for a decision, by package name. */
    val conflicts: StateFlow<Map<String, PendingConflict>> = _conflicts.asStateFlow()

    /** Starts a sync for [packageName]: finds the session, the game's rules and the cloud files. */
    /** What a request for a ticket came to. */
    sealed interface TicketOutcome {
        class Given(val ticket: ByteArray) : TicketOutcome

        /** No ticket: GamePort is offline, signed out, or Steam gave none. The game starts without it. */
        data object None : TicketOutcome

        /** Steam says another device plays with the account: what to do is the player's (see [PlayDecisions]). */
        class Blocked(val otherApp: Int) : TicketOutcome
    }

    /**
     * A Steam session ticket for the game, made with the signed-in account. The game's own servers verify it (login to their online services).
     * With [takeOver] the playing session is first taken from the other device, as Steam's client does when the player chooses to play here. With an [identity] the ticket is the one for a web service of that name.
     */
    suspend fun authTicket(packageName: String, takeOver: Boolean = false, identity: String? = null): TicketOutcome {
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return TicketOutcome.None
        // Taking the playing session is the player's wish: the connection is opened even when the account was just taken by another device.
        runCatching { if (takeOver) auth.resume() else auth.restoreSession() }
        if (auth.offline.value) return TicketOutcome.None
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() } ?: return TicketOutcome.None
        if (takeOver) {
            val taken = runCatching { session.takePlaying(appId) }.getOrDefault(false)
            events.note(appId, "the playing session was taken from the other device: ${if (taken) "done" else "Steam still says another one plays"}")
        }
        return try {
            TicketOutcome.Given(session.authSessionTicket(appId, identity)).also { events.note(appId, "Steam ticket given to the game") }
        } catch (blocked: app.gameport.core.steam.session.PlayingBlockedException) {
            events.note(appId, "no Steam ticket: Steam says another device plays (app ${blocked.other.appId})")
            TicketOutcome.Blocked(blocked.other.appId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.w("GPSync", "no session ticket for $packageName: the connection fell", e)
            // Steam drops the connection when it refuses the playing session: said at once, as a headset stops GamePort a few seconds after a game starts.
            val refused = session.playingElsewhere.value?.takeIf { !it.kicked }
            if (refused != null) {
                events.note(appId, "no Steam ticket: Steam dropped the connection as the game was declared, another device plays with the account")
                TicketOutcome.Blocked(refused.appId)
            } else {
                retryOnNextConnection(appId, session)
            }
        } catch (e: Exception) {
            android.util.Log.w("GPSync", "no session ticket for $packageName", e)
            events.note(appId, "no Steam ticket (${e::class.simpleName})")
            TicketOutcome.None
        }
    }

    /** The connection fell as the ticket was asked for: on the next one, Steam is asked whether the account may play, and says if it may not. */
    private suspend fun retryOnNextConnection(appId: Int, lost: SteamSession): TicketOutcome {
        val next = withTimeoutOrNull(RECONNECT_WAIT_MS) { sessions.current.filterNotNull().first { it !== lost && it.isAlive } }
        val other = next?.probePlaying(appId, PROBE_WAIT_MS)
        if (other != null) {
            events.note(appId, "no Steam ticket: Steam says another device plays (app ${other.appId})")
            return TicketOutcome.Blocked(other.appId)
        }
        events.note(appId, "no Steam ticket: the connection to Steam fell as it was asked for, and Steam says nothing against playing")
        return TicketOutcome.None
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
        val published = game.saveRules.map { SaveRule(it.localDir, it.pattern, it.recursive, it.cloudPrefix, it.pcCloudPrefix) }
        val paths = CloudPaths(rulesFor(packageName, appId, published), account.steamId, session.accountId)
        val snapshot = runCatching { session.fetchCloud(appId) }.getOrNull() ?: return false
        snapshots.saveCloud(
            appId,
            snapshot.files.filterNot { it.deleted || OwnFiles.isOwn(it.name) }.mapNotNull { file ->
                paths.toLocal(file.name)?.let { rel -> SnapFile(rel, file.sizeBytes.toLong(), file.timestampMillis, file.sha1) }
            },
        )
        // The Windows copy of the saves, when the game keeps one apart: shown on the saves page whatever the space in use.
        val pcRules = published.filter { it.pcCloudPrefix != null }.map(::onPcSpace)
        snapshots.savePc(
            appId,
            if (pcRules.isEmpty()) null else {
                val pcPaths = CloudPaths(pcRules, account.steamId, session.accountId)
                snapshot.files.filterNot { it.deleted || OwnFiles.isOwn(it.name) }.mapNotNull { file ->
                    pcPaths.toLocal(file.name)?.let { rel -> SnapFile(rel, file.sizeBytes.toLong(), file.timestampMillis, file.sha1) }
                }
            },
        )
        return true
    }

    /**
     * [launch] is the sync before the game reads its saves; [pass] counts the looks at Steam Cloud of this one, [knowsPasses] says the hook asks for more
     * when [BeginResult.Ready.more] says so (a hook from before only looks once, at the Android saves).
     */
    suspend fun begin(packageName: String, launch: Boolean = true, pass: Int = 0, knowsPasses: Boolean = false): BeginResult {
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return BeginResult.Unsupported
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return BeginResult.Offline.also { syncStatus.record(appId, SyncStatus.OFFLINE) }
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() }
            ?: return BeginResult.Offline.also { syncStatus.record(appId, SyncStatus.OFFLINE) }
        val account = (auth.authState.value as? AuthState.SignedIn)?.account
            ?: return BeginResult.Offline.also { syncStatus.record(appId, SyncStatus.OFFLINE) }
        val game = library.load(session.accountId)?.games?.firstOrNull { it.appId == appId } ?: return BeginResult.Unsupported
        val published = game.saveRules.map { SaveRule(it.localDir, it.pattern, it.recursive, it.cloudPrefix, it.pcCloudPrefix) }
        // The places of Steam Cloud to look at, in order: the Android saves, and the Windows ones before them when the player chose to use them.
        noticeSavesChange(appId, published)
        val passes = SavePasses.of(spaces.get(appId), published.any { it.pcCloudPrefix != null }, launch, knowsPasses)
        val thisPass = passes.getOrNull(pass) ?: return BeginResult.Unsupported
        if (thisPass.isPc && pass == 0) events.note(appId, if (thisPass == SavePass.PC_DOWNLOAD) "saves: the Windows saves are brought in" else "saves: synced with the Windows saves")
        val rules = rulesFor(packageName, appId, if (thisPass.isPc) published.map(::onPcSpace) else published)

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
        active[packageName] = Active(packageName, appId, game.name, session, paths, cloud, changeNumber, thisPass)
        gate.setSyncing(true)
        showCloud(appId, thisPass, cloud.values.map { SnapFile(it.rel, it.size, it.timestamp, it.sha1) })
        syncStatus.record(appId, SyncStatus.OK)
        return BeginResult.Ready(paths.expandedRules(), more = pass < passes.size - 1)
    }

    /**
     * What the game says its saves are right now, outside any sync: the page of its saves shows it, and a difference with the cloud is
     * remembered as something to send (see [SaveCatchUp]), so a save is never left behind without anyone knowing.
     */
    fun observeLocal(packageName: String, local: List<LocalFile>) {
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return
        val files = local.filterNot { OwnFiles.isOwn("/" + it.rel) }
        snapshots.saveLocal(appId, files.map { SnapFile(it.rel, it.size, it.mtime, it.sha1) })
        val cloud = snapshots.load(appId).cloud
        statusAfterObserving(syncStatus.statuses.value[appId], files.associate { it.rel to it.sha1 }, cloud.associate { it.rel to it.sha1 })
            ?.let { syncStatus.record(appId, it) }
    }

    /** Compares the game's [local] files with the cloud. [force] applies the player's choice. */
    fun plan(packageName: String, local: List<LocalFile>, force: Side? = null): SyncAction {
        val sync = active[packageName] ?: return SyncAction.None
        // A game patched by an early version still lists GamePort's own folders; they are not saves.
        sync.local = local.filterNot { OwnFiles.isOwn("/" + it.rel) }.associateBy { it.rel }
        snapshots.saveLocal(sync.appId, sync.local.values.map { SnapFile(it.rel, it.size, it.mtime, it.sha1) })
        val baseline = baselines.load(sync.accountId, sync.baselineKey)
        // The player's choice on the saves page counts as one made at the conflict screen.
        // The player's choice on the saves page is for the Android saves, which it shows.
        val choice = force ?: if (sync.pass == SavePass.ANDROID) pending.take(sync.appId) else null
        val planned = SyncPlanner.plan(sync.local, sync.cloud, sync.changeNumber, baseline, choice)
        // The Windows saves are only read: sending what this device holds is not done, and is remembered as seen so it is not asked again.
        val action = if (sync.pass == SavePass.PC_DOWNLOAD) readOnly(sync, planned) else planned
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
        baselines.save(sync.accountId, sync.baselineKey, Baseline(sync.changeNumber, sync.local.mapValues { it.value.sha1 }))
        snapshots.saveLocal(sync.appId, sync.local.values.map { SnapFile(it.rel, it.size, it.mtime, it.sha1) })
        sync.plan = SyncAction.None
        clearConflict(packageName)
    }

    /**
     * A pass that only reads the Windows saves: what this device holds that they do not is not sent. The Windows saves as they are now are
     * remembered as seen, so the next start only asks again if they moved.
     */
    private fun readOnly(sync: Active, planned: SyncAction): SyncAction = when (planned) {
        is SyncAction.Upload -> {
            baselines.save(sync.accountId, sync.baselineKey, Baseline(sync.changeNumber, sync.cloud.mapValues { it.value.sha1 }))
            SyncAction.None
        }
        else -> planned
    }

    /** Where the page of the saves shows the cloud: the Android saves, or the Windows copy while a pass reads that. */
    private fun showCloud(appId: Int, pass: SavePass, files: List<SnapFile>) {
        if (pass.isPc) snapshots.savePc(appId, files) else snapshots.saveCloud(appId, files)
    }

    /**
     * Steam can change how it describes a game's saves (an update of the game, a root override added by the studio). When it does, what GamePort
     * agreed on with the old places says nothing about the new ones, and would read as files that were deleted: the records are dropped, so the next
     * sync compares afresh and asks if the two sides differ. A choice to use the PC saves goes when the studio now links them itself.
     */
    private fun noticeSavesChange(appId: Int, published: List<SaveRule>) {
        val signature = SaveRulesSignature.of(published)
        val before = spaces.rulesSignature(appId)
        if (before == signature) return
        spaces.setRulesSignature(appId, signature)
        // The first look: nothing to compare with.
        if (before == null) return
        baselines.forgetAndroid(appId)
        baselines.forgetPc(appId)
        val mode = spaces.get(appId)
        val kept = SaveRulesSignature.modeAfterChange(mode, stillApart = published.any { it.pcCloudPrefix != null })
        if (kept != mode) spaces.set(appId, kept)
        events.note(appId, "saves: Steam changed how this game's saves are described" + if (kept != mode) ", the PC and Android saves are now linked by the studio: GamePort follows it" else ": GamePort follows it")
    }

    // ---- The PC saves ----

    /**
     * The rules whose files are "this device's saves" of the game, as the hook looks at them. Known from what Steam published and GamePort kept, so
     * it needs no connection. Empty when the game is not installed or its rules are not known.
     */
    fun localRulesOf(appId: Int): List<SaveRule> {
        val packageName = installed.all()[appId] ?: return emptyList()
        val account = (auth.authState.value as? AuthState.SignedIn)?.account ?: return emptyList()
        val game = library.load(account.steamId and 0xFFFFFFFFL)?.games?.firstOrNull { it.appId == appId } ?: return emptyList()
        return rulesFor(packageName, appId, game.saveRules.map { SaveRule(it.localDir, it.pattern, it.recursive, it.cloudPrefix, it.pcCloudPrefix) })
    }

    /**
     * The saves on this device were put aside by the hook, and nothing was removed from Steam. What the page shows of this device is emptied, and a
     * choice waiting on the page to send this device's saves is dropped: with nothing on the device it could only mean "remove everything from Steam".
     */
    fun afterLocalCleared(appId: Int, moved: Int) {
        pending.clear(appId)
        snapshots.saveLocal(appId, emptyList())
        events.note(appId, "saves: $moved file(s) on this device put aside in the backup folder, nothing removed from Steam")
    }

    /** Each start, the Windows saves feed this device; this device feeds the Android saves as usual. Nothing is written to the Windows saves. */
    fun setUsePc(appId: Int, on: Boolean) {
        pending.clear(appId)
        baselines.forgetPc(appId)
        spaces.set(appId, if (on) SaveSpaceMode.USE_PC else SaveSpaceMode.ANDROID)
        events.note(appId, if (on) "saves: the Windows saves now feed this device" else "saves: back to the Android saves only")
    }

    /**
     * Makes this device write to the Windows saves too, both ways. A clean copy of what they hold is made first and kept; if it cannot be made,
     * nothing changes. False when it could not be made.
     */
    suspend fun linkPcSpace(appId: Int): Boolean {
        if (!backupPcSpace(appId)) return false
        spaces.set(appId, SaveSpaceMode.USE_PC_AND_SEND)
        events.note(appId, "saves: this device now also writes to the Windows saves, after a clean copy of them")
        return true
    }

    /** This device stops writing to the Windows saves; they still feed it. */
    fun unlinkPcSpace(appId: Int) {
        spaces.set(appId, SaveSpaceMode.USE_PC)
        events.note(appId, "saves: this device no longer writes to the Windows saves")
    }

    /** What is needed to look at the Windows copy of a game's saves: the session, and the paths that name its files. Null offline or when there is none. */
    private suspend fun pcSpaceOf(appId: Int): Triple<SteamSession, CloudPaths, String>? {
        val packageName = installed.all()[appId] ?: return null
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return null
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() } ?: return null
        val account = (auth.authState.value as? AuthState.SignedIn)?.account ?: return null
        val game = library.load(session.accountId)?.games?.firstOrNull { it.appId == appId } ?: return null
        val rules = game.saveRules.map { SaveRule(it.localDir, it.pattern, it.recursive, it.cloudPrefix, it.pcCloudPrefix) }
            .filter { it.pcCloudPrefix != null }.map(::onPcSpace)
        if (rules.isEmpty()) return null
        return Triple(session, CloudPaths(rules, account.steamId, session.accountId), packageName)
    }

    /** Copies the Windows saves of Steam Cloud, as they are now, aside. All of them or none: false (and nothing kept) if one cannot be fetched intact. */
    suspend fun backupPcSpace(appId: Int): Boolean {
        val (session, paths, _) = pcSpaceOf(appId) ?: return false
        val snapshot = runCatching { session.fetchCloud(appId) }.getOrNull() ?: return false
        val files = snapshot.files.filterNot { it.deleted || OwnFiles.isOwn(it.name) }.filter { paths.toLocal(it.name) != null }
        val (stamp, dir) = pcBackups.begin(appId)
        val kept = files.mapIndexed { index, file ->
            val stored = "f$index"
            val target = File(dir, stored)
            if (!session.downloadCloudFile(appId, file.name, target) || target.sha1() != file.sha1) {
                pcBackups.discard(dir)
                Log.w(TAG, "could not copy a Windows save aside")
                return false
            }
            PcBackupFile(file.name, stored, file.sha1, file.sizeBytes.toLong(), file.timestampMillis)
        }
        pcBackups.commit(appId, PcBackup(stamp, System.currentTimeMillis(), kept))
        events.note(appId, "saves: clean copy of the Windows saves made (${kept.size} file(s))")
        return true
    }

    /**
     * Puts the Windows saves back as the last clean copy held them: the files are sent again, dated now so that Steam and the PC take them over
     * whatever was written since, and what the copy does not hold is removed from the Windows space. This device goes back to the Android saves.
     */
    suspend fun restorePcSpace(appId: Int): Boolean {
        val backup = pcBackups.latest(appId) ?: return false
        val (session, paths, _) = pcSpaceOf(appId) ?: return false
        val dir = pcBackups.directoryOf(appId, backup)
        if (backup.files.any { file -> !File(dir, file.stored).isFile || File(dir, file.stored).sha1() != file.sha1 }) return false
        val snapshot = runCatching { session.fetchCloud(appId) }.getOrNull() ?: return false
        val now = System.currentTimeMillis()
        val wanted = backup.files.map { it.cloudName }.toSet()
        val extra = snapshot.files.filterNot { it.deleted || OwnFiles.isOwn(it.name) }
            .filter { paths.toLocal(it.name) != null && it.name !in wanted }.map { it.name }
        val uploads = backup.files.map { file -> CloudUpload(file.cloudName, File(dir, file.stored), file.sha1.hexToBytes(), now) }
        session.uploadToCloud(appId, uploads, extra, "GamePort (${Build.MODEL})", clientId()) ?: return false
        // The Windows saves changed under the record of the last sync with them: it is dropped, and this device stops writing to them.
        baselines.forgetPc(appId)
        unlinkPcSpace(appId)
        events.note(appId, "saves: the Windows saves were put back from the clean copy")
        runCatching { refreshCloudSnapshot(appId) }
        return true
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
        showCloud(sync.appId, sync.pass, sync.local.values.map { SnapFile(it.rel, it.size, it.mtime, it.sha1) })
        sync.changeNumber = newChange
        baselines.save(sync.accountId, sync.baselineKey, Baseline(newChange, sync.local.mapValues { it.value.sha1 }))
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
        gate.setSyncing(active.isNotEmpty())
        clearConflict(packageName)
    }

    /** Both sides were already identical or unchanged: remember it, so a later change is seen as one. */
    private fun recordAgreement(sync: Active) {
        if (sync.local.isEmpty() && sync.cloud.isEmpty()) return
        val identical = sync.local.keys == sync.cloud.keys && sync.local.all { (rel, file) -> sync.cloud.getValue(rel).sha1 == file.sha1 }
        if (identical) baselines.save(sync.accountId, sync.baselineKey, Baseline(sync.changeNumber, sync.local.mapValues { it.value.sha1 }))
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
        const val RECONNECT_WAIT_MS = 10_000L
        const val PROBE_WAIT_MS = 5_000L

        /** A request to send a game's saves stays valid this long; the hook says when it is done. */
        const val CATCH_UP_WINDOW_MS = 60_000L
    }
}

/** The same rule, naming the Windows copy of the saves where the studio keeps one apart from the Android one. */
private fun onPcSpace(rule: SaveRule): SaveRule = rule.pcCloudPrefix?.let { rule.copy(cloudPrefix = it) } ?: rule

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

/**
 * The status to record once the saves on this device are known, or null to leave it as it is. Saves that differ from the cloud's are to be
 * sent; a status that already says a sync did not go through (offline, failed) stays, and one that said "to send" ends when they agree.
 */
internal fun statusAfterObserving(current: SyncStatus?, local: Map<String, String>, cloud: Map<String, String>): SyncStatus? = when {
    local == cloud -> if (current == SyncStatus.PENDING) SyncStatus.OK else null
    current == null || current == SyncStatus.OK -> SyncStatus.PENDING
    else -> null
}

