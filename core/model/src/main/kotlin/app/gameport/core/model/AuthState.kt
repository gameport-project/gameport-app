package app.gameport.core.model

data class SteamAccount(
    val steamId: Long,
    val displayName: String,
)

sealed interface AuthState {
    /** Checking for a saved session at startup; the sign-in prompt must wait for the answer. */
    data object Connecting : AuthState

    data object SignedOut : AuthState

    data class AwaitingConfirmation(val challengeUrl: String) : AuthState

    data class Failed(val reason: String?) : AuthState

    data class SignedIn(val account: SteamAccount) : AuthState
}
