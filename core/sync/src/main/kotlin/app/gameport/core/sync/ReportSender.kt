package app.gameport.core.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import app.gameport.core.device.DeviceProfile
import app.gameport.core.model.Game
import app.gameport.core.settings.GameVerdicts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What became of the technical report that goes with the answer "it did not work". */
enum class ReportStatus {
    /** The relay has it. */
    SENT,

    /** No connection, or the relay is busy: it is kept on the device and tried again the next time GamePort starts. */
    WAITING,

    /** Sending is off in Settings: nothing was sent. */
    OFF,

    /** The relay will never take it. */
    REFUSED,

    /** There is no report to send: the game is not installed, or the report is too large. */
    UNAVAILABLE,
}

/** The facts that come with a report, never more: see the README of the relay. */
data class ReportMeta(val appId: Int, val appVersion: String, val device: String, val voter: String)

/** Sends one report to the relay. */
fun interface ReportTransport {
    suspend fun post(meta: ReportMeta, zip: ByteArray): Delivery
}

/** The relay of the project (see its README). The reports go to the test route in a debug build, as the votes do. */
class HttpReportTransport(private val address: String) : ReportTransport {
    override suspend fun post(meta: ReportMeta, zip: ByteArray): Delivery = withContext(Dispatchers.IO) {
        fun q(text: String) = URLEncoder.encode(text, "UTF-8")
        val url = "$address?appId=${meta.appId}&app=${q(meta.appVersion)}&device=${q(meta.device)}&voter=${q(meta.voter)}"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(zip.size)
            connection.setRequestProperty("Content-Type", "application/zip")
            connection.outputStream.use { it.write(zip) }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NO_CONTENT -> Delivery.SENT
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
        const val RELAY_REPORT_URL = "https://gameport-relay.gameport.workers.dev/report"
        const val RELAY_TEST_REPORT_URL = "https://gameport-relay.gameport.workers.dev/test/report"
        private const val TIMEOUT_MS = 20_000

        fun addressFor(debuggable: Boolean) = if (debuggable) RELAY_TEST_REPORT_URL else RELAY_REPORT_URL
    }
}

/**
 * The reports that were made and not yet delivered, one file per game in a folder of GamePort's own storage. A report is made when the player
 * answers, so it holds the run that just ended, and it waits here when there is no network. At most [MAX_KEPT] are kept, none older than [MAX_AGE_MS].
 */
class ReportOutbox(private val folder: File, private val now: () -> Long = System::currentTimeMillis) {
    fun put(appId: Int, zip: ByteArray) {
        folder.mkdirs()
        val partial = File(folder, "$appId.zip.part")
        partial.writeBytes(zip)
        // Written under another name and renamed when whole, so a report cut short is never sent.
        check(partial.renameTo(File(folder, "$appId.zip"))) { "The report could not be kept." }
        tidy()
    }

    fun read(appId: Int): ByteArray? = File(folder, "$appId.zip").takeIf { it.isFile }?.readBytes()

    fun remove(appId: Int) {
        File(folder, "$appId.zip").delete()
    }

    fun waiting(): List<Int> = folder.listFiles { file -> file.isFile && file.name.endsWith(".zip") }.orEmpty().mapNotNull { it.name.removeSuffix(".zip").toIntOrNull() }

    private fun tidy() {
        val files = folder.listFiles { file -> file.isFile && file.name.endsWith(".zip") }.orEmpty().sortedByDescending { it.lastModified() }
        files.filterIndexed { index, file -> index >= MAX_KEPT || now() - file.lastModified() > MAX_AGE_MS }.forEach { it.delete() }
    }

    companion object {
        const val MAX_KEPT = 5
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    }
}

/** Sends the report kept for [appId] and forgets it when the relay has taken it or will never take it. A report that is not there is [ReportStatus.UNAVAILABLE]. */
internal suspend fun deliverReport(outbox: ReportOutbox, meta: ReportMeta, transport: ReportTransport): ReportStatus {
    val zip = outbox.read(meta.appId) ?: return ReportStatus.UNAVAILABLE
    return when (transport.post(meta, zip)) {
        Delivery.SENT -> ReportStatus.SENT.also { outbox.remove(meta.appId) }
        Delivery.REFUSED -> ReportStatus.REFUSED.also { outbox.remove(meta.appId) }
        Delivery.LATER -> ReportStatus.WAITING
    }
}

/**
 * Sends the technical report of a game to the project when the player says it did not work, without asking more, and when the player asks for it by
 * hand. Sending is off with the switch of the answers (Settings > Account); the player's own request goes through anyway. A report that finds no
 * network waits on the device and goes the next time GamePort starts.
 */
@Singleton
class ReportSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reporter: ProblemReporter,
    private val verdicts: GameVerdicts,
    private val device: DeviceProfile,
) {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debuggable: Boolean get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    internal var transport: ReportTransport = HttpReportTransport(HttpReportTransport.addressFor(debuggable))
    private val outbox by lazy { ReportOutbox(File(context.filesDir, "report-outbox")) }
    private val appVersion: String by lazy { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    private fun meta(appId: Int) = ReportMeta(appId, appVersion, device.kind, verdicts.voterId)

    /** Sends what waited for a network, when GamePort starts. */
    fun start() {
        scope.launch {
            if (!verdicts.share.value) return@launch
            for (appId in outbox.waiting()) lock.withLock { deliverReport(outbox, meta(appId), transport).also { Log.i(TAG, "$appId: a report that waited: $it") } }
        }
    }

    /** The player said the game did not work: the report is made now, from the run that just ended, and goes at once unless sending is off. */
    suspend fun sendAfterFailure(game: Game): ReportStatus = lock.withLock {
        val zip = reporter.bytes(game)
        if (zip == null) {
            Log.i(TAG, "${game.appId}: no report to send (the game is not installed, or the report is too large)")
            return ReportStatus.UNAVAILABLE
        }
        runCatching { outbox.put(game.appId, zip) }.onFailure {
            Log.w(TAG, "${game.appId}: the report could not be kept", it)
            return ReportStatus.UNAVAILABLE
        }
        if (!verdicts.share.value) {
            Log.i(TAG, "${game.appId}: a ${zip.size}-byte report is kept, sending is off")
            return ReportStatus.OFF
        }
        deliverReport(outbox, meta(game.appId), transport).also { Log.i(TAG, "${game.appId}: a ${zip.size}-byte report: $it") }
    }

    /** The player asked from the game's page: the report is made now and sent, even when the automatic sending is off. */
    suspend fun sendByHand(game: Game, installFailure: String? = null): ReportStatus = lock.withLock {
        val zip = reporter.bytes(game, installFailure = installFailure) ?: return ReportStatus.UNAVAILABLE
        runCatching { outbox.put(game.appId, zip) }.onFailure {
            Log.w(TAG, "${game.appId}: the report could not be kept", it)
            return ReportStatus.UNAVAILABLE
        }
        deliverReport(outbox, meta(game.appId), transport).also { Log.i(TAG, "${game.appId}: the player asked to send a ${zip.size}-byte report: $it") }
    }

    /** The player asked to send the report that was made: it goes even when the automatic sending is off. */
    suspend fun sendNow(appId: Int): ReportStatus = lock.withLock { deliverReport(outbox, meta(appId), transport).also { Log.i(TAG, "$appId: the player asked to send the report: $it") } }

    /** True when a report for this game is kept on the device. */
    fun isKept(appId: Int): Boolean = appId in outbox.waiting()

    private companion object {
        const val TAG = "GPReport"
    }
}
