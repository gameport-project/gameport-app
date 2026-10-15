package app.gameport.core.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The games whose APK Android accepted but whose expansion files could not be placed: the package that is on the device, by game. Such a game is not installed
 * (it is not offered to play), its download is kept so the files can be placed again, and the player can remove it.
 */
@Singleton
class IncompleteInstalls @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "incomplete-installs.json")
    private val serializer = MapSerializer(Int.serializer(), String.serializer())

    @Synchronized
    fun all(): Map<Int, String> = runCatching { Json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

    @Synchronized
    fun put(appId: Int, packageName: String) = write(all() + (appId to packageName))

    @Synchronized
    fun remove(appId: Int) = write(all() - appId)

    private fun write(map: Map<Int, String>) {
        file.writeText(Json.encodeToString(serializer, map))
    }
}
