package app.gameport.core.steam

import app.gameport.core.model.AuthState
import app.gameport.core.model.SteamConnection
import kotlinx.coroutines.flow.StateFlow

interface SteamAuthRepository {
    val authState: StateFlow<AuthState>

    /**
     * True while GamePort runs without a connection to Steam: by the player's choice (offline mode) or because
     * Steam could not be reached. The library and installed games still work from what was saved.
     */
    val offline: StateFlow<Boolean>

    /** Why and how: connected, reconnecting, offline by choice, or unreachable. */
    val connection: StateFlow<SteamConnection>

    /** Offline mode: stays off Steam until turned off. Turning it off reconnects. */
    suspend fun setOfflineMode(enabled: Boolean)

    /**
     * Connects again with the saved token on a new connection, to take a changed download region into account. A download in progress on the
     * old connection stops and can be resumed. Does nothing when not signed in or in offline mode.
     */
    suspend fun reconnect()

    /** Restores a previous session from the stored refresh token, if any. */
    suspend fun restoreSession()

    /** Starts a QR sign-in; returns when the flow succeeded, failed or was cancelled. */
    suspend fun beginQrSignIn()

    /** Starts a sign-in with the account name and password; Steam Guard is then answered with [submitGuardCode]. */
    suspend fun beginCredentialsSignIn(accountName: String, password: String)

    /** The Steam Guard code asked for by [app.gameport.core.model.AuthState.AwaitingCode]. */
    fun submitGuardCode(code: String)

    fun cancelSignIn()

    /** Leaves a failed sign-in, back to the choice of how to sign in. */
    fun resetSignIn()

    suspend fun signOut()
}
