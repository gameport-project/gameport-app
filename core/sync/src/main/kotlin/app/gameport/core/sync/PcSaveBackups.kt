package app.gameport.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One file of a clean copy of the Windows saves: its cloud name, its place in the copy, and how Steam held it. */
@Serializable
data class PcBackupFile(val cloudName: String, val stored: String, val sha1: String, val size: Long, val timestamp: Long)

/** A clean copy of what the Windows space of Steam Cloud held for a game, made before this device was allowed to write there. */
@Serializable
data class PcBackup(val stamp: String, val createdAt: Long, val files: List<PcBackupFile>)

/**
 * The clean copies of the Windows saves. They are never changed or pruned by GamePort: the player's PC saves are not GamePort's to lose, and
 * these are what puts them back if this device's saves damage them. Kept apart from the ordinary cloud backups, which are removed over time.
 */
@Singleton
class PcSaveBackups @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root = File(context.filesDir, "pc-originals")
    private val json = Json { ignoreUnknownKeys = true }
    private val changes = MutableStateFlow(0)

    fun observeLatest(appId: Int): Flow<PcBackup?> = changes.map { latest(appId) }

    @Synchronized
    fun latest(appId: Int): PcBackup? = File(root, appId.toString()).listFiles { f -> f.isDirectory }
        ?.sortedByDescending { it.name }
        ?.firstNotNullOfOrNull { dir -> runCatching { json.decodeFromString<PcBackup>(File(dir, MANIFEST).readText()) }.getOrNull() }

    /** The folder holding the files of [backup]. */
    fun directoryOf(appId: Int, backup: PcBackup) = File(File(root, appId.toString()), backup.stamp)

    /** A new, empty folder for a copy: the files are put in it, then [commit] makes it count. */
    @Synchronized
    fun begin(appId: Int): Pair<String, File> {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return stamp to File(File(root, appId.toString()), stamp).also { it.mkdirs() }
    }

    /** A copy only counts once its list is written: one that was cut short is never used to restore. */
    @Synchronized
    fun commit(appId: Int, backup: PcBackup) {
        File(directoryOf(appId, backup), MANIFEST).writeText(json.encodeToString(PcBackup.serializer(), backup))
        changes.value += 1
    }

    @Synchronized
    fun discard(dir: File) {
        dir.deleteRecursively()
    }

    private companion object {
        const val MANIFEST = "manifest.json"
    }
}
