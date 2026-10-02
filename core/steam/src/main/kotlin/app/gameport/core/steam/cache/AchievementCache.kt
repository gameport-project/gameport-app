package app.gameport.core.steam.cache

import android.content.Context
import app.gameport.core.model.Achievement
import app.gameport.core.model.AchievementList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CachedAchievement(
    val name: String,
    val title: String,
    val description: String,
    val icon: String? = null,
    val iconGray: String? = null,
    val hidden: Boolean = false,
    val unlocked: Boolean = false,
    val unlockedAt: Long = 0L,
)

@Serializable
private data class CachedAchievements(
    val version: Int = VERSION,
    val steamId: Long,
    val appId: Int,
    val language: String,
    val items: List<CachedAchievement>,
)

private const val VERSION = 1

/**
 * The last achievements Steam gave for each game, so the page still shows them without a connection. They belong to an
 * account: a list kept for another account is not shown, and signing out removes them all.
 */
@Singleton
class AchievementCache @Inject constructor(@ApplicationContext context: Context) {
    private val folder = File(context.filesDir, "achievements")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(appId: Int, steamId: Long): AchievementList? = runCatching {
        val file = File(folder, "$appId.json").takeIf { it.isFile } ?: return null
        val cached = json.decodeFromString<CachedAchievements>(file.readText())
        if (cached.version != VERSION || cached.steamId != steamId) return null
        AchievementList(
            cached.appId,
            cached.language,
            cached.items.map { Achievement(it.name, it.title, it.description, it.icon, it.iconGray, it.hidden, it.unlocked, it.unlockedAt) },
        )
    }.getOrNull()

    fun write(list: AchievementList, steamId: Long) {
        runCatching {
            folder.mkdirs()
            val items = list.items.map { CachedAchievement(it.name, it.title, it.description, it.icon, it.iconGray, it.hidden, it.unlocked, it.unlockedAt) }
            File(folder, "${list.appId}.json").writeText(json.encodeToString(CachedAchievements.serializer(), CachedAchievements(VERSION, steamId, list.appId, list.language, items)))
        }
    }

    fun clear() {
        folder.deleteRecursively()
    }
}
