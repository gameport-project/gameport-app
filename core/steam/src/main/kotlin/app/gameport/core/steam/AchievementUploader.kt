package app.gameport.core.steam

import android.util.Log
import app.gameport.core.model.AuthState
import app.gameport.core.steam.session.SteamSession
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.UnlockPlan
import app.gameport.core.steam.session.achievementPositions
import app.gameport.core.steam.session.planUnlocks
import app.gameport.core.steam.session.storeStats
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import `in`.dragonbra.javasteam.types.SteamID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What came of sending achievements to Steam. */
sealed interface AchievementUpload {
    /** Steam took them: these achievements are now on the account. */
    data class Sent(val names: List<String>) : AchievementUpload

    /** A trial run: [names] would be added, by changing the stats in [stats]; nothing was sent. */
    data class Planned(val names: List<String>, val stats: Map<Int, Int>, val already: List<String>, val unknown: List<String>) : AchievementUpload

    /** Nothing to send: the account has them all already, or the game does not know them. */
    data class NothingToSend(val already: List<String>, val unknown: List<String>) : AchievementUpload

    /** Steam cannot be reached right now (offline mode, no connection or no answer): to try again later. */
    data object Offline : AchievementUpload

    /** Steam answered and refused. Trying again would give the same answer. */
    data class Refused(val reason: String) : AchievementUpload
}

private const val INDIVIDUAL_BASE = 76561197960265728L
private const val ANSWER_TIMEOUT_MS = 15_000L
private const val SESSION_WAIT_MS = 10_000L
private const val TAG = "GPAchievements"

/**
 * Adds achievements the player earned in a game to the Steam account, the way the Steam client does when a game saves its stats:
 * Steam keeps achievements as bits in stats, so the stats as Steam has them now are read, the bits are added and the stats that
 * change are stored with the checksum Steam gave. Only bits are ever added; nothing is taken away or reset. Only what the
 * game itself reported is sent, never the numeric stats the Steamworks shim keeps.
 */
@Singleton
class AchievementUploader @Inject constructor(
    private val auth: SteamAuthRepository,
    private val sessions: SteamSessionHolder,
) {
    /** Sends [names] (the API names the game uses) for [appId]. With [dryRun] it only works out what would change and sends nothing. */
    suspend fun upload(appId: Int, names: Collection<String>, dryRun: Boolean = false): AchievementUpload = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext AchievementUpload.NothingToSend(emptyList(), emptyList())
        // The session is restored first: just after GamePort starts it is not there yet.
        runCatching { auth.restoreSession() }
        if ((auth.authState.value as? AuthState.SignedIn) == null || auth.offline.value) return@withContext AchievementUpload.Offline
        val session: SteamSession = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() }
            ?: return@withContext AchievementUpload.Offline

        val stats = session.client.getHandler(SteamUserStats::class.java) ?: return@withContext AchievementUpload.Offline
        val now = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { stats.getUserStats(appId, SteamID(INDIVIDUAL_BASE + session.accountId)).await() }
            ?: return@withContext AchievementUpload.Offline
        if (now.result != EResult.OK) return@withContext AchievementUpload.Refused("Steam answered ${now.result} when asked for the stats")

        // What the account has now: the value of each stat, whose bits are the achievements. The unlock times are not used, since
        // Steam keeps them after an achievement was cleared.
        val current = now.stats.associate { it.statId to it.statValue }
        Log.i(TAG, "app $appId: Steam holds stats ${now.stats.associate { it.statId to it.statValue }} and unlock times ${now.achievementBlocks.associate { it.achievementId to it.unlockTime.toList() }}")
        val plan: UnlockPlan = planUnlocks(achievementPositions(now.schemaKeyValues), current, names)
        Log.i(TAG, "app $appId: ${plan.toUnlock.size} to add, ${plan.alreadyUnlocked.size} already there, ${plan.unknown.size} unknown; stats ${plan.blocks}")
        if (plan.blocks.isEmpty()) return@withContext AchievementUpload.NothingToSend(plan.alreadyUnlocked, plan.unknown)
        if (dryRun) return@withContext AchievementUpload.Planned(plan.toUnlock, plan.blocks, plan.alreadyUnlocked, plan.unknown)

        val answer = session.storeStats(appId, SteamID(INDIVIDUAL_BASE + session.accountId).convertToUInt64(), now.crcStats, plan.blocks)
            ?: return@withContext AchievementUpload.Offline
        if (answer.result != EResult.OK) {
            return@withContext AchievementUpload.Refused("Steam answered ${answer.result}" + if (answer.rejectedStats.isNotEmpty()) ", stats refused: ${answer.rejectedStats}" else "")
        }
        if (answer.rejectedStats.isNotEmpty()) {
            return@withContext AchievementUpload.Refused("Steam refused the stats ${answer.rejectedStats}")
        }
        Log.i(TAG, "app $appId: Steam took ${plan.toUnlock}")
        AchievementUpload.Sent(plan.toUnlock)
    }
}
