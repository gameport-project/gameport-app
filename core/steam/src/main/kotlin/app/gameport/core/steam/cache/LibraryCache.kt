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
    /** How Steam names the Windows copy of the same saves, when the studio keeps one apart from the Android one. */
    val pcCloudPrefix: String? = null,
)

@Serializable
data class CachedGame(
    val appId: Int,
    val name: String,
    val isVr: Boolean,
    /** Steam files the game as adult content. */
    val adult: Boolean = false,
    val depots: List<CachedDepot>,
    val saveRules: List<CachedSaveRule> = emptyList(),
    /** `game`, `demo` or `beta`: the app type Steam gives it. */
    val kind: String = "game",
    /** The artwork file names Steam publishes for the app (`<hash>/library_capsule.jpg`); null when it publishes none. */
    val capsule: String? = null,
    val hero: String? = null,
    val header: String? = null,
    /** The full game a demo or playtest belongs to. */
    val parent: Int? = null,
)

/**
 * A DLC app that carries an Android depot of its own. A game whose content is sold as DLC (Steam says `hasdepotsindlc`) declares no depot for it: each DLC app lists
 * its own, tagged Android, with its own manifest. [parent] is the game it belongs to.
 */
@Serializable
data class CachedDlcApp(
    val appId: Int,
    val parent: Int,
    val name: String,
    val depots: List<CachedDepot>,
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
    /** The DLC apps the account has that carry an Android depot of their own. */
    val dlcApps: List<CachedDlcApp> = emptyList(),
)

interface LibraryCacheStore {
    fun load(accountId: Long): CachedLibrary?

    fun save(accountId: Long, cache: CachedLibrary)

    fun clear()

    /** Marks an app as not inspected yet, so the next scan reads what Steam publishes for it again (a changed game, a changed way to save). */
    fun forgetChecked(accountId: Long, appId: Int) {
        load(accountId)?.let { save(accountId, it.copy(checkedApps = it.checkedApps - appId)) }
    }
}

/** Version 4 adds the save rules used for the cloud sync, version 6 the build id of each depot, version 8 the artwork names and the parent app, version 10 the Windows copy of the saves, version 11 the DLC apps with an Android depot of their own, and the private data root. */
const val CACHE_VERSION = 11

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
