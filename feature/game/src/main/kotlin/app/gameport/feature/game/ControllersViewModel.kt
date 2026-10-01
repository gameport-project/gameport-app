package app.gameport.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerMapping
import app.gameport.core.settings.ControllerMappingStore
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ControllersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: ControllerMappingStore,
    library: SteamLibraryRepository,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<GameControllersRoute>().appId

    val gameName: StateFlow<String> = library.observeGame(appId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    val mapping: StateFlow<ControllerMapping> = store.observe(appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.get(appId))

    init {
        // Opening the page answers the notice that offered it.
        store.markNoticeSeen(appId)
    }

    fun onEnabledChanged(enabled: Boolean) = store.setEnabled(appId, enabled)

    fun onTargetChosen(source: ControlRef, target: ControlRef?) = store.setOverride(appId, source, target)

    fun onReset() = store.reset(appId)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
