package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGamesPlayed
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.enums.EPersonaState
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
class SteamSession {
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
        },
    )
    private val callbacks = CallbackManager(client)
    private val user = client.getHandler(SteamUser::class.java)!!

    val apps: SteamApps = client.getHandler(SteamApps::class.java)!!
    val cloud: SteamCloud = client.getHandler(SteamCloud::class.java)!!

    internal val connectTokens = GameConnectTokens().also { client.addHandler(it) }

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
    @Volatile private var playingJob: kotlinx.coroutines.Job? = null

    /** The game the player is playing right now, held for as long as it is on screen so Steam counts its time. */
    @Volatile private var sessionApp = 0

    private suspend fun sendPlaying(appId: Int) {
        val message = `in`.dragonbra.javasteam.base.ClientMsgProtobuf<CMsgClientGamesPlayed.Builder>(
            CMsgClientGamesPlayed::class.java,
            `in`.dragonbra.javasteam.enums.EMsg.ClientGamesPlayedWithDataBlob,
        )
        if (appId != 0) message.body.addGamesPlayed(CMsgClientGamesPlayed.GamePlayed.newBuilder().setGameId(appId.toLong()))
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
        return AuthTicket.build(this, appId)
    }

    private val _licenses = MutableStateFlow<List<License>?>(null)

    /** Licenses of the signed-in account (own and family-shared); null until Steam sent them. */
    val licenses: StateFlow<List<License>?> = _licenses.asStateFlow()

    /** Account id (lower 32 bits of the SteamID) of the signed-in user; 0 before log on. */
    @Volatile var accountId: Long = 0L
        private set

    @Volatile private var connected: CompletableDeferred<Unit>? = null
    @Volatile private var loggedOn: CompletableDeferred<LoggedOnCallback>? = null
    @Volatile private var accountName: CompletableDeferred<String>? = null

    init {
        SteamLogging.install()
        callbacks.subscribe(ConnectedCallback::class.java) { connected?.complete(Unit) }
        callbacks.subscribe(DisconnectedCallback::class.java) {
            connected?.completeExceptionally(IllegalStateException("Disconnected from Steam"))
            loggedOn?.completeExceptionally(IllegalStateException("Disconnected from Steam"))
        }
        callbacks.subscribe(LoggedOnCallback::class.java) { loggedOn?.complete(it) }
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
            ),
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
        if (client.isConnected) client.disconnect()
    }

    private companion object {
        const val PUMP_TIMEOUT_MS = 1_000L
        const val PLAYING_SETTLE_MS = 700L
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
