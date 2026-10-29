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
    /** The channels of the game that hold an Android build, the normal one included (see [ChannelsParser]). */
    val channels: List<CachedChannel> = emptyList(),
)

/** A channel of a game on Steam with an Android build: [updated] is Steam's date for it in seconds, [androidKey] says which build it is (the same text, the same build). */
@Serializable
data class CachedChannel(
    val name: String,
    val description: String? = null,
    val updated: Long = 0,
    val androidBytes: Long = 0,
    val androidKey: String = "",
    val passwordRequired: Boolean = false,
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
    /**
     * The games whose save rules are not what an earlier scan of this device read (GamePort read them differently, or Steam changed them). The sync looks at it once
     * for each game, to stop trusting what it agreed on with Steam under the old rules.
     */
    val rulesChanged: Set<Int> = emptySet(),
)

/** Whether a game's save rules, as read now, are not the ones known before. Nothing known before (a new game, a first scan) is no change. */
object SaveRulesChange {
    fun differ(known: List<CachedSaveRule>?, now: List<CachedSaveRule>): Boolean = known != null && known.toSet() != now.toSet()
}

/** The cache as it was written, whatever its version; null when the text is not a cache. */
internal fun decodeLibraryCache(json: Json, text: String): CachedLibrary? = runCatching { json.decodeFromString<CachedLibrary>(text) }.getOrNull()

interface LibraryCacheStore {
    fun load(accountId: Long): CachedLibrary?

    /** The cache of an earlier version, as it was written: not used to show anything (its shape may be old), only to compare with what the next scan reads. */
    fun loadPrevious(accountId: Long): CachedLibrary? = null

    fun save(accountId: Long, cache: CachedLibrary)

    fun clear()

    /** Marks an app as not inspected yet, so the next scan reads what Steam publishes for it again (a changed game, a changed way to save). */
    fun forgetChecked(accountId: Long, appId: Int) {
        load(accountId)?.let { save(accountId, it.copy(checkedApps = it.checkedApps - appId)) }
    }

    /** The same for several apps at once, with one reading and one writing of the cache. */
    fun forgetChecked(accountId: Long, appIds: Collection<Int>) {
        load(accountId)?.let { save(accountId, it.copy(checkedApps = it.checkedApps - appIds.toSet())) }
    }

    /** Marks every app as not inspected yet: the next scan reads them all again. */
    fun forgetAllChecked(accountId: Long) {
        load(accountId)?.let { save(accountId, it.copy(checkedApps = emptySet())) }
    }

    /** The change number of Steam the library was last brought up to date from (see LibraryChanges), 0 when none was kept. */
    fun changeNumber(accountId: Long): Int = 0

    fun saveChangeNumber(accountId: Long, number: Int) {}
}

/** Version 4 adds the save rules used for the cloud sync, version 6 the build id of each depot, version 8 the artwork names and the parent app, version 10 the Windows copy of the saves, version 11 the DLC apps with an Android depot of their own, and the private data root, version 12 the folder of the saves of a game whose path is written from Android/data, version 13 the order of the two cloud names of such a game, version 14 the channels of a game, version 15 the games whose Android build is only on a test channel (they are read again). */
const val CACHE_VERSION = 15

@Singleton
class FileLibraryCacheStore @Inject constructor(
    @ApplicationContext context: Context,
) : LibraryCacheStore {
    private val directory = File(context.filesDir, "library-cache")
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(accountId: Long): CachedLibrary? = readCache(accountId)?.takeIf { it.version == CACHE_VERSION }

    override fun loadPrevious(accountId: Long): CachedLibrary? = readCache(accountId)?.takeIf { it.version != CACHE_VERSION }

    private fun readCache(accountId: Long): CachedLibrary? = runCatching { file(accountId).readText() }.getOrNull()?.let { decodeLibraryCache(json, it) }

    override fun save(accountId: Long, cache: CachedLibrary) {
        runCatching {
            directory.mkdirs()
            file(accountId).writeText(json.encodeToString(CachedLibrary.serializer(), cache))
        }
    }

    override fun clear() {
        directory.deleteRecursively()
    }

    override fun changeNumber(accountId: Long): Int = runCatching { changesFile(accountId).readText().trim().toInt() }.getOrDefault(0)

    override fun saveChangeNumber(accountId: Long, number: Int) {
        runCatching {
            directory.mkdirs()
            changesFile(accountId).writeText(number.toString())
        }
    }

    private fun file(accountId: Long) = File(directory, "$accountId.json")

    private fun changesFile(accountId: Long) = File(directory, "$accountId.changes")
}
