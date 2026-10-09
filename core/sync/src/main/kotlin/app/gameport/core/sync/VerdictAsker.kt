package app.gameport.core.sync

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.OfflineEvidence
import app.gameport.core.model.RecentEvents
import app.gameport.core.model.ReturnMode
import app.gameport.core.settings.GameVerdicts
import app.gameport.core.settings.IncompatibleGames
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides, when a game closes, whether to ask the player if it worked. The question waits in [GameVerdicts] until GamePort is on screen. Nothing
 * is asked for a launch the player cancelled, for a process GamePort started only to send saves, or for a game known to be incompatible. Each
 * decision is written to the system log (tag GPVerdict), so that a question that never comes can be explained.
 */
@Singleton
class VerdictAsker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installed: InstalledGames,
    private val verdicts: GameVerdicts,
    private val incompatible: IncompatibleGames,
    private val decisions: PlayDecisions,
    private val coordinator: CloudSyncCoordinator,
    private val auth: SteamAuthRepository,
    private val playtime: PlaytimeTracker,
    private val settings: UserSettings,
) : PlaytimeTracker.SessionListener {
    private val ends = RecentEvents(DEDUP_MS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Listens to the follow-up of the time played: a game killed by the system cannot say it closed, but it stops being seen. Called once when GamePort starts. */
    fun start() {
        playtime.listener = this
        // GamePort can be running, with its screen open, when a game is killed: the games being played are looked at again as long as it runs.
        scope.launch {
            while (true) {
                // Often while a game is being played (its end is then wanted quickly), seldom otherwise.
                delay(if (verdicts.book.value.openRuns.isEmpty()) CHECK_EVERY_MS else CHECK_WHILE_PLAYING_MS)
                checkRuns()
            }
        }
    }

    /** The game gave a sign of life: it is written on disk, so that the end of this run is known even if GamePort is stopped before it sees it. */
    override fun seen(packageName: String, paused: Boolean) {
        runCatching {
            val appId = appIdOf(packageName) ?: return
            verdicts.update { it.seen(appId, System.currentTimeMillis(), paused) }
        }.onFailure { Log.w(TAG, "$packageName: a sign of life could not be noted", it) }
    }

    /**
     * Looks at the games being played that GamePort has not heard from for long enough: they ended (the system killed them, or GamePort was stopped
     * when they did), whether that was a minute ago or yesterday. Called when GamePort starts and each time one of its screens opens.
     */
    fun checkRuns() {
        runCatching {
            val now = System.currentTimeMillis()
            val book = verdicts.book.value
            val stale = book.staleRuns(now)
            if (stale.isNotEmpty()) {
                Log.i(TAG, "games being played: ${book.openRuns.map { (id, run) -> "$id seen ${(now - run.at) / 1000}s ago${if (run.paused) " (left the screen)" else ""}" }}; ended: $stale")
            }
            for (appId in stale) {
                val packageName = installed.all()[appId]
                if (packageName == null) {
                    verdicts.update { it.runEnded(appId) }
                    continue
                }
                Log.i(TAG, "$packageName: no sign of life for long enough, the run ended while GamePort was not looking")
                gameClosed(packageName)
            }
        }.onFailure { Log.w(TAG, "the games being played could not be checked", it) }
    }

    override fun over(packageName: String) {
        Log.i(TAG, "$packageName: the follow-up of the time played says the run is over")
        runCatching { gameClosed(packageName) }.onFailure { Log.w(TAG, "$packageName: the question could not be prepared", it) }
    }

    override fun back(packageName: String) {
        runCatching {
            val appId = appIdOf(packageName) ?: return
            verdicts.update { it.resumed(appId) }
            Log.i(TAG, "$packageName: back on screen")
        }.onFailure { Log.w(TAG, "$packageName: the return to the screen could not be noted", it) }
    }

    /** A run of the game ended: the game said it closed, or the follow-up saw it leave. The same end told by both is dealt with once. */
    fun gameClosed(packageName: String) {
        // This run is no longer followed, whatever is decided next.
        appIdOf(packageName)?.let { id -> verdicts.update { it.runEnded(id) } }
        if (!ends.firstWithin(packageName, System.currentTimeMillis())) {
            Log.i(TAG, "$packageName: this end was already dealt with")
            return
        }
        // The window about another device is told once per run, whatever follows.
        val otherDeviceAsked = decisions.consumeAsked(packageName)
        if (decisions.wasQuitRecently(packageName)) {
            Log.i(TAG, "$packageName: its launch was cancelled, nothing to ask")
            return
        }
        if (coordinator.isCatchUp(packageName)) {
            Log.i(TAG, "$packageName: started only to send saves, nothing to ask")
            return
        }
        val appId = appIdOf(packageName) ?: run {
            Log.i(TAG, "$packageName: not a game GamePort installed")
            return
        }
        if (incompatible.list.game(appId) != null) {
            Log.i(TAG, "$packageName: a game known as incompatible, nothing to ask")
            return
        }
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val offlineRun = OfflineEvidence.qualifies(auth.connection.value, networkAvailable(), otherDeviceAsked)
        // A test build asks every time (it is the only way to try the question again and again); a release build follows the rules of [VerdictBook].
        val testBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        verdicts.update { it.closed(appId, version, LocalDate.now().toString(), offlineRun, always = testBuild) }
        val waits = appId in verdicts.book.value.pending
        Log.i(TAG, "$packageName: the run is over; the question waits: $waits (offline run: $offlineRun)")
        if (waits) bringGamePortBack()
    }

    /**
     * The question can only be seen with GamePort on screen: when the player left a game, GamePort comes back by itself, as the setting
     * "come back to GamePort when a game closes" says. Android may refuse it from the background: the log tells.
     */
    private fun bringGamePortBack() {
        if (settings.returnMode.value == ReturnMode.NEVER) {
            Log.i(TAG, "the player does not want GamePort back by itself: the question waits for the next time it opens")
            return
        }
        runCatching {
            context.startActivity(Intent().setClassName(context.packageName, "app.gameport.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onSuccess { Log.i(TAG, "GamePort asked to come back to the screen") }
            .onFailure { Log.w(TAG, "GamePort could not come back to the screen", it) }
    }

    private fun appIdOf(packageName: String): Int? = installed.all().entries.firstOrNull { it.value == packageName }?.key

    /** The device has a connection that reaches the internet (it does not say that Steam can be reached). */
    private fun networkAvailable(): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return@runCatching false) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        // A device that does not say is a device that is not known to have a network: the answer then says nothing about the offline mode.
    }.onFailure { Log.w(TAG, "the state of the network could not be read", it) }.getOrDefault(false)

    private companion object {
        /** Both sides tell the same end within this time of each other. */
        const val DEDUP_MS = 90_000L
        const val TAG = "GPVerdict"

        /** How often the games being played are looked at while GamePort runs: about the rhythm of their signs of life. */
        const val CHECK_EVERY_MS = 30_000L

        /** How often they are looked at while a game is being played. */
        const val CHECK_WHILE_PLAYING_MS = 5_000L
    }
}
