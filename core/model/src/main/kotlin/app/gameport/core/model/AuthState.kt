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

    /** The account name and password were sent and Steam waits for the Steam Guard code. */
    data class AwaitingCode(val kind: GuardCodeKind, val emailHint: String?, val wrongCode: Boolean) : AuthState

    /** [badCredentials]: Steam refused the account name or the password, which the screen says in its own words. */
    data class Failed(val reason: String?, val badCredentials: Boolean = false) : AuthState

    data class SignedIn(val account: SteamAccount) : AuthState
}

/** Where the Steam Guard code comes from: the Steam mobile app, or an e-mail sent by Steam. */
enum class GuardCodeKind { APP, EMAIL }

/** How GamePort stands with Steam while someone is signed in. */
enum class SteamConnection {
    /** Connected. */
    ONLINE,

    /** Not connected, and trying again by itself. */
    CONNECTING,

    /** The player chose offline mode: GamePort stays off Steam. */
    OFFLINE_MODE,

    /** Not connected, and the attempts to reconnect ran out for now. */
    UNREACHABLE,
}
