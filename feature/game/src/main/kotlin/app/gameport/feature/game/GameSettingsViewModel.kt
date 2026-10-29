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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GameSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: GameSettingsStore,
    private val library: SteamLibraryRepository,
    private val installer: GameInstallRepository,
    device: DeviceProfile,
    private val launchOptionsRepository: app.gameport.core.steam.LaunchOptionsRepository,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<GameSettingsRoute>().appId

    init {
        viewModelScope.launch { launchOptionsRepository.refresh(appId) }
    }

    /** The arguments Steam lists for starting the game. */
    val launchOptions: StateFlow<List<app.gameport.core.model.LaunchOption>> = launchOptionsRepository.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), launchOptionsRepository.cached(appId))

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

    /** The extra content (DLC) of the game that the account has, with what is on the device and the work in progress. */
    val extraContent: StateFlow<app.gameport.core.model.ExtraContent> = combine(
        library.observeGame(appId),
        installer.observeInstalledDlc(appId),
        installer.observeDlcWork(appId),
    ) { game, (installed, removable), work ->
        app.gameport.core.model.ExtraContent.of(game?.androidBuild?.dlc.orEmpty(), installed, removable, work)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), app.gameport.core.model.ExtraContent())

    /** The channels of the game on Steam that hold an Android build, the normal one included, in the order to show them. */
    val channels: StateFlow<List<app.gameport.core.model.UpdateChannel>> = library.observeGame(appId)
        .map { it?.androidBuild?.channels.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The channel this game is fetched from: the one picked, the normal one by default. */
    val channelInUse: StateFlow<String> = store.observe(appId)
        .map { it.channelName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), store.get(appId).channelName)

    /**
     * The player picks the channel the game is fetched from. An installed game is then installed again from it, over the one on the device (the saves stay, as in any update); a game that is
     * not installed will be fetched from it when it is.
     */
    fun onUseChannel(name: String) {
        val picked = if (name == app.gameport.core.model.UpdateChannels.NORMAL) null else name
        if (store.get(appId).channel == picked) return
        store.set(appId, store.get(appId).copy(channel = picked))
        viewModelScope.launch {
            val game = library.observeGame(appId).first() ?: return@launch
            if (installer.installedPackage(appId) != null) installer.update(game)
        }
    }

    fun onInstallDlc(dlcAppId: Int) {
        viewModelScope.launch { library.observeGame(appId).first()?.let { installer.installDlc(it, dlcAppId) } }
    }

    fun onRemoveDlc(dlcAppId: Int) {
        viewModelScope.launch { library.observeGame(appId).first()?.let { installer.removeDlc(it, dlcAppId) } }
    }

    fun onDismissDlcError() = installer.clearDlcError(appId)

    fun onRepatch() = installer.repatch(appId)

    fun onRemovePatch() = installer.removePatch(appId)

    /** The player picks the arguments to start the game with; null goes back to what Steam suggests. */
    fun onLaunchArgumentsChanged(picked: Set<String>?) = store.set(appId, settings.value.copy(launchArguments = picked))

    fun onSeatedChanged(seated: Boolean) = store.set(appId, settings.value.copy(seated = seated))

    /** Moving the slider gives this game its own height. */
    fun onHeightChanged(heightCm: Int) = store.set(appId, settings.value.copy(heightCm = heightCm))

    /** Drops this game's own height, so it follows the global configuration again. */
    fun onUseGlobalConfiguration() = store.set(appId, settings.value.copy(heightCm = null))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
