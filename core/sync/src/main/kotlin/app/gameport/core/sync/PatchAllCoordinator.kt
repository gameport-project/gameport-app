package app.gameport.core.sync

import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.PatchAllInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the "patch all" buttons of the home and of the settings need: how many games are behind, where the run stands, and the run itself.
 * A game on screen is left out, since updating it would close it.
 */
@Singleton
class PatchAllCoordinator @Inject constructor(
    private val installer: GameInstallRepository,
    private val installed: InstalledGames,
    private val playtime: PlaytimeTracker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun observe(): Flow<PatchAllInfo> = combine(installer.observeOutdatedPatches(), installer.patchAll) { behind, progress ->
        PatchAllInfo(behind.size, progress)
    }

    /** Stops the run that is going on. */
    fun stop() = installer.cancelPatchAll()

    /** Closes the report of a run that ended. */
    fun clear() = installer.clearPatchAll()

    /** Patches every game that is behind and not on screen, one after the other. */
    fun patchAll() {
        scope.launch {
            val behind = withTimeoutOrNull(5_000) { installer.observeOutdatedPatches().first() } ?: return@launch
            val ids = behind.sorted().filterNot { appId -> installed.all()[appId]?.let(playtime::isOnScreen) == true }
            installer.repatchAll(ids)
        }
    }
}
