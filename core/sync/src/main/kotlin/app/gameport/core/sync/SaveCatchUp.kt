package app.gameport.core.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.SteamConnection
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The games whose saves still have to be sent: the ones whose last sync could not be made (no connection, or it failed), installed and
 * not on screen. Pure, so it is easy to check.
 */
internal fun gamesToCatchUp(statuses: Map<Int, SyncStatus>, installed: Map<Int, String>, onScreen: (String) -> Boolean): List<String> =
    statuses.filterValues { it != SyncStatus.OK }.keys.sorted()
        .mapNotNull { installed[it] }
        .filterNot(onScreen)

/**
 * Sends the saves of a game played without a connection once Steam can be reached again, without waiting for the game to be started:
 * a player who plays offline, then goes to another device, would otherwise find the cloud behind. The saves are in the game's own
 * folder, which only the game can read, so the game's process is started briefly for its hook to send them (and for nothing else: the
 * account is not said to play it, no time is counted, and the game does not appear). The hook uses the sending that exists, which
 * never downloads and leaves a conflict for the player to decide when the game is next started.
 *
 * Only games with a sync that did not go through are touched, one at a time, so nothing happens when all is up to date.
 */
@Singleton
class SaveCatchUp @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: SteamAuthRepository,
    private val syncStatus: SyncStatusStore,
    private val installed: InstalledGames,
    private val playtime: PlaytimeTracker,
    private val coordinator: CloudSyncCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Looks each time the connection to Steam comes (back). */
    fun start() {
        scope.launch {
            auth.connection.collect { connection -> if (connection == SteamConnection.ONLINE) runDue() }
        }
    }

    /**
     * A game has just closed: what it could not send (its saves differ from the cloud's and the send did not go through) is sent from here.
     * Waits a little, the game's process is still ending.
     */
    fun afterClose() {
        scope.launch {
            delay(AFTER_CLOSE_DELAY_MS)
            if (auth.connection.value == SteamConnection.ONLINE) runDue()
        }
    }

    private suspend fun runDue() = lock.withLock {
        val due = gamesToCatchUp(syncStatus.statuses.value, installed.all(), playtime::isOnScreen)
        for ((index, packageName) in due.withIndex()) {
            // The connection may have gone again while the previous one was sent.
            if (auth.connection.value != SteamConnection.ONLINE) return@withLock
            if (index > 0) delay(PAUSE_BETWEEN_GAMES_MS)
            sendSaves(packageName)
        }
    }

    private fun sendSaves(packageName: String) {
        coordinator.requestCatchUp(packageName)
        try {
            // The provider of the game's hook; it starts the game's process. A game patched before this existed refuses, and is left to its next start.
            val answer = context.contentResolver.call(Uri.parse("content://$packageName.gameporthook"), "catchup", null, null)
            Log.i(TAG, "$packageName: saves sent after the connection came back -> ${answer?.getString("status")}")
        } catch (e: Exception) {
            Log.i(TAG, "$packageName: could not send the saves now, they wait for its next start: $e")
        } finally {
            coordinator.finishCatchUp(packageName)
        }
    }

    private companion object {
        const val TAG = "GPCatchUp"
        const val PAUSE_BETWEEN_GAMES_MS = 3_000L
        const val AFTER_CLOSE_DELAY_MS = 6_000L
    }
}
