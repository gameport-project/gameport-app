package app.gameport.core.steam.session

import app.gameport.core.model.LaunchOption
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import kotlinx.coroutines.future.await

/** The arguments Steam lists for starting [appId], or null when Steam gives nothing for the app. */
suspend fun SteamSession.launchOptions(appId: Int): List<LaunchOption>? {
    val tokens = apps.picsGetAccessTokens(listOf(appId), emptyList()).await().appTokens
    val info = apps.picsGetProductInfo(listOf(PICSRequest(appId, tokens[appId] ?: 0L)), emptyList()).await()
    val app = info.results.flatMap { it.apps.values }.firstOrNull { it.id == appId } ?: return null
    return LaunchOptionsReader.read(app.keyValues["config"]["launch"])
}
