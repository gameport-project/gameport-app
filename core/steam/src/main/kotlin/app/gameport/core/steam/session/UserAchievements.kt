package app.gameport.core.steam.session

import app.gameport.core.model.AchievementList
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import `in`.dragonbra.javasteam.types.SteamID
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val INDIVIDUAL_BASE = 76561197960265728L
private const val ANSWER_TIMEOUT_MS = 15_000L
private const val TAG = "GPAchievements"

/**
 * The achievements of [appId] for the signed-in account, texts in [language] (a Steam language name) and in English
 * where the game has none in that language. Read only: it asks what Steam holds and changes nothing there.
 * Null when Steam does not answer or refuses; a game without achievements gives an empty list.
 */
suspend fun SteamSession.achievements(appId: Int, language: String): AchievementList? = withContext(Dispatchers.IO) {
    val stats = client.getHandler(SteamUserStats::class.java) ?: return@withContext null.also { Log.w(TAG, "app $appId: no stats handler") }
    val response = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { stats.getUserStats(appId, SteamID(INDIVIDUAL_BASE + accountId)).await() }
        ?: return@withContext null.also { Log.w(TAG, "app $appId: no answer") }
    if (response.result != EResult.OK) return@withContext null.also { Log.w(TAG, "app $appId: Steam answered ${response.result}") }

    val times = response.achievementBlocks.associate { it.achievementId to it.unlockTime }
    val items = parseAchievements(response.schemaKeyValues, language) { statId, bit -> times[statId]?.getOrNull(bit)?.toLong() ?: 0L }
    Log.i(TAG, "app $appId: ${response.schema.size()} bytes of schema, ${response.achievementBlocks.size} blocks, ${response.stats.size} stats, ${items.size} achievements in $language, ${items.count { it.unlocked }} unlocked")
    AchievementList(appId, language, items)
}
