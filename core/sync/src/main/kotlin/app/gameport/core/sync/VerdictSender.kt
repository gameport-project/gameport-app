package app.gameport.core.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import app.gameport.core.device.DeviceProfile
import app.gameport.core.model.Answer
import app.gameport.core.model.VoteMessage
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

/** What became of a message sent to the relay. */
enum class Delivery {
    /** The relay has it. */
    SENT,

    /** The relay will never take it (it is not what it expects): trying again would only block the answers after it. */
    REFUSED,

    /** No connection, or the relay is busy or down: it is tried again later. */
    LATER,
}

/** Sends one message to the relay. */
fun interface VoteTransport {
    suspend fun post(json: String): Delivery
}

/** The relay of the project (see its README). The address is public and holds nothing personal. */
class HttpVoteTransport(private val address: String = RELAY_VOTE_URL) : VoteTransport {
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
                HttpURLConnection.HTTP_NO_CONTENT -> Delivery.SENT
                // The relay looked at it and said no: a message that is not as it expects, or too big. It would say no again.
                HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> Delivery.REFUSED
                else -> Delivery.LATER
            }
        } catch (e: java.io.IOException) {
            Delivery.LATER
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELAY_VOTE_URL = "https://gameport-relay.gameport.workers.dev/vote"
        const val RELAY_TEST_VOTE_URL = "https://gameport-relay.gameport.workers.dev/test/vote"
        private const val TIMEOUT_MS = 10_000

        /** The real route for a release build, the test route (its own table in the relay) for a debug build. */
        fun addressFor(debuggable: Boolean) = if (debuggable) RELAY_TEST_VOTE_URL else RELAY_VOTE_URL
    }
}

/**
 * Sends the answers the player gave, and keeps those that did not get through for the next time.
 */
@Singleton
class VerdictSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val verdicts: GameVerdicts,
    private val device: DeviceProfile,
) {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debuggable: Boolean get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    // A debug build sends to the test routes of the relay, which keep their own table: test answers never reach the real totals.
    internal var transport: VoteTransport = HttpVoteTransport(HttpVoteTransport.addressFor(debuggable))

    private val appVersion: String by lazy { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    /** Sends the answers when GamePort starts, and each time the player answers or changes the setting. What fails is tried again at the next of those. */
    fun start() {
        scope.launch { combine(verdicts.book, verdicts.share) { book, share -> share && book.unsent().isNotEmpty() }.collect { if (it) flush() } }
    }

    /** Sends what is waiting. Safe to call at any time: one run at a time, and what fails stays for the next run. */
    suspend fun flush() {
        lock.withLock {
            if (!verdicts.share.value) return
            val kind = device.kind
            sendAnswers(
                verdicts.book.value.unsent(),
                { answer -> VoteMessage(answer.appId, answer.verdict, appVersion, kind, verdicts.voterId, offline = answer.offline) },
                transport,
            ) { answer -> verdicts.update { it.sent(answer.appId, answer.verdict) } }
        }
    }

}

/**
 * Sends the answers one after the other and tells each one that got through. At the first one that does not (no connection, the relay is down)
 * it stops: the others stay waiting, in order, for the next time. An answer the relay refuses is given up, so it cannot block the others.
 */
internal suspend fun sendAnswers(
    unsent: List<Answer>,
    message: (Answer) -> VoteMessage,
    transport: VoteTransport,
    onSent: (Answer) -> Unit,
) {
    for (answer in unsent) {
        when (transport.post(message(answer).toJson())) {
            Delivery.SENT, Delivery.REFUSED -> onSent(answer) // done with it either way
            Delivery.LATER -> return
        }
    }
}
