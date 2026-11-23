package app.gameport.core.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import app.gameport.core.device.DeviceProfile
import app.gameport.core.install.InstalledGames
import app.gameport.core.install.PackageGateway
import app.gameport.core.model.DiscordName
import app.gameport.core.model.Game
import app.gameport.core.model.LibraryChoice
import app.gameport.core.settings.GameVerdicts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The two steps of sending the libraries of a game, each followed by a bar of its own. */
enum class LibraryStage { PREPARING, SENDING }

/** Where the sending stands: the step, and how far it went (0 to 1). */
data class LibraryProgress(val stage: LibraryStage, val fraction: Float)

/** What became of the libraries the player chose to send. */
enum class LibraryResult {
    /** The relay has them. */
    SENT,

    /** No connection, or the relay is busy: nothing is kept, the player tries again. */
    LATER,

    /** The relay will not take them. */
    REFUSED,

    /** There is nothing to send: the game is not installed, it holds no library, or they do not fit. */
    UNAVAILABLE,

    /** No Discord name was given: without it nothing is sent. */
    NO_NAME,
}

/** Sends the zip of the libraries to the relay, with the Discord name that must come with it. */
fun interface LibraryTransport {
    suspend fun post(meta: ReportMeta, discord: String, zip: ByteArray, onProgress: (Long) -> Unit): Delivery
}

/** The relay's route for the libraries (see its README): the test route in a debug build, as for the reports. */
class HttpLibraryTransport(private val address: String) : LibraryTransport {
    override suspend fun post(meta: ReportMeta, discord: String, zip: ByteArray, onProgress: (Long) -> Unit): Delivery = withContext(Dispatchers.IO) {
        fun q(text: String) = urlEncode(text)
        postZip("$address?appId=${meta.appId}&app=${q(meta.appVersion)}&device=${q(meta.device)}&voter=${q(meta.voter)}&discord=${q(discord)}${meta.answer?.let { "&answer=${q(it)}" }.orEmpty()}", zip, READ_TIMEOUT_MS, onProgress)
    }

    companion object {
        const val RELAY_LIBRARIES_URL = "https://gameport-relay.gameport.workers.dev/libraries"
        const val RELAY_TEST_LIBRARIES_URL = "https://gameport-relay.gameport.workers.dev/test/libraries"

        /** The relay stores a large set in parts before it answers: it is given more time than a report. */
        private const val READ_TIMEOUT_MS = 90_000

        fun addressFor(debuggable: Boolean) = if (debuggable) RELAY_TEST_LIBRARIES_URL else RELAY_LIBRARIES_URL
    }
}

/**
 * Sends the native libraries of an installed game, with the usual report, for a player who chose to help find why a game does not work. It is a button of the game's settings and a choice
 * made for one game at a time: the player sees which files go and how big they are before anything is sent, and a Discord name is required, so the project can talk to them about it.
 * Which libraries go is decided by [LibraryChoice]; the report part is the one [ProblemReporter] makes for every report, redacted the same way.
 */
