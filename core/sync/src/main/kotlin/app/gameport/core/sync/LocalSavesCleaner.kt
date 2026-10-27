package app.gameport.core.sync

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import app.gameport.core.install.InstalledGames
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Puts the saves on this device aside, now, for a test or a clean start. The saves are in the game's own folders, which only the game can reach: the game's
 * process is started briefly for its hook to move them to its backup folder (nothing is removed for good), and goes away again. The game does not appear,
 * the account is not said to play it, and nothing is asked of Steam or removed from it: what Steam holds is brought back by the next sync.
 */
@Singleton
class LocalSavesCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installed: InstalledGames,
    private val playtime: PlaytimeTracker,
    private val coordinator: CloudSyncCoordinator,
) {
    /** What came of it: [moved] files were put aside when [result] is [Result.DONE]. */
    data class Outcome(val result: Result, val moved: Int = 0)

    enum class Result {
        DONE,

        /** The game is on screen: its folders are not touched while it plays. */
        GAME_RUNNING,

        /** The game does not answer (patched before this existed, or not installed), or its folders could not be reached. */
        FAILED,
    }

    suspend fun clear(appId: Int): Outcome = withContext(Dispatchers.IO) {
        val packageName = installed.all()[appId] ?: return@withContext Outcome(Result.FAILED)
        if (playtime.isOnScreen(packageName)) return@withContext Outcome(Result.GAME_RUNNING)
        val rules = coordinator.localRulesOf(appId)
        if (rules.isEmpty()) return@withContext Outcome(Result.FAILED)
        val extras = Bundle().apply {
            putStringArray("rules", rules.map { listOf(it.localDir, it.pattern, if (it.recursive) "1" else "0", it.cloudPrefix).joinToString("\t") }.toTypedArray())
        }
        // As for the other one-off questions to the hook: told first that the process is not started for the game to be played.
        coordinator.requestCatchUp(packageName)
        val client = context.contentResolver.acquireUnstableContentProviderClient(Uri.parse("content://$packageName.gameporthook"))
        if (client == null) {
            coordinator.finishCatchUp(packageName)
            return@withContext Outcome(Result.FAILED)
        }
        try {
            val answer = withTimeoutOrNull(ASK_TIMEOUT_MS) { runCatching { client.call("clear_saves", null, extras) }.onFailure { Log.w(TAG, "no answer", it) }.getOrNull() }
            if (answer?.getString("status") != "OK") return@withContext Outcome(Result.FAILED)
            val moved = answer.getInt("moved", 0)
            coordinator.afterLocalCleared(appId, moved)
            Outcome(Result.DONE, moved)
        } finally {
            client.close()
            coordinator.finishCatchUp(packageName)
        }
    }

    private companion object {
        const val TAG = "GPClearSaves"
        const val ASK_TIMEOUT_MS = 30_000L
    }
}
