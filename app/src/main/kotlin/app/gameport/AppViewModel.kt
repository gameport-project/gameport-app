package app.gameport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.AuthState
import app.gameport.core.settings.TourController
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: SteamAuthRepository,
    private val installer: GameInstallRepository,
    private val installedGames: InstalledGames,
    settings: UserSettings,
    val tour: TourController,
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.authState

    /** While installs run the screen must stay on, or the headset locks and pauses them. */
    val keepScreenOn: StateFlow<Boolean> = installer.isBusy

    init {
        // A player who used GamePort before the tours keeps the screens as they are: the tours are in Settings > Help.
        tour.decideFirstRun(alreadyUsed = settings.whatsNewSeen.value > 0 || installedGames.all().isNotEmpty())
        viewModelScope.launch { authRepository.restoreSession() }
    }
}
