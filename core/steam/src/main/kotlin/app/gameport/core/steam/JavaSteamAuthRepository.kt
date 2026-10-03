package app.gameport.core.steam

import android.os.Build
import app.gameport.core.model.AuthState
import app.gameport.core.model.SteamConnection
import app.gameport.core.model.SteamAccount
import app.gameport.core.steam.cache.LibraryCacheStore
import app.gameport.core.steam.session.CachedIdentity
import app.gameport.core.steam.session.SessionIdentity
import app.gameport.core.steam.session.SteamIdentityStore
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import app.gameport.core.model.GuardCodeKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture
import app.gameport.core.steam.session.SteamSession
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.StoredCredentials
import app.gameport.core.steam.session.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val identities: SteamIdentityStore,
    private val achievementCache: app.gameport.core.steam.cache.AchievementCache,
) : SteamAuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<AuthState>(AuthState.Connecting)
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    private val _offline = MutableStateFlow(false)
    override val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private val _connection = MutableStateFlow(SteamConnection.ONLINE)
    override val connection: StateFlow<SteamConnection> = _connection.asStateFlow()

    private fun setConnection(value: SteamConnection) {
        _connection.value = value
        _offline.value = value != SteamConnection.ONLINE
    }

    private var reconnectJob: kotlinx.coroutines.Job? = null

    private val restoreLock = Mutex()
    private var session: SteamSession? = null
    private var signInJob: kotlinx.coroutines.Job? = null
    @Volatile private var guardPrompt: GuardPrompt? = null
    private val deviceName = "GamePort (${Build.MODEL})"

    override suspend fun restoreSession() = withContext(Dispatchers.IO) {
        restoreLock.withLock { restoreOnce() }
    }

    // Safe to call from several places (the UI, the save sync): the first one does the work.
    private suspend fun restoreOnce() {
        val chosenOffline = identities.offlineMode.value
        val current = session
        if (state.value is AuthState.SignedIn) {
            if (chosenOffline || current?.isAlive == true) return
            // Signed in, but Steam may have dropped the connection since: every request would then fail until restart.
            if (current != null) { reviveOnce(current); return }
        }
        val stored = tokenStore.load()
        if (stored == null) {
            state.value = AuthState.SignedOut
            return
        }
        val known = identities.identity()
        if (chosenOffline && known != null) {
            goOffline(known, chosen = true)
            return
        }
        try {
            val identity = newSession().logOn(stored.accountName, stored.refreshToken, deviceName)
            sessions.set(session)
            session?.let(::watch)
            identities.save(CachedIdentity(identity.steamId, identity.displayName))
            setConnection(SteamConnection.ONLINE)
            state.value = AuthState.SignedIn(identity.toAccount())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            closeSession()
            // Steam refusing the token means signed out; not reaching Steam only means offline.
            if (known == null || (e is AuthenticationException && e.result !in TRANSIENT_RESULTS)) state.value = AuthState.SignedOut else goOffline(known, chosen = false)
        }
    }

    /** Keeps the account without a connection: the saved identity stands in for the session. */
    private fun goOffline(identity: CachedIdentity, chosen: Boolean) {
        closeSession()
        state.value = AuthState.SignedIn(SteamAccount(identity.steamId, identity.displayName))
        if (chosen) {
            reconnectJob?.cancel()
            setConnection(SteamConnection.OFFLINE_MODE)
        } else {
            // Steam could not be reached: keep trying in the background.
            startReconnect(null)
        }
    }

    override suspend fun setOfflineMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        restoreLock.withLock {
            identities.setOfflineMode(enabled)
            if (enabled) {
                val known = identities.identity()
                    ?: (state.value as? AuthState.SignedIn)?.account?.let { CachedIdentity(it.steamId, it.displayName) }
                if (known != null) {
                    identities.save(known)
                    goOffline(known, chosen = true)
                } else {
                    identities.setOfflineMode(false)
                }
            }
        }
        if (!enabled) restoreSession()
    }

    override suspend fun beginQrSignIn() = withContext(Dispatchers.IO) {
        signIn { steam ->
            steam.authenticateWithQr(deviceName) { url ->
                state.value = AuthState.AwaitingConfirmation(url)
            }
        }
    }

    override suspend fun beginCredentialsSignIn(accountName: String, password: String) = withContext(Dispatchers.IO) {
        val prompt = GuardPrompt { kind, hint, wrong -> state.value = AuthState.AwaitingCode(kind, hint, wrong) }
        guardPrompt = prompt
        state.value = AuthState.Connecting
        try {
            signIn { steam -> steam.authenticateWithCredentials(deviceName, accountName.trim(), password, prompt) }
        } finally {
            prompt.cancel()
            guardPrompt = null
        }
    }

    override fun submitGuardCode(code: String) {
        guardPrompt?.answer(code.trim())
    }

    /** What both sign-ins share: [authenticate] gets the credentials, which are kept for the next start, then the session logs on. */
    private suspend fun signIn(authenticate: suspend (SteamSession) -> StoredCredentials) {
        signInJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        try {
            val steam = newSession()
            val credentials = authenticate(steam)
            tokenStore.save(credentials)
            val identity: SessionIdentity = steam.logOn(credentials.accountName, credentials.refreshToken, deviceName)
            sessions.set(session)
            watch(steam)
            identities.save(CachedIdentity(identity.steamId, identity.displayName))
            setConnection(SteamConnection.ONLINE)
            state.value = AuthState.SignedIn(identity.toAccount())
        } catch (e: CancellationException) {
            closeSession()
            state.value = AuthState.SignedOut
            throw e
        } catch (e: Exception) {
            closeSession()
            val refused = e is AuthenticationException && e.result in BAD_CREDENTIALS_RESULTS
            state.value = AuthState.Failed(e.message, badCredentials = refused)
        }
    }

    override fun cancelSignIn() {
        signInJob?.cancel()
    }

    override fun resetSignIn() {
        if (state.value is AuthState.Failed) state.value = AuthState.SignedOut
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        tokenStore.clear()
        libraryCache.clear()
        achievementCache.clear()
        identities.clear()
        reconnectJob?.cancel()
        setConnection(SteamConnection.ONLINE)
        closeSession()
        state.value = AuthState.SignedOut
    }

    /** Reconnects by itself when Steam drops the connection of [steam]. */
    private fun watch(steam: SteamSession) {
        steam.onLost = { if (session === steam) startReconnect(steam) }
    }

    /**
     * Tries to get back to Steam in the background, with growing pauses: [dead] is the session that was lost, or
     * null when GamePort never got connected. Gives up after the last pause; the next request tries again.
     */
    private fun startReconnect(dead: SteamSession?) {
        if (reconnectJob?.isActive == true) return
        setConnection(SteamConnection.CONNECTING)
        reconnectJob = scope.launch {
            for (pause in RECONNECT_PAUSES_MS) {
                delay(pause)
                if (identities.offlineMode.value) return@launch
                val done = restoreLock.withLock { if (dead != null) reviveOnce(dead) else retryLogOn() }
                if (done) return@launch
            }
            setConnection(SteamConnection.UNREACHABLE)
        }
    }

    /** One more try at logging on with the saved token when there is no session at all. */
    private suspend fun retryLogOn(): Boolean {
        if (session?.isAlive == true) return true
        val stored = tokenStore.load() ?: return true
        return try {
            val identity = newSession().logOn(stored.accountName, stored.refreshToken, deviceName)
            sessions.set(session)
            session?.let(::watch)
            identities.save(CachedIdentity(identity.steamId, identity.displayName))
            setConnection(SteamConnection.ONLINE)
            state.value = AuthState.SignedIn(identity.toAccount())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            closeSession()
            false
        }
    }

    /** Logs on again with the saved token on a new connection and swaps it in. False if that did not work. */
    private suspend fun reviveOnce(dead: SteamSession): Boolean {
        if (session !== dead) return true
        val stored = tokenStore.load() ?: return false
        val fresh = SteamSession()
        return try {
            val identity = fresh.logOn(stored.accountName, stored.refreshToken, deviceName)
            session = fresh
            sessions.set(fresh)
            watch(fresh)
            dead.disconnect()
            setConnection(SteamConnection.ONLINE)
            state.value = AuthState.SignedIn(identity.toAccount())
            true
        } catch (e: CancellationException) {
            fresh.disconnect()
            throw e
        } catch (e: Exception) {
            fresh.disconnect()
            false
        }
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

/** How long to wait before each attempt to reconnect after Steam dropped the connection. */
private val RECONNECT_PAUSES_MS = listOf(3_000L, 10_000L, 30_000L, 60_000L, 120_000L)

/** Results that mean Steam could not answer right now, not that the saved sign-in is no longer valid. */
private val TRANSIENT_RESULTS = setOf(EResult.ServiceUnavailable, EResult.Timeout, EResult.TryAnotherCM, EResult.Busy, EResult.NoConnection)

/** Results that mean the account name or the password was wrong. */
private val BAD_CREDENTIALS_RESULTS = setOf(EResult.InvalidPassword, EResult.AccountNotFound, EResult.InvalidLoginAuthCode)

/**
 * Asks the screen for the Steam Guard code and waits for the answer. The code of the mobile app is asked first, and the one sent by e-mail when
 * the account uses that. Confirming in the mobile app instead is not offered here: the QR sign-in is the way for that.
 */
private class GuardPrompt(private val ask: (GuardCodeKind, String?, Boolean) -> Unit) : IAuthenticator {
    @Volatile private var pending: CompletableDeferred<String>? = null

    override fun getDeviceCode(previousCodeWasIncorrect: Boolean): CompletableFuture<String> = request(GuardCodeKind.APP, null, previousCodeWasIncorrect)

    override fun getEmailCode(email: String?, previousCodeWasIncorrect: Boolean): CompletableFuture<String> = request(GuardCodeKind.EMAIL, email, previousCodeWasIncorrect)

    override fun acceptDeviceConfirmation(): CompletableFuture<Boolean> = CompletableFuture.completedFuture(false)

    fun answer(code: String) {
        pending?.complete(code)
    }

    fun cancel() {
        pending?.cancel()
    }

    private fun request(kind: GuardCodeKind, hint: String?, wrong: Boolean): CompletableFuture<String> {
        val deferred = CompletableDeferred<String>().also { pending = it }
        ask(kind, hint, wrong)
        return deferred.asCompletableFuture()
    }
}
