package app.gameport.core.steam.session

data class StoredCredentials(val accountName: String, val refreshToken: String)

/** Persists the Steam refresh token. Implementations must keep it encrypted at rest. */
interface TokenStore {
    fun load(): StoredCredentials?

    fun save(credentials: StoredCredentials)

    fun clear()
}
