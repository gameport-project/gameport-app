package app.gameport.core.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import app.gameport.core.install.ExpansionVerdict
import app.gameport.core.install.ExpansionVerdicts
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.InstalledBuilds
import app.gameport.core.install.InstalledGames
import app.gameport.core.install.PackageGateway
import app.gameport.core.model.ExpansionVerification
import app.gameport.core.model.ObbNames
import app.gameport.core.model.SteamConnection
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.session.SteamInspector
import app.gameport.core.steam.session.SteamSessionHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks the games that may be missing their expansion files whether they are. No other app can read a game's `Android/obb` folder, but the game can: its hook lists the
 * sizes of what it holds when GamePort asks (the same way the saves are fetched, by starting the game's process briefly, without the game appearing). The sizes are set
 * against the files Steam lists for the build the game was installed from (see [ExpansionVerification]).
 *
 * A game is asked once per build. One that does not answer (its hook is from before this existed) is asked again only after it was patched or updated, so its process
 * is not started at every launch of GamePort.
 */
@Singleton
class ExpansionVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: SteamAuthRepository,
    private val installer: GameInstallRepository,
    private val installed: InstalledGames,
    private val builds: InstalledBuilds,
    private val verdicts: ExpansionVerdicts,
    private val sessions: SteamSessionHolder,
    private val packages: PackageGateway,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Looks again each time Steam can be reached, and each time the games in doubt change. */
    fun start() {
        scope.launch {
            combine(auth.connection, installer.expansionMayBeMissing) { connection, suspects -> connection to suspects }.collect { (connection, suspects) ->
                if (connection == SteamConnection.ONLINE && suspects.isNotEmpty()) runDue(suspects)
            }
        }
    }

    private suspend fun runDue(suspects: Set<Int>) = lock.withLock {
        for ((index, appId) in suspects.sorted().filter(::isDue).withIndex()) {
            if (auth.connection.value != SteamConnection.ONLINE) return@withLock
            if (index > 0) delay(PAUSE_BETWEEN_GAMES_MS)
            runCatching { verify(appId) }.onFailure { Log.w(TAG, "could not check the expansion files of $appId", it) }
        }
    }

    /** A game is asked when nothing is known of this build, or when it could not answer and has been patched or updated since. */
    private fun isDue(appId: Int): Boolean {
        val packageName = installed.all()[appId] ?: return false
        val said = verdicts.all.value[appId]
        return said == null || said.manifests != builds.get(appId)?.manifests ||
            (said.state == ExpansionVerdict.State.UNKNOWN && said.packageUpdatedAt != packages.lastUpdateTimeOf(packageName))
    }

    private suspend fun verify(appId: Int) {
        val packageName = installed.all()[appId] ?: return
        val build = builds.get(appId) ?: return
        val updatedAt = packages.lastUpdateTimeOf(packageName) ?: 0L
        val held = askGame(packageName)
        if (held == null) {
            verdicts.put(appId, ExpansionVerdict(ExpansionVerdict.State.UNKNOWN, build.manifests, packageUpdatedAt = updatedAt))
            return
        }
        val session = sessions.current.value ?: return
        val expected = mutableListOf<Long>()
        for ((depotId, manifestId) in build.manifests) {
            // A manifest that cannot be read leaves the game unchecked for now, rather than called complete or missing on half of what Steam says.
            val files = SteamInspector.manifestFiles(session, appId, depotId, manifestId) ?: return
            val sizes = files.associate { it.first to it.second }
            ObbNames.selectPlaced(files.map { it.first }, packageName).forEach { (path, _) -> sizes[path]?.let(expected::add) }
        }
        val verdict = when (val result = ExpansionVerification.compare(expected, held)) {
            ExpansionVerification.Result.Complete -> ExpansionVerdict(ExpansionVerdict.State.COMPLETE, build.manifests, expected = expected.size, packageUpdatedAt = updatedAt)
            is ExpansionVerification.Result.Missing -> ExpansionVerdict(ExpansionVerdict.State.MISSING, build.manifests, result.missing, result.expected, updatedAt)
        }
        verdicts.put(appId, verdict)
    }

    /** The sizes of the files the game holds in its folder, or null when it did not answer or could not list it. */
    private suspend fun askGame(packageName: String): List<Long>? = withContext(Dispatchers.IO) {
        val uri = Uri.parse("content://$packageName.gameporthook")
        val client = context.contentResolver.acquireUnstableContentProviderClient(uri) ?: return@withContext null
        try {
            val answer = withTimeoutOrNull(ASK_TIMEOUT_MS) { runCatching { client.call("obb_report", null, null) }.getOrNull() } ?: return@withContext null
            if (answer.getString("status") != "OK") return@withContext null
            answer.getLongArray("sizes")?.toList()
        } finally {
            client.close()
        }
    }

    private companion object {
        const val TAG = "GPExpansion"
        const val PAUSE_BETWEEN_GAMES_MS = 3_000L
        const val ASK_TIMEOUT_MS = 20_000L
    }
}
