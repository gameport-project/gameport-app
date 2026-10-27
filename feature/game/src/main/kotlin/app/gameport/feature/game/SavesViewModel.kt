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

/** A slow action on the Windows saves. */
enum class PcBusy { LINKING, RESTORING, CLEARING }

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

    /** What the page is doing with the Windows saves that takes a moment, or null. */
    private val _busy = MutableStateFlow<PcBusy?>(null)
    val busy: StateFlow<PcBusy?> = _busy.asStateFlow()

    /** The last request that failed, until the page shows it. */
    private val _failed = MutableStateFlow<PcBusy?>(null)
    val failed: StateFlow<PcBusy?> = _failed.asStateFlow()

    fun onUsePcChanged(on: Boolean) = saves.setUsePc(appId, on)

    /** How many files the last clearing of this device's saves put aside, until the page is left. */
    private val _cleared = MutableStateFlow<Int?>(null)
    val cleared: StateFlow<Int?> = _cleared.asStateFlow()

    /** Puts this device's saves aside now (the game must be closed); nothing is removed from Steam. */
    fun onClearLocal() {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = PcBusy.CLEARING
            _cleared.value = null
            val outcome = saves.clearLocal(appId)
            _failed.value = if (outcome.result == app.gameport.core.sync.LocalSavesCleaner.Result.DONE) null else PcBusy.CLEARING
            if (outcome.result == app.gameport.core.sync.LocalSavesCleaner.Result.DONE) _cleared.value = outcome.moved
            _busy.value = null
        }
    }

    fun onLinkPc() = run(PcBusy.LINKING) { saves.linkPc(appId) }

    fun onUnlinkPc() = saves.unlinkPc(appId)

    fun onRestorePc() = run(PcBusy.RESTORING) { saves.restorePc(appId) }

    fun onFailureShown() {
        _failed.value = null
    }

    private fun run(what: PcBusy, action: suspend () -> Boolean) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = what
            _failed.value = if (action()) null else what
            _busy.value = null
            refresh()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
