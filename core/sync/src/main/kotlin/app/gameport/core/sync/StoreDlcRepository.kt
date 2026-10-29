package app.gameport.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One extra content (DLC) of a game, as Steam's shop describes it: its name, its picture, and whether it is free or what it costs. */
@Serializable
data class StoreDlc(val appId: Int, val name: String, val image: String? = null, val free: Boolean = false, val price: String? = null)

/** What Steam's shop answers about a game and about its extra content, read into what the page shows. */
internal object StoreDlcParsing {
    private val json = Json { ignoreUnknownKeys = true }

    /** The numbers of the extra content of the game [appId], in the order Steam lists them; none when the answer says nothing. */
    fun ids(body: String, appId: Int): List<Int> =
        dataOf(body, appId)?.get("dlc")?.let { runCatching { it.jsonArray.mapNotNull { id -> id.jsonPrimitive.intOrNull } }.getOrNull() }.orEmpty()

    /** The extra content [id], or null when the shop does not describe it (it is not sold any more, or not in this country). */
    fun dlc(body: String, id: Int): StoreDlc? {
        val data = dataOf(body, id) ?: return null
        val name = data["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val free = data["is_free"]?.jsonPrimitive?.booleanOrNull == true
        val price = runCatching { data["price_overview"]?.jsonObject?.get("final_formatted")?.jsonPrimitive?.contentOrNull }.getOrNull()
        return StoreDlc(id, name, data["header_image"]?.jsonPrimitive?.contentOrNull, free, price)
    }

    // The shop answers {"<id>": {"success": true, "data": {...}}}; the data is an empty list when it has nothing.
    private fun dataOf(body: String, id: Int): JsonObject? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val entry = runCatching { root[id.toString()]?.jsonObject }.getOrNull() ?: return null
        if (entry["success"]?.jsonPrimitive?.booleanOrNull != true) return null
        return entry["data"] as? JsonObject
    }
}

/** The name Steam gives a language, for the shop's `l` parameter, from a code of the catalog; English when it has none. */
fun steamLanguageName(code: String): String = when (code) {
    "ar" -> "arabic"; "bg" -> "bulgarian"; "zh-CN" -> "schinese"; "zh-TW" -> "tchinese"; "cs" -> "czech"; "da" -> "danish"; "nl" -> "dutch"
    "fi" -> "finnish"; "fr" -> "french"; "de" -> "german"; "el" -> "greek"; "hu" -> "hungarian"; "id" -> "indonesian"; "it" -> "italian"
    "ja" -> "japanese"; "ko" -> "koreana"; "no" -> "norwegian"; "pl" -> "polish"; "pt" -> "portuguese"; "pt-BR" -> "brazilian"; "ro" -> "romanian"
    "ru" -> "russian"; "es-419" -> "latam"; "es" -> "spanish"; "sv" -> "swedish"; "th" -> "thai"; "tr" -> "turkish"; "uk" -> "ukrainian"; "vi" -> "vietnamese"
    else -> "english"
}

/**
 * The extra content of a game, as Steam's shop lists it. It is asked for when a page of the shop of GamePort opens (one question for the list, then one for each extra content, a few at
 * a time), and the answer is kept on the device for a day (a few minutes when some of the questions failed, so the missing ones are asked again soon). A device that cannot reach the
 * shop shows what it kept, however old.
 */
@Singleton
class StoreDlcRepository internal constructor(
    private val folder: File,
    private val get: suspend (String) -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "store-dlc").also { it.mkdirs() }, ::httpGet)

    @Serializable
    private class Kept(val at: Long, val complete: Boolean, val dlc: List<StoreDlc>)

    private val json = Json { ignoreUnknownKeys = true }

    /** The extra content of [appId] with prices for [country] and words in [language] (a code of the catalog). Empty when the game has none, or when nothing can be had. */
    suspend fun of(appId: Int, country: String, language: String): List<StoreDlc> {
        val name = steamLanguageName(language)
        val file = File(folder, "$appId-$name-$country.json")
        val kept = read(file)
        if (kept != null && now() - kept.at < (if (kept.complete) FRESH_MS else PARTIAL_MS)) return kept.dlc
        val answer = ask(appId, country, name) ?: return kept?.dlc.orEmpty()
        runCatching { file.writeText(json.encodeToString(Kept.serializer(), answer)) }
        return answer.dlc
    }

    private fun read(file: File): Kept? = runCatching { json.decodeFromString(Kept.serializer(), file.readText()) }.getOrNull()

    private suspend fun ask(appId: Int, country: String, language: String): Kept? {
        val listBody = get("$BASE?appids=$appId&filters=basic&l=$language") ?: return null
        val ids = StoreDlcParsing.ids(listBody, appId).take(MAX_LISTED)
        if (ids.isEmpty()) return Kept(now(), true, emptyList())
        val slots = Semaphore(AT_A_TIME)
        val answers = coroutineScope {
            ids.map { id ->
                async { slots.withPermit { get("$BASE?appids=$id&filters=basic,price_overview&cc=$country&l=$language") } }
            }.awaitAll()
        }
        // A question with no answer (the network failed) is asked again soon; one the shop answers "no data" to is an extra content it does not describe, and is left out.
        val complete = answers.none { it == null }
        val dlc = ids.zip(answers).mapNotNull { (id, body) -> body?.let { StoreDlcParsing.dlc(it, id) } }
        if (dlc.isEmpty() && !complete) return null
        return Kept(now(), complete, dlc)
    }

    private companion object {
        const val BASE = "https://store.steampowered.com/api/appdetails"
        const val FRESH_MS = 24L * 60 * 60 * 1000
        const val PARTIAL_MS = 10L * 60 * 1000
        const val AT_A_TIME = 4
        const val MAX_LISTED = 120
    }
}

private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: java.io.IOException) {
        null
    } finally {
        connection.disconnect()
    }
}
