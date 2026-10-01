package app.gameport.core.steam

import app.gameport.core.model.AuthState
import kotlinx.coroutines.flow.StateFlow

interface SteamAuthRepository {
    val authState: StateFlow<AuthState>

    /** Restores a previous session from the stored refresh token, if any. */
    suspend fun restoreSession()

    /** Starts a QR sign-in; returns when the flow succeeded, failed or was cancelled. */
    suspend fun beginQrSignIn()

    fun cancelSignIn()

    suspend fun signOut()
}
