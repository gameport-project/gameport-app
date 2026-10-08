package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGamesPlayed
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientKickPlayingSession
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.AccountInfoCallback
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import android.util.Log
import `in`.dragonbra.javasteam.util.log.LogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import java.util.EnumSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/** Identity of a signed-in Steam session. */
data class SessionIdentity(val steamId: Long, val displayName: String)

/**
 * Thin wrapper around a JavaSteam client: connection, callback pump, QR authentication and
 * refresh-token log on. It exposes coroutines and knows nothing about Android or UI state.
 */
class SteamSession(private val cellId: Int = 0) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val traffic = ConcurrentHashMap<Int, AtomicLong>()

    /**
     * Bytes received from the network so far for these depots: what a download really fetched, not what it verified
     * on disk. Counted per depot, so two games downloading at once each see their own figure.
     */
    fun receivedBytes(depotIds: Collection<Int>): Long = depotIds.sumOf { traffic[it]?.get() ?: 0L }

    internal val client = SteamClient(
        SteamConfiguration.create {
            it.withProtocolTypes(EnumSet.of(ProtocolTypes.WEB_SOCKET))
            it.withHttpClient(httpClient(traffic))
            // The region downloads are served from, when the player chose one (0: Steam decides).
            if (cellId > 0) it.withCellID(cellId)
        },
    )
    private val callbacks = CallbackManager(client)
    private val user = client.getHandler(SteamUser::class.java)!!

    val apps: SteamApps = client.getHandler(SteamApps::class.java)!!
    val cloud: SteamCloud = client.getHandler(SteamCloud::class.java)!!

    internal val connectTokens = GameConnectTokens().also { client.addHandler(it) }

    /** Steam allows one playing session per account: set while it says another session plays (see [PlayingElsewhere]). */
    val playingElsewhere: kotlinx.coroutines.flow.StateFlow<PlayingElsewhere?> get() = connectTokens.playingElsewhere
    internal val storeStatsResponses = StoreStatsResponses().also { client.addHandler(it) }

    @Volatile internal var loggedOnAtMillis: Long = System.currentTimeMillis()
        private set
    private val connectionCount = java.util.concurrent.atomic.AtomicInteger()

    internal fun nextConnectionCount(): Int = connectionCount.incrementAndGet()

    private val authSequenceCounter = java.util.concurrent.atomic.AtomicInteger()
    internal val authSequence: Int get() = authSequenceCounter.get()
    internal fun nextAuthSequence(): Int = authSequenceCounter.incrementAndGet()

    /** An arbitrary Steam pipe handle, as the ticket list wants one per ticket. */
    internal val pipeHandle: Int = 1 + java.util.Random().nextInt(0x3fffffff)

    @Volatile private var playingApp = 0

    /** When the account was last declared as playing a game, to tell a refusal (Steam drops the connection at once) from an ordinary loss. */
    @Volatile private var playingSentAt = 0L
    @Volatile private var playingJob: kotlinx.coroutines.Job? = null

    /** The game the player is playing right now, held for as long as it is on screen so Steam counts its time. */
    @Volatile private var sessionApp = 0

    private suspend fun sendPlaying(appId: Int) {
        val message = `in`.dragonbra.javasteam.base.ClientMsgProtobuf<CMsgClientGamesPlayed.Builder>(
            CMsgClientGamesPlayed::class.java,
            `in`.dragonbra.javasteam.enums.EMsg.ClientGamesPlayedWithDataBlob,
        )
        if (appId != 0) message.body.addGamesPlayed(CMsgClientGamesPlayed.GamePlayed.newBuilder().setGameId(appId.toLong()))
        // What Steam answers is written down for a few seconds: it is how a refused playing session shows (see [PlayingElsewhere]).
        if (appId != 0) {
            connectTokens.traceFor(TRACE_MS)
            playingSentAt = android.os.SystemClock.elapsedRealtime()
        }
        client.send(message)
        playingApp = appId
    }

    /** Tells Steam the account is in [appId] until [releasePlaying]: the time it runs is counted. */
    suspend fun holdPlaying(appId: Int) {
        sessionApp = appId
        playingJob?.cancel()
        if (playingApp != appId) sendPlaying(appId)
    }

    /** The game left the screen: Steam is told the account is no longer in it. */
    suspend fun releasePlaying(appId: Int) {
        if (sessionApp == appId) sessionApp = 0
        playingJob?.cancel()
        if (playingApp == appId) sendPlaying(0)
    }

    /**
     * Steam only honours a session ticket while the account is in the game it is for, so the account
     * is marked as playing [appId] before a ticket is made. When nothing holds the game (see
     * [holdPlaying]), it is released [PLAYING_TIMEOUT_MS] after the last ticket.
     */
    private suspend fun markPlaying(appId: Int) {
        if (playingApp != appId) {
            sendPlaying(appId)
            // Let Steam register the state before a ticket depends on it.
            kotlinx.coroutines.delay(PLAYING_SETTLE_MS)
        }
        if (sessionApp == appId) return
        playingJob?.cancel()
        playingJob = scope.launch {
            kotlinx.coroutines.delay(PLAYING_TIMEOUT_MS)
            if (sessionApp == 0) sendPlaying(0)
        }
    }

    /** A session ticket for [appId] that its servers can verify against Steam; see [AuthTicket]. Each one is good for a single use. */
    suspend fun authSessionTicket(appId: Int): ByteArray {
        markPlaying(appId)
        // Steam refuses a second playing session: the ticket would not be honoured, and it is for the player to say what to do.
        playingElsewhere.value?.takeIf { !it.kicked }?.let { throw PlayingBlockedException(it) }
        return AuthTicket.build(this, appId)
    }

    /**
     * Asks Steam whether the account may play [appId] here, for at most [waitMs]: declares it, and says what Steam answers. Null when Steam
     * says nothing against it. The playing state is released again.
     */
    suspend fun probePlaying(appId: Int, waitMs: Long): PlayingElsewhere? {
        sendPlaying(appId)
        val answer = kotlinx.coroutines.withTimeoutOrNull(waitMs) { playingElsewhere.first { it != null && !it.kicked } }
        if (answer != null) sendPlaying(0)
        return answer
    }

    /**
     * Takes the playing session from the other device, as Steam's own client does when the player chooses to play here: the game that runs there
     * is stopped, then this one is declared. True when Steam no longer says another session plays.
     */
    suspend fun takePlaying(appId: Int): Boolean {
        val kick = `in`.dragonbra.javasteam.base.ClientMsgProtobuf<CMsgClientKickPlayingSession.Builder>(
            CMsgClientKickPlayingSession::class.java,
            `in`.dragonbra.javasteam.enums.EMsg.ClientKickPlayingSession,
        )
        kick.body.setOnlyStopGame(true)
        connectTokens.traceFor(TRACE_MS)
        client.send(kick)
        // The other session needs a moment to stop its game before this one can be declared.
        kotlinx.coroutines.delay(KICK_SETTLE_MS)
        sendPlaying(appId)
        // Refused again, Steam drops the connection within a moment (see [PlayingElsewhere]); still there after that, the session is ours.
        kotlinx.coroutines.delay(TAKE_WAIT_MS)
        return isAlive && playingElsewhere.value == null
    }

    private val _licenses = MutableStateFlow<List<License>?>(null)

    /** Licenses of the signed-in account (own and family-shared); null until Steam sent them. */
    val licenses: StateFlow<List<License>?> = _licenses.asStateFlow()

    /** Account id (lower 32 bits of the SteamID) of the signed-in user; 0 before log on. */
    @Volatile var accountId: Long = 0L
        private set

    /** Set once Steam dropped the connection: the session is then dead and cannot be used again. */
    @Volatile private var lost = false

    @Volatile private var closedOnPurpose = false

    /**
     * Steam ended this session because the same account logged on somewhere else (another GamePort, another device): that one has the session now,
     * and taking it back at once would only make it take it back in turn.
     */
    @Volatile var wasReplaced = false
        private set

    /** False once the connection to Steam is gone (network change, sleep, Steam closing it): requests would fail at once. */
    val isAlive: Boolean get() = !lost && client.isConnected

    /** Called when Steam drops the connection on its own, not when GamePort closes the session. */
    @Volatile var onLost: (() -> Unit)? = null

    @Volatile private var connected: CompletableDeferred<Unit>? = null
    @Volatile private var loggedOn: CompletableDeferred<LoggedOnCallback>? = null
    @Volatile private var accountName: CompletableDeferred<String>? = null

    init {
        SteamLogging.install()
        callbacks.subscribe(ConnectedCallback::class.java) { connected?.complete(Unit) }
        callbacks.subscribe(DisconnectedCallback::class.java) {
            connected?.completeExceptionally(IllegalStateException("Disconnected from Steam"))
            loggedOn?.completeExceptionally(IllegalStateException("Disconnected from Steam"))
            lost = true
            if (!closedOnPurpose) onLost?.invoke()
        }
        callbacks.subscribe(LoggedOnCallback::class.java) { loggedOn?.complete(it) }
        callbacks.subscribe(LoggedOffCallback::class.java) {
            if (it.result == EResult.LogonSessionReplaced) wasReplaced = true
            // Right after the account was declared as playing, it is Steam saying another session plays: it does not accept a second one.
            val justDeclared = playingApp != 0 && android.os.SystemClock.elapsedRealtime() - playingSentAt < REFUSAL_WINDOW_MS
            android.util.Log.i("GPSteamTicket", "Steam logged this connection off: ${it.result}" + if (justDeclared) " (just after playing was declared)" else "")
            if (justDeclared) connectTokens.refusedPlaying()
        }
        callbacks.subscribe(LicenseListCallback::class.java) { _licenses.value = it.licenseList }
        callbacks.subscribe(AccountInfoCallback::class.java) { accountName?.complete(it.personaName) }
        scope.launch {
            while (isActive) {
                runCatching { callbacks.runWaitCallbacks(PUMP_TIMEOUT_MS) }
            }
        }
    }

    /** A depot downloader bound to this session; the caller closes it. */
    fun newDepotDownloader(): DepotDownloader =
        // More parallel chunks than before: a slow cache server held a slot until it timed out, and four slots
        // left the link idle (measured on device: 2.8 MB/s on a connection above 400 Mbit/s, CPU mostly idle).
        DepotDownloader(client, _licenses.value.orEmpty(), maxDownloads = MAX_PARALLEL_DOWNLOADS, maxDecompress = MAX_PARALLEL_DECOMPRESS)

    suspend fun connect() {
        if (client.isConnected) return
        val deferred = CompletableDeferred<Unit>().also { connected = it }
        client.connect()
        withTimeout(CONNECT_TIMEOUT_MS) { deferred.await() }
    }

    /**
     * Runs the QR flow: [onChallengeUrl] receives the URL to render (and each refreshed one);
     * returns the account name and refresh token once the user approved the sign-in in the Steam mobile app.
     */
    suspend fun authenticateWithQr(deviceName: String, onChallengeUrl: (String) -> Unit): StoredCredentials {
        connect()
        val details = AuthSessionDetails().apply {
            deviceFriendlyName = deviceName
            clientOSType = EOSType.AndroidUnknown
        }
        val session = client.authentication.beginAuthSessionViaQR(details).await() ?: error("Steam returned no QR session")
        session.challengeUrlChanged = IChallengeUrlChanged { changed -> changed?.let { onChallengeUrl(it.challengeUrl) } }
        onChallengeUrl(session.challengeUrl)
        val result = session.pollingWaitForResult().await()
        if (result.refreshToken.isEmpty()) throw AuthenticationException("No refresh token received", EResult.Fail)
        return StoredCredentials(result.accountName, result.refreshToken)
    }

    /**
     * Runs the account name and password flow. Steam Guard is answered through [authenticator]; the password
     * goes to Steam encrypted and is not kept. Returns the same credentials as the QR flow: the refresh token.
     */
    suspend fun authenticateWithCredentials(deviceName: String, username: String, password: String, authenticator: IAuthenticator): StoredCredentials {
        connect()
        val details = AuthSessionDetails().apply {
            this.username = username
            this.password = password
            this.authenticator = authenticator
            persistentSession = true
            deviceFriendlyName = deviceName
            clientOSType = EOSType.AndroidUnknown
        }
        val session = client.authentication.beginAuthSessionViaCredentials(details).await() ?: error("Steam returned no sign-in session")
        val result = session.pollingWaitForResult().await()
        if (result.refreshToken.isEmpty()) throw AuthenticationException("No refresh token received", EResult.Fail)
        return StoredCredentials(result.accountName, result.refreshToken)
    }

    /** Logs on with a refresh token; throws [AuthenticationException] when Steam rejects it. */
    suspend fun logOn(accountName: String, refreshToken: String, deviceName: String): SessionIdentity {
        connect()
        val result = CompletableDeferred<LoggedOnCallback>().also { loggedOn = it }
        val name = CompletableDeferred<String>().also { this.accountName = it }
        user.logOn(
            LogOnDetails(
                username = accountName,
                accessToken = refreshToken,
                shouldRememberPassword = true,
                machineName = deviceName,
                clientOSType = EOSType.AndroidUnknown,
            ).also { details -> if (cellId > 0) details.cellID = cellId },
        )
        val logon = withTimeout(CONNECT_TIMEOUT_MS) { result.await() }
        if (logon.result != EResult.OK) throw AuthenticationException("Log on failed", logon.result)
        loggedOnAtMillis = System.currentTimeMillis()
        val displayName = runCatching { withTimeout(ACCOUNT_INFO_TIMEOUT_MS) { name.await() } }.getOrDefault("")
        val steamId = logon.clientSteamID!!
        accountId = steamId.accountID
        return SessionIdentity(steamId.convertToUInt64(), displayName)
    }

    fun disconnect() {
        closedOnPurpose = true
        if (client.isConnected) client.disconnect()
    }

    private companion object {
        const val PUMP_TIMEOUT_MS = 1_000L
        const val PLAYING_SETTLE_MS = 700L
        const val KICK_SETTLE_MS = 1_500L
        const val REFUSAL_WINDOW_MS = 10_000L
        const val TAKE_WAIT_MS = 3_000L
        const val PLAYING_TIMEOUT_MS = 3 * 60 * 1_000L
        const val MAX_PARALLEL_DOWNLOADS = 12
        const val MAX_PARALLEL_DECOMPRESS = 3
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val ACCOUNT_INFO_TIMEOUT_MS = 5_000L
    }
}

