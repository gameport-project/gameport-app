package app.gameport.core.sync

import android.os.SystemClock
import android.util.Log
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.PlaytimeMeter
import app.gameport.core.settings.PlaytimeStore
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.session.SteamSession
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.playtimeMinutes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Follows the time each game is really on screen, from the signs the patched game sends (it came to
 * the screen, it is still there, it left). That time is added to the game's total on this device and,
 * when the player allows it, Steam is told the account is in the game for exactly as long, so its
 * time counts on the account. A device asleep or a game left behind sends nothing: nothing is counted.
 */
@Singleton
class PlaytimeTracker @Inject constructor(
    private val sessions: SteamSessionHolder,
    private val auth: SteamAuthRepository,
    private val installed: InstalledGames,
    private val store: PlaytimeStore,
    private val settings: UserSettings,
) {
    private class Game(val appId: Int, val meter: PlaytimeMeter = PlaytimeMeter()) {
        var steamHeld = false
        var release: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val games = HashMap<String, Game>()
    private var watchdog: Job? = null

    /** The game came to the screen. */
    @Synchronized
    fun resumed(packageName: String) {
        val game = gameOf(packageName) ?: return
        game.release?.cancel()
        game.release = null
        game.meter.resumed(now())
        if (!game.steamHeld && settings.countPlaytimeOnSteam.value) {
            game.steamHeld = true
            scope.launch { withSession { it.holdPlaying(game.appId) } }
        }
        startWatchdog()
    }

    /** The game is still on screen. */
    @Synchronized
    fun alive(packageName: String) {
        val game = gameOf(packageName) ?: return
        val wasRunning = game.meter.running
        store.add(game.appId, game.meter.alive(now()))
        // GamePort restarted while the game ran: it picks the game up again where it is.
        if (!wasRunning) resumed(packageName)
    }

    /** The game left the screen (home button, headset taken off, screen off). A short grace covers a brief interruption. */
    @Synchronized
    fun paused(packageName: String) {
        val game = games[packageName] ?: return
        store.add(game.appId, game.meter.paused(now()))
        scheduleRelease(game)
    }

    /** True while the game is on screen. */
    @Synchronized
    fun isOnScreen(packageName: String): Boolean = games[packageName]?.meter?.running == true

    /** True once the game said it left the screen and has not come back. */
    @Synchronized
    fun isPaused(packageName: String): Boolean = games[packageName]?.let { !it.meter.running } ?: false

    private fun gameOf(packageName: String): Game? {
        games[packageName]?.let { return it }
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return null
        return Game(appId).also { games[packageName] = it }
    }

    private fun scheduleRelease(game: Game) {
        game.release?.cancel()
        game.release = scope.launch {
            delay(RELEASE_GRACE_MS)
            synchronized(this@PlaytimeTracker) { if (game.meter.running) return@launch }
            if (game.steamHeld) {
                game.steamHeld = false
                withSession { it.releasePlaying(game.appId) }
            }
        }
    }

    /** A game that stopped talking without leaving: its stretch ends where it was last seen. */
    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = scope.launch {
            while (true) {
                delay(WATCH_INTERVAL_MS)
                synchronized(this@PlaytimeTracker) {
                    games.values.filter { it.meter.isStale(now()) }.forEach { game ->
                        game.meter.drop()
                        scheduleRelease(game)
                    }
                }
            }
        }
    }

    private suspend fun withSession(block: suspend (SteamSession) -> Unit) {
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() } ?: return
        runCatching { block(session) }.onFailure { Log.w(TAG, "could not tell Steam about the game", it) }
    }

    /** The total time Steam counts for [appId] on the account, in minutes, or null when it cannot be read now. */
    suspend fun steamMinutes(appId: Int): Int? {
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return null
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() } ?: return null
        return runCatching { session.playtimeMinutes(appId) }.getOrNull()
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val TAG = "GPPlaytime"
        const val RELEASE_GRACE_MS = 15_000L
        const val WATCH_INTERVAL_MS = 30_000L
        const val SESSION_WAIT_MS = 10_000L
    }
}
