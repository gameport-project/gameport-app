package app.gameport.core.sync

import android.content.Intent
import app.gameport.core.device.DeviceProfile
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.GameUpdatesRepository
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.GameIssue
import app.gameport.core.model.needsAttention
import app.gameport.core.settings.ControllerMappingStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * What needs the player's attention for each installed game: an outdated patch, a missing storage
 * permission, a save conflict, a save sync that failed. The game's page lists them, and the cover
 * carries a badge while there is any.
 */
@Singleton
class GameIssuesRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val installer: GameInstallRepository,
    private val coordinator: CloudSyncCoordinator,
    private val syncStatus: SyncStatusStore,
    private val installed: InstalledGames,
    private val updates: GameUpdatesRepository,
    private val controllers: ControllerMappingStore,
    private val device: DeviceProfile,
    private val reports: ReportStore,
    private val reinstallNotice: app.gameport.core.settings.ReinstallNoticeStore,
    private val events: app.gameport.core.install.GameEventLog,
) {
    private val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Development only: a debug build treats the games whose ids are in its files folder, in `debug-expansion-missing.txt` (one id per line), as lacking one of their large
     * files, so the card that says so can be looked at on the emulator. A release build never reads it.
     */
    private fun simulatedMissing(): Set<Int> = if (!debuggable) emptySet() else
        runCatching { java.io.File(context.filesDir, "debug-expansion-missing.txt").readLines().mapNotNull { it.trim().toIntOrNull() }.toSet() }.getOrDefault(emptySet())

    // Permissions are granted outside GamePort, so the screens ask for a re-check when they come back.
    private val recheck = MutableStateFlow(0)

    fun refresh() = recheck.update { it + 1 }

    fun observe(appId: Int): Flow<List<GameIssue>> = combine(
        installer.observePatchOutdated(appId),
        coordinator.conflicts,
        syncStatus.statuses,
        combine(recheck, updates.updates, controllers.observe(appId)) { _, list, mapping ->
            Triple(
                list.any { it.appId == appId },
                mapping.takeIf { device.isHeadset && it.detected.isNotEmpty() && !it.noticeSeen && !it.both }?.source,
                if (device.isHeadset && mapping.both && mapping.silent) mapping.useFrame else null,
            )
        },
        combine(reports.suspected, installer.expansionMayBeMissing, installer.expansionMissing, reinstallNotice.answered) { suspected, unchecked, missing, answered ->
            Others(suspected, unchecked, missing, answered)
        },
    ) { patchOutdated, conflicts, statuses, (updateAvailable, mappingNoticeSource, silent), others ->
        // The controller notice is informative: it is listed on the game's page but never lights the cover badge.
        issuesFor(appId, patchOutdated, conflicts.keys, statuses, updateAvailable, others.suspected, others.answered) +
            listOfNotNull(
                mappingNoticeSource?.let { GameIssue.ControllerMappingAvailable(it) },
                silent?.let { GameIssue.ControllerSilent(activated = it) },
                GameIssue.ExpansionMayBeMissing.takeIf { appId in others.unchecked },
                others.missing[appId]?.let { GameIssue.ExpansionMissing(it) } ?: GameIssue.ExpansionMissing(1).takeIf { appId in simulatedMissing() },
            )
    }

    /** Ids of the installed games with at least one issue that needs attention. A newer version is good news and is not one. */
    fun observeAttention(): Flow<Set<Int>> = combine(
        installer.observeOutdatedPatches(),
        coordinator.conflicts,
        syncStatus.statuses,
        combine(recheck, updates.updates) { _, list -> list.map { it.appId }.toSet() },
        reports.suspected,
    ) { outdated, conflicts, statuses, updatable, suspected ->
        installed.all().keys.filter { appId ->
            issuesFor(appId, appId in outdated, conflicts.keys, statuses, updateAvailable = false, suspected = suspected).any { it.needsAttention }
        }.toSet()
    }

    /** Ids of the installed games that have a question for the player (the blue mark): the game may need installing again, and it was not said that it works. */
    fun observeQuestions(): Flow<Set<Int>> = combine(reinstallNotice.answered, installer.observeAll()) { answered, _ ->
        installed.all().filter { (appId, packageName) -> appId !in answered && installer.reinstallMayHelp(packageName) }.keys
    }

    /** The player says the game works: its question and its blue mark go. */
    fun answerReinstallWorks(appId: Int) = reinstallNotice.answerWorks(appId)

    /**
     * True when the game was started from GamePort and its hook never answered: it was refused or closed before it began. On a device that refuses a game it knows the
     * package name of but not the signature of the copy installed here, that is what a game installed under its own name comes to, and installing it again under a name
     * of GamePort's own is the way out. Nothing is said when the game already carries that name: there would be nothing to install again under.
     *
     * What is judged is the copy installed now. A run that came before a change of the game — patched again, updated, or installed under another name — says nothing
     * about it: the verdict goes, the page offers Play again, and the player tries the game as it is. Only a run made under the name the game carries now, and since it
     * was last changed, is counted.
     */
    private fun refusedByDevice(appId: Int, packageName: String): Boolean {
        if (!app.gameport.core.model.vr.VrPlatforms.refusesReSignedGames(device.vrPlatform?.id)) return false
        val wanted = installer.renamedPackageFor(appId) ?: return false
        if (wanted == packageName) return false
        val launched = events.lastTimeOf(appId, app.gameport.core.install.LAUNCH_NOTE) ?: return false
        // The game changed since that run: it was patched again, updated, or installed under another name. The run was another copy, and the player is left to try this one.
        val installedAt = installer.installedAt(appId) ?: return false
        if (launched < installedAt) return false
        // Long enough for a game that does start to have answered through its hook: before that, nothing is said of a run still beginning.
        if (System.currentTimeMillis() - launched < ANSWER_WAIT_MS) return false
        val answered = reports.lastDataMillis(packageName)
        return answered == null || answered < launched
    }

    /** Ids of the games whose patch is older than the current one: the ones the patch button is for. */
    fun observePatchesToApply(): Flow<Set<Int>> = installer.observeOutdatedPatches()

    /** Opens the screen where the player resolves the game's save conflict. */
    fun conflictIntent(appId: Int): Intent? = installed.all()[appId]?.let { packageName ->
        Intent().setClassName(context.packageName, "app.gameport.feature.sync.SyncActivity")
            .putExtra("pkg", packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun issuesFor(
        appId: Int,
        patchOutdated: Boolean,
        conflictPackages: Set<String>,
        statuses: Map<Int, SyncStatus>,
        updateAvailable: Boolean,
        suspected: Map<String, Suspicion>,
        reinstallAnswered: Set<Int> = emptySet(),
    ): List<GameIssue> {
        val packageName = installed.all()[appId] ?: return emptyList()
        return buildList {
            if (updateAvailable) add(GameIssue.UpdateAvailable)
            if (patchOutdated) add(GameIssue.PatchOutdated)
            if (refusedByDevice(appId, packageName)) add(GameIssue.RefusedByDevice)
            // Only when the access can be given: a note that sends the player to a setting that does not exist is worse than none.
            if (installer.storagePermissionBlocksPlay(appId)) add(GameIssue.StoragePermissionMissing)
            if (appId !in reinstallAnswered && installer.reinstallMayHelp(packageName)) add(GameIssue.ReinstallMayHelp)
            if (packageName in conflictPackages) add(GameIssue.SaveConflict)
            suspected[packageName]?.let { add(GameIssue.ProblemSuspected(crash = it == Suspicion.CRASH)) }
            when (statuses[appId]) {
                SyncStatus.OFFLINE -> add(GameIssue.SaveSyncFailed(offline = true))
                SyncStatus.FAILED -> add(GameIssue.SaveSyncFailed(offline = false))
                else -> Unit
            }
        }
    }
}

/** What the issues of one game are made of besides the patch, the conflicts, the sync and the controllers: kept together for the combine. */
private class Others(
    val suspected: Map<String, Suspicion>,
    val unchecked: Set<Int>,
    val missing: Map<Int, Int>,
    val answered: Set<Int>,
)

/** How long after a launch from GamePort a game that does start is given to answer through its hook (it runs as its process starts, so a couple of seconds), before a run that said nothing is taken for a refusal. */
private const val ANSWER_WAIT_MS = 5_000L
