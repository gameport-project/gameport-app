package app.gameport.core.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import app.gameport.core.device.DeviceProfile
import app.gameport.core.model.CommunityMapping
import app.gameport.core.model.CommunityMappings
import app.gameport.core.model.ControllerFeedbackMessage
import app.gameport.core.model.PendingControllerAnswer
import app.gameport.core.settings.ControllerFeedbackStore
import app.gameport.core.settings.GameVerdicts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The relay of the project for the controllers' feedback (see its README). The addresses are public and hold nothing personal. */
object ControllerFeedbackRoutes {
    private const val RELAY = "https://gameport-relay.gameport.workers.dev"

    /** The real route for a release build, the test route (its own tables in the relay) for a debug build. */
    fun sendAddress(debuggable: Boolean) = if (debuggable) "$RELAY/test/controllers" else "$RELAY/controllers"

    fun readAddress(appId: Int, device: String, debuggable: Boolean): String =
        "${sendAddress(debuggable)}/$appId?device=${java.net.URLEncoder.encode(device, "UTF-8")}"
}

/** Sends one answer to the relay: it replies 200 once it has it, 400 when it will never take it. */
class HttpControllerFeedbackTransport(private val address: String) : VoteTransport {
    override suspend fun post(json: String): Delivery = withContext(Dispatchers.IO) {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(json.toByteArray()) }
            when (connection.responseCode) {
                in 200..299 -> Delivery.SENT
                // The relay looked at it and said no: it would say no again.
                HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> Delivery.REFUSED
                else -> Delivery.LATER
            }
        } catch (e: java.io.IOException) {
            Delivery.LATER
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

/** Reads the mappings the players say work for one game on one kind of device: the text of the relay's answer, or null when it did not answer clearly. */
fun interface ControllerFeedbackReader {
    suspend fun read(appId: Int, device: String): String?
}

class HttpControllerFeedbackReader(private val debuggable: Boolean) : ControllerFeedbackReader {
    override suspend fun read(appId: Int, device: String): String? = withContext(Dispatchers.IO) {
        val connection = URL(ControllerFeedbackRoutes.readAddress(appId, device, debuggable)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readNBytes(MAX_BYTES).toString(Charsets.UTF_8) } else null
        } catch (e: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_BYTES = 64 * 1024
    }
}

/**
 * Sends what the player says about the controls of a game, keeping what does not get through for the next time (as the answers about the games are), and reads what the other
 * players say works. Nothing personal goes: the random number of the player's votes, the kind of device and the version of GamePort, as for a vote.
 */
@Singleton
class ControllerFeedbackSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ControllerFeedbackStore,
    private val verdicts: GameVerdicts,
    private val device: DeviceProfile,
) {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debuggable: Boolean get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    internal var reader: ControllerFeedbackReader = HttpControllerFeedbackReader(debuggable)
    internal var transport: VoteTransport = HttpControllerFeedbackTransport(ControllerFeedbackRoutes.sendAddress(debuggable))
    private val appVersion: String by lazy { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    /** Sends the answers when GamePort starts, and each time the player answers or changes the setting. What fails is tried again at the next of those. */
    fun start() {
        scope.launch { combine(store.book, verdicts.share) { book, share -> share && book.unsent().isNotEmpty() }.collect { if (it) flush() } }
    }

    /** Sends what is waiting. Safe to call at any time: one run at a time, and what fails stays for the next run. */
    suspend fun flush() {
        lock.withLock {
            if (!verdicts.share.value) return
            val kind = device.kind
            sendControllerAnswers(
                store.book.value.unsent(),
                { pending -> ControllerFeedbackMessage(pending.appId, kind, verdicts.voterId, appVersion, pending.answer, pending.mapping, pending.preset.ifEmpty { null }) },
                transport,
            ) { pending -> store.update { it.sent(pending.appId, pending.mapping, pending.answer) } }
        }
    }

    private val kept = HashMap<Int, Pair<Long, List<CommunityMapping>>>()

    /** The mappings the players say work for [appId] on this kind of device: kept for a few minutes, empty (never an error) when the relay cannot be reached. */
    suspend fun communityMappings(appId: Int): List<CommunityMapping> {
        val known = synchronized(kept) { kept[appId] }
        if (known != null && System.currentTimeMillis() - known.first < KEEP_MS) return known.second
        val text = runCatching { reader.read(appId, device.kind) }.getOrNull() ?: return known?.second.orEmpty()
        val read = CommunityMappings.parse(text)
        synchronized(kept) { kept[appId] = System.currentTimeMillis() to read }
        return read
    }

    private companion object {
        const val KEEP_MS = 5 * 60_000L
    }
}

/**
 * Sends the answers one after the other and tells each one that got through. At the first one that does not (no connection, the relay is down) it stops: the others stay
 * waiting for the next time. An answer the relay refuses is given up, so it cannot block the others.
 */
internal suspend fun sendControllerAnswers(
    unsent: List<PendingControllerAnswer>,
    message: (PendingControllerAnswer) -> ControllerFeedbackMessage,
    transport: VoteTransport,
    onSent: (PendingControllerAnswer) -> Unit,
) {
    for (pending in unsent) {
        when (transport.post(message(pending).toJson())) {
            Delivery.SENT, Delivery.REFUSED -> onSent(pending)
            Delivery.LATER -> return
        }
    }
}
