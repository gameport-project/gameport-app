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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the player asks of the device's graphics for one game: the size of the image, the frame rate, and how much the processor and the graphics chip may work. */
@HiltViewModel
class GamePerformanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    library: SteamLibraryRepository,
    private val settings: GameSettingsStore,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<GamePerformanceRoute>().appId

    val gameName: StateFlow<String> = library.observeGame(appId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    val gameSettings: StateFlow<GameSettings> = settings.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), settings.get(appId))

    /** What the device says about the image it shows, read once: the screen's side for one eye and the side the system recommends. */
    val eyeSides = settings.eyeSides()

    fun onResolutionChosen(percent: Int) = settings.set(appId, gameSettings.value.copy(resolutionPercent = percent))
    fun onRefreshRateChosen(rate: Int) = settings.set(appId, gameSettings.value.copy(refreshRate = rate))
    fun onCpuLevelChosen(level: Int) = settings.set(appId, gameSettings.value.copy(cpuLevel = level))
    fun onGpuLevelChosen(level: Int) = settings.set(appId, gameSettings.value.copy(gpuLevel = level))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
