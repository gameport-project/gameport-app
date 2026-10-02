package app.gameport.core.sync

import android.content.Intent
import app.gameport.core.device.DeviceProfile
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.GameUpdatesRepository
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.GameIssue
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
    private val installer: GameInstallRepository,
    private val coordinator: CloudSyncCoordinator,
    private val syncStatus: SyncStatusStore,
    private val installed: InstalledGames,
    private val updates: GameUpdatesRepository,
    private val controllers: ControllerMappingStore,
    private val device: DeviceProfile,
    private val reports: ReportStore,
) {
    // Permissions are granted outside GamePort, so the screens ask for a re-check when they come back.
    private val recheck = MutableStateFlow(0)

    fun refresh() = recheck.update { it + 1 }

    fun observe(appId: Int): Flow<List<GameIssue>> = combine(
        installer.observePatchOutdated(appId),
        coordinator.conflicts,
        syncStatus.statuses,
        combine(recheck, updates.updates, controllers.observe(appId)) { _, list, mapping ->
            list.any { it.appId == appId } to mapping.takeIf { device.isHeadset && it.detected.isNotEmpty() && !it.noticeSeen }?.source
        },
        reports.suspected,
    ) { patchOutdated, conflicts, statuses, (updateAvailable, mappingNoticeSource), suspected ->
        // The controller notice is informative: it is listed on the game's page but never lights the cover badge.
        issuesFor(appId, patchOutdated, conflicts.keys, statuses, updateAvailable, suspected) +
            if (mappingNoticeSource != null) listOf(GameIssue.ControllerMappingAvailable(mappingNoticeSource)) else emptyList()
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
            issuesFor(appId, appId in outdated, conflicts.keys, statuses, updateAvailable = false, suspected = suspected).isNotEmpty()
        }.toSet()
    }

    /** Opens the screen where the player resolves the game's save conflict. */
    fun conflictIntent(appId: Int): Intent? = installed.all()[appId]?.let { packageName ->
        Intent().setClassName("app.gameport", "app.gameport.feature.sync.SyncActivity")
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
    ): List<GameIssue> {
        val packageName = installed.all()[appId] ?: return emptyList()
        return buildList {
            if (updateAvailable) add(GameIssue.UpdateAvailable)
            if (patchOutdated) add(GameIssue.PatchOutdated)
            if (installer.shouldExplainStoragePermission(appId)) add(GameIssue.StoragePermissionMissing)
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
