package app.gameport.feature.game

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.steam.SteamLibraryRepository
import app.gameport.core.steam.SteamWebSession
import app.gameport.core.steam.session.WebCookies
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the page of the Workshop is waiting for: the cookies that make Steam see the account, or the word that they could not be made. */
sealed interface WorkshopState {
    data object Loading : WorkshopState
    data class Ready(val cookies: WebCookies) : WorkshopState
    data object Unavailable : WorkshopState
}

/**
 * The Workshop of a game, shown as the web page of Steam with the account already signed in: the cookies are made from the session open in GamePort, so there is no new sign-in.
 * Subscribing to an item on that page is all that is needed: GamePort follows what the account is subscribed to and downloads it for the game (see WorkshopRepository).
 */
@HiltViewModel
class WorkshopViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val web: SteamWebSession,
    private val installer: GameInstallRepository,
    private val playtime: app.gameport.core.sync.PlaytimeTracker,
    library: SteamLibraryRepository,
) : ViewModel() {
    val appId = savedStateHandle.toRoute<GameWorkshopRoute>().appId

    val gameName: StateFlow<String> = library.observeGame(appId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    private val isVr: StateFlow<Boolean?> = library.observeGame(appId)
        .map { it?.androidBuild?.isVr }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** True while the game is there to go back to, false when it has to be launched: looked at again every few seconds, as the game can stop while the page is open. */
    val gameRunning: StateFlow<Boolean> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(installer.installedPackage(appId)?.let(playtime::isActive) == true)
            kotlinx.coroutines.delay(RUNNING_POLL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private val _state = MutableStateFlow<WorkshopState>(WorkshopState.Loading)
    val state: StateFlow<WorkshopState> = _state.asStateFlow()

    init {
        load()
    }

    /** Asks again for the cookies, after a failure. */
    fun load() {
        _state.value = WorkshopState.Loading
        viewModelScope.launch {
            _state.value = web.cookies()?.let { WorkshopState.Ready(it) } ?: WorkshopState.Unavailable
        }
    }

    /** What starts the game again, or brings it back to the front when it is still running: GamePort itself is not left. */
    fun launchIntent(): Intent? = installer.launchIntent(appId, isVr.value)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RUNNING_POLL_MS = 2_000L
    }
}