/** Routes JavaSteam's internal logs to logcat (tag "JavaSteam"). */
private object SteamLogging : LogListener {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        LogManager.addListener(this)
    }

    override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
        Log.d(TAG, "${clazz.simpleName}: $message", throwable)
    }

    override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) {
        Log.e(TAG, "${clazz.simpleName}: $message", throwable)
    }

    private const val TAG = "JavaSteam"
}

/**
 * The HTTP client JavaSteam downloads depot chunks with. Over HTTP/2 every chunk of a cache server shares one
 * connection, and a few requests per host are allowed at once: a single slow stream then holds the whole
 * link back. One connection per request, and room for many at once, lets the parallel chunks fill the line.
 */
private fun httpClient(traffic: ConcurrentHashMap<Int, AtomicLong>): OkHttpClient {
    val dispatcher = Dispatcher().apply {
        maxRequests = HTTP_MAX_REQUESTS
        maxRequestsPerHost = HTTP_MAX_REQUESTS_PER_HOST
    }
    return OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@addNetworkInterceptor response
            // Cache servers address a depot as /depot/<id>/...
            val segments = chain.request().url.pathSegments
            val depot = segments.getOrNull(1)?.toIntOrNull()?.takeIf { segments.firstOrNull() == "depot" }
                ?: return@addNetworkInterceptor response
            val counter = traffic.getOrPut(depot) { AtomicLong() }
            val counting = object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also { if (it > 0) counter.addAndGet(it) }
            }
            response.newBuilder().body(counting.buffer().asResponseBody(body.contentType(), body.contentLength())).build()
        }
        .dispatcher(dispatcher)
        .connectionPool(ConnectionPool(HTTP_MAX_REQUESTS, 1, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Keeps the Steam websocket alive while nothing is downloading.
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
}

private const val HTTP_MAX_REQUESTS = 64
private const val HTTP_MAX_REQUESTS_PER_HOST = 16

/** How long after a game is declared as played the messages of Steam are written to the log. */
private const val TRACE_MS = 15_000L
