package app.gameport.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.GameSettings
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.model.PassthroughGames
import app.gameport.core.model.PassthroughMode
import app.gameport.core.model.RecenterMode
import app.gameport.core.settings.ControllerMappingStore
import app.gameport.core.settings.GameDefaults
import app.gameport.core.settings.GameSettingsStore
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What can be turned on for one game that does not work as it should: each switch is off until the player chooses it. */
@HiltViewModel
class GameCompatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    library: SteamLibraryRepository,
    private val controllers: ControllerMappingStore,
    private val settings: GameSettingsStore,
    private val gameDefaults: GameDefaults,
    private val installer: GameInstallRepository,
    device: app.gameport.core.device.DeviceProfile,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<GameCompatRoute>().appId

    /** The headset family this device is (`pico`, `meta`, `openxr`), null when it is not a headset. */
    private val deviceFamily: String? = device.vrPlatform?.id

    val gameName: StateFlow<String> = library.observeGame(appId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    val mapping: StateFlow<ControllerMapping> = controllers.observe(appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, controllers.get(appId))

    val gameSettings: StateFlow<GameSettings> = settings.observe(appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.get(appId))

    /** The layer gave the game a space that follows the recentering by itself, because the headset has no play area. */
    val stageFallback: StateFlow<Boolean> = settings.stageFallback(appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Whether to offer the choice of opening the game itself in VR: when the game holds the entry the patch gave it, or when the player turned it off
     * (to be able to turn it on again). Nothing is offered for a game that never needed it.
     */
    val showOpenInVr: StateFlow<Boolean> = combine(installer.observeVrGameActivityEntry(appId), gameSettings) { entry, chosen -> entry || !chosen.openInVr }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Whether to offer the choice of the stand-in for Meta's OVRPlugin: when the game holds it, or when the player turned it off (to turn it on again). */
    val showOvrPluginStandIn: StateFlow<Boolean> = combine(installer.observeOvrPluginStandIn(appId), gameSettings) { held, chosen -> held || !chosen.ovrPluginStandIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** The choice of the foveation that follows the eyes is offered on a Pico, where a runtime may crash on it when the device has no eye tracking. */
    val showEyeFoveation: Boolean = device.vrPlatform?.id == "pico"

    fun onEyeFoveationChosen(mode: app.gameport.core.model.EyeFoveationMode) = settings.set(appId, gameSettings.value.copy(eyeFoveation = mode))

    /** The strength of the controllers' vibration is offered on any headset: it applies to every game, and the game's own is the first choice. */
    val showHaptics: Boolean = device.isHeadset

    fun onHapticsChosen(percent: Int) = settings.set(appId, gameSettings.value.copy(hapticsPercent = percent))

    /** What GamePort can ask of the graphics is offered on a Pico: its runtime takes the image size, the frame rate and the levels. */
    val showPerformance: Boolean = device.vrPlatform?.id == app.gameport.core.model.vr.PicoPlatform.id

    fun onResolutionChosen(percent: Int) = settings.set(appId, gameSettings.value.copy(resolutionPercent = percent))
    fun onRefreshRateChosen(rate: Int) = settings.set(appId, gameSettings.value.copy(refreshRate = rate))
    fun onCpuLevelChosen(level: Int) = settings.set(appId, gameSettings.value.copy(cpuLevel = level))
    fun onGpuLevelChosen(level: Int) = settings.set(appId, gameSettings.value.copy(gpuLevel = level))

    fun onUseFrameChanged(useFrame: Boolean) = controllers.setUseFrame(appId, useFrame)

    /** Automatic leaves it to GamePort; on and off are the player's choice, kept even where GamePort would have decided otherwise. */
    fun onRecenterChosen(mode: RecenterMode) = settings.set(appId, gameSettings.value.copy(recenter = mode))

    /** Automatic gives the game's own activity the VR entry, off takes it away. Either way the game is patched again, which is what applies the choice. */
    fun onOpenInVrChosen(automatic: Boolean) {
        if (gameSettings.value.openInVr == automatic) return
        settings.set(appId, gameSettings.value.copy(openInVr = automatic))
        installer.repatch(appId)
    }

    /** Whether the game declares the headset's passthrough feature: automatic follows the list of games known to need it. A change patches the game again, which applies it. */
    fun onPassthroughChosen(mode: PassthroughMode) {
        val told = gameDefaults.list.passthrough
        val before = PassthroughGames.declared(gameSettings.value.passthrough, appId, deviceFamily, told)
        settings.set(appId, gameSettings.value.copy(passthrough = mode))
        if (PassthroughGames.declared(mode, appId, deviceFamily, told) != before) installer.repatch(appId)
    }

    /** True when the list, and not the player, gives this game the declaration. */
    val passthroughListed: Boolean get() = PassthroughGames.wanted(appId, deviceFamily, gameDefaults.list.passthrough)

    /** True when this device refuses a game whose package name it knows but whose signature is not the one it signed (see [app.gameport.core.model.RenamedPackages]). */
    val refusesReSignedGames: Boolean = device.vrPlatform?.refusesReSignedGames == true

    /** True when the game is on the device under a name that is not the one it must have now: installing it again applies the choice. */
    val renameNeeded: StateFlow<Boolean> = installer.observeRenameNeeded(appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Whether the game is installed under a name of GamePort's own: automatic follows this device (the ones whose store refuses a re-signed game get it), on and
     * off are the player's choice. A change that moves the game to the other name installs it again, which leaves what it kept on this device behind.
     */
    fun onPackageNameChosen(mode: app.gameport.core.model.PackageNameMode) {
        if (gameSettings.value.packageName == mode) return
        val before = installer.renamedPackageFor(appId)
        settings.set(appId, gameSettings.value.copy(packageName = mode))
        if (installer.renamedPackageFor(appId) != before) installer.repatch(appId)
    }

    /** Automatic gives the game the stand-in when it needs one, off takes away the one GamePort gave. Either way the game is patched again, which applies the choice. */
    fun onOvrPluginStandInChosen(automatic: Boolean) {
        if (gameSettings.value.ovrPluginStandIn == automatic) return
        settings.set(appId, gameSettings.value.copy(ovrPluginStandIn = automatic))
        installer.repatch(appId)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