@Singleton
class LibrarySender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reporter: ProblemReporter,
    private val verdicts: GameVerdicts,
    private val device: DeviceProfile,
    private val packages: PackageGateway,
    private val installed: InstalledGames,
) {
    private val debuggable: Boolean get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    internal var transport: LibraryTransport = HttpLibraryTransport(HttpLibraryTransport.addressFor(debuggable))
    private val appVersion: String by lazy { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    /** A library of one of the game's APK files: where it is, and what it is. */
    private class Found(val apk: File, val library: LibraryChoice.Library)

    private fun scan(packageName: String): List<Found> = packages.apkFilesOf(packageName).flatMap { apk ->
        runCatching {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("lib/") && it.name.endsWith(".so") }
                    .map { Found(apk, LibraryChoice.Library(it.name, it.size, if (it.compressedSize >= 0) it.compressedSize else it.size)) }.toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun packageOf(game: Game): String? = installed.all()[game.appId]?.takeIf(packages::isInstalled)

    /** The libraries that would go and the ones that would not, for the window that asks the player first. Null when the game is not installed. */
    suspend fun plan(game: Game): LibraryChoice.Pick? = withContext(Dispatchers.IO) {
        val packageName = packageOf(game) ?: return@withContext null
        LibraryChoice.pick(LibraryChoice.ofDeviceAbi(scan(packageName).map { it.library }))
    }

    /**
     * Makes the report with the libraries and sends it. [onProgress] follows both steps. The zip is made again with fewer libraries, the biggest first to go, when it comes out larger than the
     * relay takes. The name is kept for the next time, as with a report.
     */
    suspend fun send(
        game: Game,
        typedName: String,
        description: String? = null,
        symptoms: Set<app.gameport.core.model.Symptom> = emptySet(),
        trigger: String = "libraries",
        answer: String? = null,
        onProgress: (LibraryProgress) -> Unit,
    ): LibraryResult = withContext(Dispatchers.IO) {
        val name = DiscordName.clean(typedName)
        if (name.isEmpty()) return@withContext LibraryResult.NO_NAME
        val packageName = packageOf(game) ?: return@withContext LibraryResult.UNAVAILABLE
        verdicts.setDiscordName(name)
        val found = scan(packageName)
        var pick: LibraryChoice.Pick? = LibraryChoice.pick(LibraryChoice.ofDeviceAbi(found.map { it.library }))
        onProgress(LibraryProgress(LibraryStage.PREPARING, 0f))
        var zip: ByteArray? = null
        while (zip == null) {
            val current = pick ?: return@withContext LibraryResult.UNAVAILABLE.also { Log.w(TAG, "${game.appId}: the libraries do not fit") }
            zip = reporter.bytesWith(game, LibraryChoice.MAX_ZIP_BYTES, trigger, description, symptoms) { out -> writeLibraries(out, current, found, onProgress) }
            if (zip == null) pick = LibraryChoice.withoutBiggest(current)
        }
        onProgress(LibraryProgress(LibraryStage.PREPARING, 1f))
        val meta = ReportMeta(game.appId, appVersion, device.kind, verdicts.voterId, answer)
        val delivery = transport.post(meta, name, zip) { sent -> onProgress(LibraryProgress(LibraryStage.SENDING, sent.toFloat() / zip.size)) }
        Log.i(TAG, "${game.appId}: ${zip.size} bytes of libraries and report: $delivery")
        when (delivery) {
            Delivery.SENT -> LibraryResult.SENT
            Delivery.REFUSED -> LibraryResult.REFUSED
            Delivery.LATER -> LibraryResult.LATER
        }
    }

    /** The libraries go in a folder of their own, and a list of them (with their SHA-256, so the same file is known again) says what is in it and what was left out. */
    private fun writeLibraries(zip: ZipOutputStream, pick: LibraryChoice.Pick, found: List<Found>, onProgress: (LibraryProgress) -> Unit) {
        val listing = StringBuilder("The libraries of the game that the player chose to send, in the libraries/ folder (path in the APK, size, SHA-256):\n")
        pick.included.forEachIndexed { index, library ->
            val source = found.first { it.library.path == library.path }
            val digest = MessageDigest.getInstance("SHA-256")
            zip.putNextEntry(ZipEntry("libraries/" + library.path.removePrefix("lib/")))
            ZipFile(source.apk).use { apk ->
                apk.getInputStream(apk.getEntry(library.path)).use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        zip.write(buffer, 0, n)
                    }
                }
            }
            zip.closeEntry()
            listing.appendLine("  ${library.path}  ${library.size}  ${digest.digest().joinToString("") { "%02x".format(it) }}")
            onProgress(LibraryProgress(LibraryStage.PREPARING, (index + 1f) / pick.included.size))
        }
        if (pick.included.isEmpty()) listing.appendLine("  (none)")
        listing.appendLine("\nLeft out (too large for what the relay takes), path in the APK and size:")
        pick.left.forEach { listing.appendLine("  ${it.path}  ${it.size}") }
        if (pick.left.isEmpty()) listing.appendLine("  (none)")
        zip.putNextEntry(ZipEntry("libraries.txt"))
        zip.write(listing.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private companion object {
        const val TAG = "GPLibraries"
        const val BUFFER_BYTES = 64 * 1024
    }
}
