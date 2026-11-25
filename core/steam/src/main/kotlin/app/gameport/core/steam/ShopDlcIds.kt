package app.gameport.core.steam

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** The numbers of the extra content of [appId] in what the shop answered, in its order; none when the answer says nothing. */
internal fun shopDlcIds(body: String, appId: Int): List<Int> = runCatching {
    val data = Json.parseToJsonElement(body).jsonObject[appId.toString()]?.jsonObject?.get("data")?.jsonObject
    data?.get("dlc")?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }.orEmpty()
}.getOrDefault(emptyList())

/**
 * Every extra content (DLC) Steam's shop lists for a game, the ones with files for Android or not: a game that sells unlocks as DLC counts them one by one, and a game told of none
 * offers to buy what the account already has. Asked once per game for a while, and empty when the shop cannot be reached (the game is then patched with what the library knows).
 */
@Singleton
class ShopDlcIds @Inject constructor() {
    private val client by lazy { OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build() }
    private val kept = ConcurrentHashMap<Int, Pair<Long, List<Int>>>()

    suspend fun of(appId: Int): List<Int> {
        kept[appId]?.takeIf { System.currentTimeMillis() - it.first < KEPT_MS }?.let { return it.second }
        val ids = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("https://store.steampowered.com/api/appdetails?appids=$appId&filters=basic").build()
                client.newCall(request).execute().use { if (it.code == 200) shopDlcIds(it.body.string(), appId) else null }
            }.getOrNull()
        } ?: return emptyList()
        kept[appId] = System.currentTimeMillis() to ids
        return ids
    }

    private companion object {
        const val KEPT_MS = 60L * 60 * 1000
    }
}
