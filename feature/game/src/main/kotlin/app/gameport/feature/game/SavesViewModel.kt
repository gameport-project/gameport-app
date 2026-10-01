package app.gameport.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.model.SaveOverview
import app.gameport.core.steam.SteamLibraryRepository
import app.gameport.core.sync.SaveStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SavesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saves: SaveStateRepository,
    library: SteamLibraryRepository,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<GameSavesRoute>().appId

    val gameName: StateFlow<String> = library.observeGame(appId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    val overview: StateFlow<SaveOverview> = saves.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SaveOverview(emptyList(), 0, 0, null))

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** True when the last look at Steam Cloud could not be made. */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _offline.value = !saves.refreshCloud(appId)
            _refreshing.value = false
        }
    }

    fun onRestore() = saves.requestRestore(appId)

    fun onSend() = saves.requestSend(appId)

    fun onCancelPending() = saves.cancel(appId)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
