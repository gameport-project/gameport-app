package app.gameport.core.steam

import android.util.Log
import app.gameport.core.steam.session.SteamSession
import app.gameport.core.steam.session.changesSince
import app.gameport.core.steam.session.offlineLibrary
import app.gameport.core.steam.session.planLibraryChanges
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import app.gameport.core.model.AuthState
import app.gameport.core.model.Game
import app.gameport.core.model.Library
import app.gameport.core.model.visibleTo
import app.gameport.core.steam.cache.LibraryCacheStore
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.androidGames
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

/** One scan shared by every screen; it restarts when the signed-in session changes. */
@Singleton
class JavaSteamLibraryRepository @Inject constructor(
    private val sessions: SteamSessionHolder,
    private val cacheStore: LibraryCacheStore,
    auth: SteamAuthRepository,
    private val age: app.gameport.core.settings.AgeStore,
    private val adult: app.gameport.core.settings.AdultApps,
) : SteamLibraryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Raised each time the library has to be read again although nothing about the session changed: a free app was added to the account. */
    private val rescans = MutableStateFlow(0)

    fun rescan() = rescans.update { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val library: Flow<Library> = combine(sessions.current, auth.offline, auth.authState, rescans) { session, offline, state, round -> Scan(session, offline, state, round) }
        .distinctUntilChanged()
        .flatMapLatest { (session, offline, state) ->
            when {
                session != null -> session.androidGames(cacheStore)
                // Without a connection the library is what the last scan saved.
                offline && state is AuthState.SignedIn -> offlineLibrary(cacheStore, state.account.steamId and 0xFFFFFFFFL)
                else -> emptyFlow()
            }
        }
        .onEach { lib -> adult.learn(lib.games.filter { it.isAdult }.map { it.appId }) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** What the player may see: adult games are left out unless the date of birth says 18 or more (see AgePolicy). */
    private val visible: Flow<Library> = combine(library, age.canSeeAdult) { lib, canSeeAdult -> lib.visibleTo(canSeeAdult) }

    override fun observeLibrary(): Flow<Library> = visible

    override fun observeGame(appId: Int): Flow<Game?> = visible.map { lib -> lib.games.firstOrNull { it.appId == appId } }

    /** Held while Steam is asked what changed, so that two checks never run together. */
    private val checking = Mutex()
    private var lastCheckAt = 0L

    init {
        watchLicenses()
        checkWhileOpen()
    }

    /**
     * A game added to the account (bought, claimed, shared by the family) makes Steam send the new list of licenses on the open connection. The first list of a session is what the
     * first scan reads; a later one, with other packages, asks for a scan, which only asks about what it does not know.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchLicenses() {
        scope.launch {
            sessions.current.flatMapLatest { session ->
                session?.licenses?.filterNotNull()?.map { list -> list.map { it.packageID }.toSet() }?.distinctUntilChanged()?.drop(1) ?: emptyFlow()
            }.collect { rescan() }
        }
    }

    /** While there is a connection to Steam (GamePort is open, see ConnectionKeeper), Steam is asked what changed: soon after it is made, then every ten minutes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun checkWhileOpen() {
        scope.launch {
            sessions.current.flatMapLatest { session ->
                if (session == null) emptyFlow() else flow { delay(FIRST_CHECK_DELAY_MS); while (true) { emit(Unit); delay(CHECK_EVERY_MS) } }
            }.collect { checkForChanges(manual = false) }
        }
    }

    override fun refreshLibrary(manual: Boolean) {
        scope.launch { checkForChanges(manual) }
    }

    /**
     * Asks Steam which apps changed since the last check, as its own client does, and reads again only those of the library: a game the account already had that got an Android
     * build, a new build, a changed way to save. When Steam says it is too long ago to tell, everything is read again. Nothing is emptied, the games shown stay while it goes on.
     */
    private suspend fun checkForChanges(manual: Boolean) = checking.withLock {
        val session = sessions.current.value ?: return@withLock
        val now = System.currentTimeMillis()
        if (!manual && now - lastCheckAt < MIN_BETWEEN_CHECKS_MS) return@withLock
        lastCheckAt = now
        val account = session.accountId
        val last = cacheStore.changeNumber(account)
        val answer = runCatching { session.changesSince(last) }.onFailure { Log.w(TAG, "asking Steam what changed failed", it) }.getOrNull()
        if (answer == null) {
            if (manual) rescan()
            return@withLock
        }
        val plan = planLibraryChanges(ownedApps(session, account), answer, last)
        cacheStore.saveChangeNumber(account, plan.number)
        if (plan.forgetAll) cacheStore.forgetAllChecked(account) else if (plan.forget.isNotEmpty()) cacheStore.forgetChecked(account, plan.forget)
        Log.i(TAG, "changes since $last: " + if (plan.forgetAll) "too long ago, everything is read again" else "${plan.forget.size} app(s) of the library changed")
        if (plan.needsScan || manual) rescan()
    }

    /** The apps the account has a license for, as the last scan knew them. */
    private fun ownedApps(session: SteamSession, account: Long): Set<Int> {
        val cached = cacheStore.load(account) ?: return emptySet()
        val packages = session.licenses.value.orEmpty().map { it.packageID }.toSet()
        return cached.packageApps.filterKeys { it in packages }.values.flatten().toSet()
    }

    override fun refreshGame(appId: Int) {
        val account = sessions.current.value?.accountId ?: return
        cacheStore.forgetChecked(account, appId)
        rescan()
    }

    /** What a scan depends on: the session, the connection, who is signed in, and how many times it was asked again. */
    private data class Scan(val session: app.gameport.core.steam.session.SteamSession?, val offline: Boolean, val state: AuthState, val round: Int)

    private companion object {
        const val TAG = "GPLibrary"
        const val STOP_TIMEOUT_MS = 30_000L
        const val FIRST_CHECK_DELAY_MS = 30_000L
        const val CHECK_EVERY_MS = 10L * 60 * 1000
        const val MIN_BETWEEN_CHECKS_MS = 60_000L
    }
}
