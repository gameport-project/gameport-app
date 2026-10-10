package app.gameport.core.steam

import android.content.Context
import android.util.Log
import app.gameport.core.steam.session.SteamSession
import app.gameport.core.steam.session.SteamSessionHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Asks Steam for the names of apps by their ids: null when Steam could not be asked (no session, no answer), the names it gave otherwise. */
fun interface AppNameLookup {
    suspend fun names(appIds: List<Int>): Map<Int, String>?
}

/** Reads the names from the product information Steam publishes for every public app: no licence is needed to read it. */
internal class SteamAppNameLookup(private val sessions: SteamSessionHolder) : AppNameLookup {
    override suspend fun names(appIds: List<Int>): Map<Int, String>? {
        val session: SteamSession = sessions.current.value ?: return null
        val found = HashMap<Int, String>()
        for (chunk in appIds.chunked(CHUNK)) {
            val answer = withTimeoutOrNull(TIMEOUT_MS) {
                val tokens = session.apps.picsGetAccessTokens(chunk, emptyList()).await().appTokens
                session.apps.picsGetProductInfo(chunk.map { PICSRequest(it, tokens[it] ?: 0L) }, emptyList()).await()
            } ?: return null
            for (callback in answer.results) {
                for (app in callback.apps.values) {
                    app.keyValues["common"]["name"].value?.let { found[app.id] = it }
                }
            }
        }
        return found
    }

    private companion object {
        const val CHUNK = 200
        const val TIMEOUT_MS = 15_000L
    }
}

/**
 * The names of games the account does not have, which only the compatibility table needs: the library knows the ones it owns. A name found is kept on
 * this device for good (a game's name does not change in a way that matters here), so it is asked once and shows without a connection afterwards.
 */
@Singleton
class AppNameRepository internal constructor(private val file: File, private val lookup: AppNameLookup) {
    @Inject
    constructor(@ApplicationContext context: Context, sessions: SteamSessionHolder) : this(File(context.cacheDir, "app-names.json"), SteamAppNameLookup(sessions))

    private val lock = Mutex()
    private val tried = HashSet<Int>()
    private val kept = MutableStateFlow(read())

    /** Every name found so far, by app id. */
    val names: StateFlow<Map<Int, String>> = kept.asStateFlow()

    /** Asks Steam for the names of [appIds] that are not known yet. An app Steam did not name is not asked about again until GamePort restarts. */
    suspend fun resolve(appIds: Collection<Int>) {
        lock.withLock {
            val missing = appIds.filter { it !in kept.value && it !in tried }.distinct()
            if (missing.isEmpty()) return
            val found = runCatching { lookup.names(missing) }.onFailure { Log.w(TAG, "could not read names", it) }.getOrNull() ?: return
            // Steam answered: what it did not name is not asked again. When it could not be asked, the apps are tried again later.
            tried += missing
            if (found.isNotEmpty()) {
                kept.value = kept.value + found
                runCatching { file.writeText(json.encodeToString(serializer, kept.value.mapKeys { it.key.toString() })) }
            }
        }
    }

    private fun read(): Map<Int, String> =
        runCatching { json.decodeFromString(serializer, file.readText()).mapNotNull { (key, value) -> key.toIntOrNull()?.let { it to value } }.toMap() }.getOrDefault(emptyMap())

    private companion object {
        const val TAG = "GPNames"
        val json = Json { ignoreUnknownKeys = true }
        val serializer = MapSerializer(String.serializer(), String.serializer())
    }
}
