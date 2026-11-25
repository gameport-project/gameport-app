package app.gameport.core.steam

import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** What came of asking Steam to put an app on the wishlist of the account. */
sealed interface WishlistOutcome {
    /** Steam has it on the wishlist now (it also answers this when it already was). */
    data object Added : WishlistOutcome

    /** Steam has taken it off the wishlist (it also answers this when it was not on it). */
    data object Removed : WishlistOutcome

    /** Nobody is signed in to Steam, or the web pages could not be opened for the account. */
    data object NotConnected : WishlistOutcome

    /** Steam did not take it, or did not answer. */
    data object Failed : WishlistOutcome
}

/** The wishlist of the signed-in account: an app is put on it, or taken off it, the way the store's own page does. Nothing is bought. */
interface WishlistRepository {
    suspend fun add(appId: Int): WishlistOutcome

    suspend fun remove(appId: Int): WishlistOutcome

    /** Whether the app is already on the wishlist, or null when Steam could not say (the page then offers to add it, which is harmless). */
    suspend fun contains(appId: Int): Boolean?
}

/** What the store answered to the request to add or remove an app: only a plain "success" counts and gives [done], a page that asks to sign in is a failure. */
internal fun wishlistOutcome(code: Int, body: String, done: WishlistOutcome = WishlistOutcome.Added): WishlistOutcome {
    if (code != 200) return WishlistOutcome.Failed
    val success = runCatching { Json.parseToJsonElement(body).jsonObject["success"]?.jsonPrimitive?.content }.getOrNull()
    return if (success == "true" || success == "1") done else WishlistOutcome.Failed
}

/** The apps a wishlist answer lists (none for an empty wishlist), or null when the answer is not one. */
internal fun wishlistApps(body: String): Set<Int>? = runCatching {
    val response = Json.parseToJsonElement(body).jsonObject["response"] as? JsonObject ?: return@runCatching null
    response["items"]?.jsonArray?.mapNotNull { it.jsonObject["appid"]?.jsonPrimitive?.int }?.toSet() ?: emptySet()
}.getOrNull()

@Singleton
class WebWishlistRepository @Inject constructor(
    private val web: SteamWebSession,
) : WishlistRepository {
    private val client by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }

    override suspend fun add(appId: Int): WishlistOutcome = send("addtowishlist", appId, WishlistOutcome.Added)

    override suspend fun remove(appId: Int): WishlistOutcome = send("removefromwishlist", appId, WishlistOutcome.Removed)

    private suspend fun send(call: String, appId: Int, done: WishlistOutcome): WishlistOutcome {
        val cookies = web.cookies() ?: return WishlistOutcome.NotConnected
        val session = sessionId()
        val request = Request.Builder()
            .url("https://store.steampowered.com/api/$call")
            .header("Cookie", "steamLoginSecure=${cookies.steamLoginSecure}; sessionid=$session")
            .header("Referer", "https://store.steampowered.com/app/$appId")
            .header("Origin", "https://store.steampowered.com")
            .post(FormBody.Builder().add("sessionid", session).add("appid", appId.toString()).build())
            .build()
        return withContext(Dispatchers.IO) {
            runCatching { client.newCall(request).execute().use { wishlistOutcome(it.code, it.body.string(), done) } }.getOrElse { WishlistOutcome.Failed }
        }
    }

    override suspend fun contains(appId: Int): Boolean? {
        val cookies = web.cookies() ?: return null
        // The token goes in the address Steam's own web calls take it in, and is never written to a log.
        val url = "https://api.steampowered.com/IWishlistService/GetWishlist/v1/".toHttpUrl().newBuilder()
            .addQueryParameter("steamid", cookies.steamId.toString())
            .addQueryParameter("access_token", cookies.accessToken)
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { if (it.code == 200) wishlistApps(it.body.string()) else null }
            }.getOrNull()?.contains(appId)
        }
    }

    /** The value of the session cookie: the store only checks that the cookie and the form say the same thing. */
    private fun sessionId(): String = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
}
