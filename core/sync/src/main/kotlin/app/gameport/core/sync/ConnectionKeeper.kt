package app.gameport.core.sync

import android.os.SystemClock
import android.util.Log
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.UpdateGate
import app.gameport.core.steam.SteamAuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True when GamePort is quit: no screen of it is open and no game runs, and that has lasted [graceMs]. Pure, so it is easy to check. */
internal fun isQuit(inUse: Boolean, quitForMs: Long, graceMs: Long): Boolean = !inUse && quitForMs >= graceMs

/** True when the connection to Steam can be closed: GamePort is quit and nothing it started is still being patched, installed or synced. */
internal fun shouldRelease(quit: Boolean, working: Boolean): Boolean = quit && !working

/**
 * Quitting GamePort quits it: Android keeps the process of a closed app for a while, and with it the downloads and the connection to Steam,
 * which another GamePort on the device (the released one next to a test build) would then fight for. So once no screen of GamePort is open
 * (a few seconds, so a dialog of the system does not count as leaving) and no game runs, the downloads stop, keeping their files, and the
 * connection to Steam is closed. The only thing that keeps GamePort connected is a game being played, which needs its ticket, its saves and
 * its play time. Everything starts again by itself: when GamePort is opened, or when a game asks for the session.
 */
@Singleton
class ConnectionKeeper @Inject constructor(
    private val auth: SteamAuthRepository,
    private val playtime: PlaytimeTracker,
    private val gate: UpdateGate,
    private val installer: GameInstallRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var screens = 0
    @Volatile private var inUseAt = SystemClock.elapsedRealtime()
    private var started = false
    private var lastWhy = ""

    /**
     * A window of GamePort was opened. On a headset, looking at another window or the home does not close it: it stays, hidden, until the
     * player closes it, and what it started goes on. Only closing the last one is quitting.
     */
    fun screenOpened() {
        screens++
        inUseAt = SystemClock.elapsedRealtime()
        if (screens == 1) scope.launch {
            // Opened again: connected at once, even if another GamePort had taken the session a moment ago, and the downloads go on.
            runCatching { auth.resume() }
            installer.resumeInterruptedDownloads()
        }
    }

    /** A window of GamePort was closed. */
    fun screenClosed() {
        screens = (screens - 1).coerceAtLeast(0)
        if (screens == 0) inUseAt = SystemClock.elapsedRealtime()
    }

    /** A window of GamePort is shown again (it was hidden behind another): what was left half done goes on. */
    fun screenShown() {
        inUseAt = SystemClock.elapsedRealtime()
        installer.resumeInterruptedDownloads()
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                delay(CHECK_MS)
                val now = SystemClock.elapsedRealtime()
                val inUse = screens > 0 || playtime.hasActiveGame()
                if (inUse) inUseAt = now
                val why = if (inUse) "screens=$screens, game=${playtime.hasActiveGame()}" else "nothing"
                if (why != lastWhy) {
                    lastWhy = why
                    Log.i(TAG, "GamePort is kept by: $why")
                }
                if (!isQuit(inUse, now - inUseAt, GRACE_MS)) continue
                val stopped = installer.stopDownloadsForExit()
                if (stopped > 0) Log.i(TAG, "GamePort is quit: $stopped download(s) stopped, they go on when it is opened again")
                // Asked at every look: a short task that opened the connection again (a game's ticket, a send) is closed behind it.
                if (shouldRelease(quit = true, working = gate.currentBlocker() != null)) runCatching { auth.release() }
            }
        }
    }

    private companion object {
        const val TAG = "GPConnection"
        const val CHECK_MS = 4_000L
        const val GRACE_MS = 5_000L
    }
}
