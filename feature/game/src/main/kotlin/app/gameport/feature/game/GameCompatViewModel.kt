package app.gameport.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.GameSettings
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.model.RecenterMode
import app.gameport.core.settings.ControllerMappingStore
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
    private val installer: GameInstallRepository,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<GameCompatRoute>().appId

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

    fun onUseFrameChanged(useFrame: Boolean) = controllers.setUseFrame(appId, useFrame)

    /** Automatic leaves it to GamePort; on and off are the player's choice, kept even where GamePort would have decided otherwise. */
    fun onRecenterChosen(mode: RecenterMode) = settings.set(appId, gameSettings.value.copy(recenter = mode))

    /** Automatic gives the game's own activity the VR entry, off takes it away. Either way the game is patched again, which is what applies the choice. */
    fun onOpenInVrChosen(automatic: Boolean) {
        if (gameSettings.value.openInVr == automatic) return
        settings.set(appId, gameSettings.value.copy(openInVr = automatic))
        installer.repatch(appId)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
