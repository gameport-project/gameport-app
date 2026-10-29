package app.gameport.feature.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.steam.SteamWebSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A page of Steam shown inside GamePort, for a link that leads to Steam (the page of a game in the shop, an extra content): its address and the name it is shown under. The page opens
 * with the account signed in when there is a session, and without when there is none.
 */
@kotlinx.serialization.Serializable
data class SteamPageRoute(val url: String, val title: String)

@HiltViewModel
class SteamPageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val web: SteamWebSession,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<SteamPageRoute>()
    val url: String = route.url
    val title: String = route.title

    private val _state = MutableStateFlow<WorkshopState>(WorkshopState.Loading)
    val state: StateFlow<WorkshopState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = WorkshopState.Loading
        viewModelScope.launch {
            // A page of the shop is fine without the account: it is not waited for long, and the page opens either way.
            _state.value = WorkshopState.Ready(withTimeoutOrNull(COOKIES_WAIT_MS) { web.cookies() })
        }
    }

    private companion object {
        const val COOKIES_WAIT_MS = 4_000L
    }
}

@Composable
fun SteamPageScreen(onBack: () -> Unit, viewModel: SteamPageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SteamBrowser(
        title = viewModel.title,
        subtitle = android.net.Uri.parse(viewModel.url).host.orEmpty(),
        hint = null,
        startUrl = viewModel.url,
        state = state,
        onRetry = viewModel::load,
        onBack = onBack,
    )
}
