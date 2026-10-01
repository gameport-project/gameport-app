package app.gameport.core.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The build of a game that was installed: Steam's build id of each depot, and the DLC that was chosen. */
@Serializable
data class InstalledBuild(val manifests: Map<Int, Long>, val dlc: Set<Int> = emptySet())

/** Remembers what each installed game was installed from, to tell when Steam publishes a newer build. */
@Singleton
class InstalledBuilds @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "installed-builds.json")
    private val serializer = MapSerializer(Int.serializer(), InstalledBuild.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun all(): Map<Int, InstalledBuild> = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

    @Synchronized
    fun get(appId: Int): InstalledBuild? = all()[appId]

    @Synchronized
    fun put(appId: Int, build: InstalledBuild) = write(all() + (appId to build))

    @Synchronized
    fun remove(appId: Int) = write(all() - appId)

    private fun write(map: Map<Int, InstalledBuild>) {
        file.writeText(json.encodeToString(serializer, map))
    }
}
