package app.gameport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.model.AuthState
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: SteamAuthRepository,
    private val installer: GameInstallRepository,
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.authState

    /** While installs run the screen must stay on, or the headset locks and pauses them. */
    val keepScreenOn: StateFlow<Boolean> = installer.isBusy

    init {
        viewModelScope.launch { authRepository.restoreSession() }
    }
}
