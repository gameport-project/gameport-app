package app.gameport.core.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Remembers which Android package belongs to which Steam app, once its install succeeded. */
@Singleton
class InstalledGames @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "installed-games.json")
    private val serializer = MapSerializer(Int.serializer(), String.serializer())

    @Synchronized
    fun all(): Map<Int, String> =
        runCatching { Json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

    @Synchronized
    fun put(appId: Int, packageName: String) = write(all() + (appId to packageName))

    @Synchronized
    fun remove(appId: Int) = write(all() - appId)

    private fun write(map: Map<Int, String>) {
        file.writeText(Json.encodeToString(serializer, map))
    }
}
