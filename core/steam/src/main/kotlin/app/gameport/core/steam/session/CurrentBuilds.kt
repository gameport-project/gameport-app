package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.future.await

/** What Steam publishes today for a game's Android depots: the build id of each. */
data class CurrentBuild(val name: String, val manifests: Map<Int, Long>)

/**
 * Asks Steam for the current build of each of [appIds], on the channel (branch) [channelOf] gives for it: the normal one unless a game is installed from another. An app that has no Android
 * build on its channel is left out, as the apps Steam does not answer for.
 */
suspend fun SteamSession.currentBuilds(appIds: Collection<Int>, channelOf: (Int) -> String = { "public" }): Map<Int, CurrentBuild> {
    val result = HashMap<Int, CurrentBuild>()
    appIds.distinct().chunked(CHUNK).forEach { chunk ->
        val tokens = apps.picsGetAccessTokens(chunk, emptyList()).await().appTokens
        val info = apps.picsGetProductInfo(chunk.map { PICSRequest(it, tokens[it] ?: 0L) }, emptyList()).await()
        for (callback in info.results) {
            for (app in callback.apps.values) {
                val name = app.keyValues["common"]["name"].value ?: continue
                val channel = channelOf(app.id)
                val manifests = app.keyValues["depots"].children
                    .filter { depot ->
                        depot.name?.toIntOrNull() != null &&
                            depot["config"]["oslist"].value.orEmpty().split(',').any { it.trim() == "android" } &&
                            depot["manifests"][channel] != KeyValue.INVALID
                    }
                    .associate { it.name!!.toInt() to it["manifests"][channel]["gid"].asLong() }
                result[app.id] = CurrentBuild(name, manifests)
            }
        }
    }
    return result
}

private const val CHUNK = 100
