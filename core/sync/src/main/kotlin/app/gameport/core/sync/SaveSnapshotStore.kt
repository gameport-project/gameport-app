package app.gameport.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SnapFile(val rel: String, val size: Long, val time: Long, val sha1: String)

@Serializable
data class SaveSnapshot(
    val localAt: Long = 0,
    val cloudAt: Long = 0,
    val local: List<SnapFile> = emptyList(),
    val cloud: List<SnapFile> = emptyList(),
)

/**
 * The last state of a game's saves that GamePort saw, on each side. GamePort cannot read another
 * app's folders, so the local side is whatever the game reported at its last launch or after it
 * downloaded something.
 */
@Singleton
class SaveSnapshotStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val directory = File(context.filesDir, "save-snapshots")
    private val json = Json { ignoreUnknownKeys = true }
    private val changes = MutableStateFlow(0)

    fun observe(appId: Int): Flow<SaveSnapshot> = changes.map { load(appId) }

    @Synchronized
    fun load(appId: Int): SaveSnapshot = runCatching { json.decodeFromString<SaveSnapshot>(file(appId).readText()) }.getOrDefault(SaveSnapshot())

    @Synchronized
    fun saveLocal(appId: Int, files: List<SnapFile>) = update(appId) { it.copy(localAt = System.currentTimeMillis(), local = files) }

    @Synchronized
    fun saveCloud(appId: Int, files: List<SnapFile>) = update(appId) { it.copy(cloudAt = System.currentTimeMillis(), cloud = files) }

    @Synchronized
    fun clear() {
        directory.deleteRecursively()
        changes.value += 1
    }

    private fun update(appId: Int, change: (SaveSnapshot) -> SaveSnapshot) {
        directory.mkdirs()
        file(appId).writeText(json.encodeToString(SaveSnapshot.serializer(), change(load(appId))))
        changes.value += 1
    }

    private fun file(appId: Int) = File(directory, "$appId.json")
}
