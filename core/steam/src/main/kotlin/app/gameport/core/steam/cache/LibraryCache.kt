package app.gameport.core.steam.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CachedDepot(
    val id: Int,
    val dlcAppId: Int?,
    val installBytes: Long,
    val downloadBytes: Long,
    val manifestId: Long = 0,
)

@Serializable
data class CachedSaveRule(
    val localDir: String,
    val pattern: String,
    val recursive: Boolean,
    val cloudPrefix: String,
)

@Serializable
data class CachedGame(
    val appId: Int,
    val name: String,
    val isVr: Boolean,
    val depots: List<CachedDepot>,
    val saveRules: List<CachedSaveRule> = emptyList(),
)

/** What a previous scan learned, so the next launch only asks Steam about what is new. */
@Serializable
data class CachedLibrary(
    /** Bumped when the cached content changes shape; an older cache is ignored and rebuilt. */
    val version: Int = 0,
    val savedAt: Long,
    /** Package id -> app ids it grants, so known packages need no Steam round trip. */
    val packageApps: Map<Int, List<Int>>,
    /** Apps already inspected (Android or not). */
    val checkedApps: Set<Int>,
    val games: List<CachedGame>,
    /** Names of the DLC apps the games' depots belong to. */
    val dlcNames: Map<Int, String> = emptyMap(),
)

interface LibraryCacheStore {
    fun load(accountId: Long): CachedLibrary?

    fun save(accountId: Long, cache: CachedLibrary)

    fun clear()
}

/** Version 4 adds the save rules used for the cloud sync, version 6 the build id of each depot. */
const val CACHE_VERSION = 6

@Singleton
class FileLibraryCacheStore @Inject constructor(
    @ApplicationContext context: Context,
) : LibraryCacheStore {
    private val directory = File(context.filesDir, "library-cache")
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(accountId: Long): CachedLibrary? = runCatching {
        json.decodeFromString<CachedLibrary>(file(accountId).readText())
    }.getOrNull()?.takeIf { it.version == CACHE_VERSION }

    override fun save(accountId: Long, cache: CachedLibrary) {
        runCatching {
            directory.mkdirs()
            file(accountId).writeText(json.encodeToString(CachedLibrary.serializer(), cache))
        }
    }

    override fun clear() {
        directory.deleteRecursively()
    }

    private fun file(accountId: Long) = File(directory, "$accountId.json")
}
