package app.gameport.core.steam.cache

import android.content.Context
import app.gameport.core.model.LaunchOption
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CachedLaunchOption(val arguments: String, val description: String? = null, val vr: Boolean = false, val android: Boolean = false)

@Serializable
private data class CachedLaunchOptions(val version: Int = VERSION, val items: List<CachedLaunchOption>)

private const val VERSION = 3

/** The arguments Steam last listed for each game, so a game can be started with them without a connection. What the list is for no account in particular. */
@Singleton
class LaunchOptionsStore @Inject constructor(@ApplicationContext context: Context) {
    private val folder = File(context.filesDir, "launch-options")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The list kept for [appId], empty when Steam listed nothing, or null when it was never asked. */
    fun read(appId: Int): List<LaunchOption>? = runCatching {
        val file = File(folder, "$appId.json").takeIf { it.isFile } ?: return null
        val cached = json.decodeFromString<CachedLaunchOptions>(file.readText())
        if (cached.version != VERSION) return null
        cached.items.map { LaunchOption(it.arguments, it.description, it.vr, it.android) }
    }.getOrNull()

    fun write(appId: Int, options: List<LaunchOption>) {
        runCatching {
            folder.mkdirs()
            val cached = CachedLaunchOptions(VERSION, options.map { CachedLaunchOption(it.arguments, it.description, it.vr, it.android) })
            File(folder, "$appId.json").writeText(json.encodeToString(CachedLaunchOptions.serializer(), cached))
        }
    }
}
