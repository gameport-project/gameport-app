package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPlayerSteamclient.CPlayer_GetOwnedGames_Request
import `in`.dragonbra.javasteam.rpc.service.Player
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext

/** SteamID64 of an individual account in the public universe is this plus its account id. */
private const val INDIVIDUAL_ID_BASE = 76561197960265728L

/**
 * The total time the signed-in account has played [appId], in minutes, as Steam counts it, or null
 * when Steam does not answer or does not list the game.
 */
suspend fun SteamSession.playtimeMinutes(appId: Int): Int? = withContext(Dispatchers.IO) {
    val service = client.getHandler(SteamUnifiedMessages::class.java)!!.createService<Player>()
    val request = CPlayer_GetOwnedGames_Request.newBuilder()
        .setSteamid(INDIVIDUAL_ID_BASE + accountId)
        .setIncludePlayedFreeGames(true)
        .setIncludeFreeSub(true)
        .addAppidsFilter(appId)
        .build()
    val response = runCatching { service.getOwnedGames(request).await() }.getOrNull() ?: return@withContext null
    if (response.result != EResult.OK) return@withContext null
    response.body.gamesList.firstOrNull { it.appid == appId }?.playtimeForever
}
