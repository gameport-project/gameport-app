package app.gameport.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.model.AuthState
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: SteamAuthRepository,
) : ViewModel() {
    val authState: StateFlow<AuthState> = repository.authState

    fun onSignInClicked() {
        viewModelScope.launch { repository.beginQrSignIn() }
    }

    fun onCancelClicked() {
        repository.cancelSignIn()
    }
}
