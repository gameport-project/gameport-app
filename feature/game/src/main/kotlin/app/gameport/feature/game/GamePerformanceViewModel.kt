package app.gameport.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.model.GameSettings
import app.gameport.core.settings.GameSettingsStore
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the player asks of the device's graphics for one game: the size of the image, the frame rate, and how much the processor and the graphics chip may work. */
@HiltViewModel
class GamePerformanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    library: SteamLibraryRepository,
    private val settings: GameSettingsStore,
    private val installer: app.gameport.core.install.GameInstallRepository,
    device: app.gameport.core.device.DeviceProfile,
    private val reports: app.gameport.core.sync.ReportStore,
) : ViewModel() {
    private val platform = app.gameport.core.model.vr.VrPlatforms.byId(device.vrPlatform?.id)

    private val appId = savedStateHandle.toRoute<GamePerformanceRoute>().appId

    val gameName: StateFlow<String> = library.observeGame(appId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    val gameSettings: StateFlow<GameSettings> = settings.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), settings.get(appId))

    /** What the device says about the image it shows, read once: the screen's side for one eye and the side the system recommends. */
    val eyeSides = settings.eyeSides(platform)

    /** Something else sets the graphics of this game (an app that knows it by its package name and is running, or system properties): it takes over, and the choices of this page would not be applied. */
    val optimizerState = settings.optimizerState(platform, installer.installedPackage(appId))

    /** The frame rates this device offers, the game's own (0) first. */
    val displayRates = settings.displayRates()

    /** The game's own (0), then the round rates the screen exposes, whatever the device: the platform's own list stands in only when the screen says nothing. */
    val refreshRates: List<Int> = run {
        // Once a game's run told which rates the runtime accepts, only those among the screen's are offered; the one already chosen stays so the box can show it.
        // What the runtime accepts, as a run of a game told (on a Quest only a game that says which Quests it is made for tells it: see ReportStore.saveGameData).
        val accepted = reports.acceptedRates()
        val exposed = if (accepted.isEmpty()) displayRates.rates else displayRates.rates.filter { it in accepted }.ifEmpty { accepted }
        val chosen = settings.get(appId).refreshRate
        (listOf(0) + app.gameport.core.model.PerformanceTuning.offeredRates(exposed, platform?.refreshRates.orEmpty())).let { if (chosen != 0 && chosen !in it) (it + chosen).sorted() else it }
    }

    /** What came of the rate chosen for this game in its last run (refused by the device, set by the game itself, not kept), or null when nothing is to be said. */
    val refreshOutcome: StateFlow<app.gameport.core.model.RefreshOutcome?> = gameSettings.map { current ->
        installer.installedPackage(appId)?.let(reports::refreshOutcome)?.takeIf { it.asked == current.refreshRate && it.verdict != app.gameport.core.model.RefreshOutcome.Verdict.KEPT }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * A frame rate is chosen on a Meta headset and the installed game does not declare the Quests it is made for: the runtime would refuse the rate, so the game has to be
     * patched again (the patch adds the declaration when a rate is chosen).
     */
    val refreshNeedsPatch: StateFlow<Boolean> = combine(gameSettings, installer.observeAll()) { current, _ ->
        platform?.id == app.gameport.core.model.vr.MetaQuestPlatform.id && current.refreshRate != 0 &&
            installer.installedPackage(appId) != null && !installer.declaresSupportedDevices(appId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** What the last run of this game measured, shown at the top of the page. */
    internal val lastRun: StateFlow<LastRun?> = lastRunFlow(appId, reports, settings, installer, device.model)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onPatchAgain() = installer.repatch(appId)

    fun onResolutionChosen(percent: Int) = settings.set(appId, gameSettings.value.copy(resolutionPercent = percent))
    /** The rate, and with it the image size when the rate only comes with a smaller picture (one change, so the second does not overwrite the first). */
    fun onRefreshRateChosen(rate: Int, resolutionPercent: Int? = null) =
        settings.set(appId, gameSettings.value.copy(refreshRate = rate, resolutionPercent = resolutionPercent ?: gameSettings.value.resolutionPercent))
    fun onCpuLevelChosen(level: Int) = settings.set(appId, gameSettings.value.copy(cpuLevel = level))
    fun onGpuLevelChosen(level: Int) = settings.set(appId, gameSettings.value.copy(gpuLevel = level))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
