package app.gameport.core.steam

import android.os.Build
import app.gameport.core.model.AuthState
import app.gameport.core.model.SteamAccount
import app.gameport.core.steam.cache.LibraryCacheStore
import app.gameport.core.steam.session.SessionIdentity
import app.gameport.core.steam.session.SteamSession
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.StoredCredentials
import app.gameport.core.steam.session.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class JavaSteamAuthRepository @Inject constructor(
    private val tokenStore: TokenStore,
    private val sessions: SteamSessionHolder,
    private val libraryCache: LibraryCacheStore,
) : SteamAuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.Connecting)
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    private val restoreLock = Mutex()
    private var session: SteamSession? = null
    private var signInJob: kotlinx.coroutines.Job? = null
    private val deviceName = "GamePort (${Build.MODEL})"

    override suspend fun restoreSession() = withContext(Dispatchers.IO) {
        restoreLock.withLock { restoreOnce() }
    }

    // Safe to call from several places (the UI, the save sync): the first one does the work.
    private suspend fun restoreOnce() {
        if (state.value is AuthState.SignedIn) return
        val stored = tokenStore.load()
        if (stored == null) {
            state.value = AuthState.SignedOut
            return
        }
        try {
            val identity = newSession().logOn(stored.accountName, stored.refreshToken, deviceName)
            sessions.set(session)
            state.value = AuthState.SignedIn(identity.toAccount())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            closeSession()
            state.value = AuthState.SignedOut
        }
    }

    override suspend fun beginQrSignIn() = withContext(Dispatchers.IO) {
        signInJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        try {
            val steam = newSession()
            val credentials: StoredCredentials = steam.authenticateWithQr(deviceName) { url ->
                state.value = AuthState.AwaitingConfirmation(url)
            }
            tokenStore.save(credentials)
            val identity: SessionIdentity = steam.logOn(credentials.accountName, credentials.refreshToken, deviceName)
            sessions.set(session)
            state.value = AuthState.SignedIn(identity.toAccount())
        } catch (e: CancellationException) {
            closeSession()
            state.value = AuthState.SignedOut
            throw e
        } catch (e: Exception) {
            closeSession()
            state.value = AuthState.Failed(e.message)
        }
    }

    override fun cancelSignIn() {
        signInJob?.cancel()
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        tokenStore.clear()
        libraryCache.clear()
        closeSession()
        state.value = AuthState.SignedOut
    }

    private fun newSession(): SteamSession {
        closeSession()
        return SteamSession().also { session = it }
    }

    private fun closeSession() {
        sessions.set(null)
        session?.disconnect()
        session = null
    }

    private fun SessionIdentity.toAccount() = SteamAccount(steamId = steamId, displayName = displayName)
}
