package app.gameport.core.steam.session

import android.util.Log
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientStoreUserStats2
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientStoreUserStatsResponse
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "GPAchievements"
private const val STORE_TIMEOUT_MS = 15_000L

/**
 * What adding some achievements to an account would change. Steam keeps the achievements of a game as bits in a few
 * stats, 32 to a stat: [blocks] holds, for each stat that would change, the value it would have. Only bits are added,
 * never taken away, and a stat that would not change is not in it.
 */
internal data class UnlockPlan(
    val blocks: Map<Int, Int>,
    /** The achievements whose bit would be set. */
    val toUnlock: List<String>,
    /** The ones the account already has. */
    val alreadyUnlocked: List<String>,
    /** The ones the game's schema does not know. */
    val unknown: List<String>,
)

/**
 * [positions] says where each achievement is (see [achievementPositions]), [current] holds the value Steam has now for each stat that
 * holds achievements, [names] are the achievements to add.
 */
internal fun planUnlocks(positions: Map<String, Pair<Int, Int>>, current: Map<Int, Int>, names: Collection<String>): UnlockPlan {
    val blocks = linkedMapOf<Int, Int>()
    val toUnlock = mutableListOf<String>()
    val already = mutableListOf<String>()
    val unknown = mutableListOf<String>()
    for (name in names.distinct()) {
        val position = positions[name.lowercase()]
        if (position == null) {
            unknown += name
            continue
        }
        val (statId, bit) = position
        val before = blocks[statId] ?: current[statId] ?: 0
        val after = before or (1 shl bit)
        if (after == before) already += name else {
            blocks[statId] = after
            toUnlock += name
        }
    }
    return UnlockPlan(blocks, toUnlock, already, unknown)
}

/** The answer of Steam to a store of stats. */
internal data class StoreAnswer(val result: EResult, val rejectedStats: List<Int>)

/** Receives the answers to stats stored by [storeStats], matched to the request by its job id. */
internal class StoreStatsResponses : ClientMsgHandler() {
    private val waiting = ConcurrentHashMap<Long, CompletableDeferred<CMsgClientStoreUserStatsResponse>>()

    fun expect(job: Long): CompletableDeferred<CMsgClientStoreUserStatsResponse> = CompletableDeferred<CMsgClientStoreUserStatsResponse>().also { waiting[job] = it }

    fun forget(job: Long) {
        waiting.remove(job)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        if (packetMsg.msgType != EMsg.ClientStoreUserStatsResponse) return
        val response = ClientMsgProtobuf<CMsgClientStoreUserStatsResponse.Builder>(CMsgClientStoreUserStatsResponse::class.java, packetMsg)
        waiting.remove(packetMsg.targetJobID)?.complete(response.body.build())
    }
}

/**
 * Stores the value of some stats of [steamId] for [appId], the way the Steam client does when a game saves its stats: [crc] is the
 * checksum Steam gave with the stats just before, and the stats listed are the ones that change. Null when Steam does not answer.
 */
internal suspend fun SteamSession.storeStats(appId: Int, steamId: Long, crc: Int, stats: Map<Int, Int>): StoreAnswer? {
    val message = ClientMsgProtobuf<CMsgClientStoreUserStats2.Builder>(CMsgClientStoreUserStats2::class.java, EMsg.ClientStoreUserStats2)
    val job = client.getNextJobID()
    message.sourceJobID = job
    message.body.setGameId(appId.toLong()).setSettorSteamId(steamId).setSetteeSteamId(steamId).setCrcStats(crc).setExplicitReset(false)
    stats.forEach { (id, value) -> message.body.addStats(CMsgClientStoreUserStats2.Stats.newBuilder().setStatId(id).setStatValue(value)) }
    val answer = storeStatsResponses.expect(job.value)
    client.send(message)
    val response = withTimeoutOrNull(STORE_TIMEOUT_MS) { answer.await() }
    if (response == null) {
        storeStatsResponses.forget(job.value)
        Log.w(TAG, "app $appId: Steam did not answer the store of ${stats.size} stat(s)")
        return null
    }
    val result = EResult.from(response.eresult) ?: EResult.Fail
    return StoreAnswer(result, response.statsFailedValidationList.map { it.statId })
}

/**
 * When the account unlocked the achievement at [bit] of stat [statId], 0 when it did not. The bit in the stat is what counts: Steam keeps
 * the old time of an achievement that was cleared, so the time alone would show it as unlocked. A bit set without any time is
 * unlocked at an unknown time, given as 1.
 */
internal fun unlockedAt(stats: Map<Int, Int>, times: Map<Int, List<Int>>, statId: Int, bit: Int): Long {
    if (((stats[statId] ?: 0) ushr bit) and 1 == 0) return 0L
    return (times[statId]?.getOrNull(bit)?.toLong() ?: 0L).takeIf { it > 0L } ?: 1L
}
