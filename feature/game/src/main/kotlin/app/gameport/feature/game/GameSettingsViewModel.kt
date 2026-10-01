package app.gameport.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.device.DeviceProfile
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.model.InstallState
import app.gameport.core.model.GameSettings
import app.gameport.core.model.PlayerDefaults
import app.gameport.core.settings.GameSettingsStore
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class GameSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: GameSettingsStore,
    library: SteamLibraryRepository,
    private val installer: GameInstallRepository,
    device: DeviceProfile,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<GameSettingsRoute>().appId

    /** The height and seated options only mean something on a headset. */
    val isHeadset: Boolean = device.isHeadset

    /** False for a flat game, which has no VR settings whatever the device. */
    val isVrGame: StateFlow<Boolean> = library.observeGame(appId)
        .map { it?.androidBuild?.isVr != false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val gameName: StateFlow<String> = library.observeGame(appId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    val settings: StateFlow<GameSettings> = store.observe(appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.get(appId))

    /** What every game uses unless it has its own height. */
    val defaults: StateFlow<PlayerDefaults> = store.defaults

    /** True when the game is installed and was patched by an older patcher. */
    val patchOutdated: StateFlow<Boolean> = installer.observePatchOutdated(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val installState: StateFlow<InstallState> = installer.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InstallState.NotInstalled)

    /** True when the player took the patch off this game. */
    val patchRemoved: StateFlow<Boolean> = installer.observePatchRemoved(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun appSettingsIntent() = installer.appSettingsIntent(appId)

    fun onRepatch() = installer.repatch(appId)

    fun onRemovePatch() = installer.removePatch(appId)

    fun onSeatedChanged(seated: Boolean) = store.set(appId, settings.value.copy(seated = seated))

    /** Moving the slider gives this game its own height. */
    fun onHeightChanged(heightCm: Int) = store.set(appId, settings.value.copy(heightCm = heightCm))

    /** Drops this game's own height, so it follows the global configuration again. */
    fun onUseGlobalConfiguration() = store.set(appId, settings.value.copy(heightCm = null))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
