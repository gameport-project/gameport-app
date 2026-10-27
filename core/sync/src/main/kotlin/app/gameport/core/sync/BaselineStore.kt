package app.gameport.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class StoredBaseline(val changeNumber: Long, val files: Map<String, String>)

/** Remembers, per account and game, what both sides held after the last successful sync. */
@Singleton
class BaselineStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val directory = File(context.filesDir, "cloud-sync")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(accountId: Long, appId: Int): Baseline? = runCatching {
        val stored = json.decodeFromString<StoredBaseline>(file(accountId, appId).readText())
        Baseline(stored.changeNumber, stored.files)
    }.getOrNull()

    @Synchronized
    fun save(accountId: Long, appId: Int, baseline: Baseline) {
        directory.mkdirs()
        file(accountId, appId).writeText(json.encodeToString(StoredBaseline.serializer(), StoredBaseline(baseline.changeNumber, baseline.files)))
    }

    /**
     * Forgets what the device and the Windows saves of a game agreed on, whoever the account. The Windows saves are kept under the app id
     * made negative, apart from the Android ones: what was agreed with one says nothing about the other, and would read as files the cloud deleted.
     */
    @Synchronized
    fun forgetPc(appId: Int) {
        directory.listFiles { file -> file.name.endsWith("_-$appId.json") }?.forEach { it.delete() }
    }

    /** Forgets what the device and the Android saves of a game agreed on, whoever the account (see [forgetPc] for the PC saves). */
    @Synchronized
    fun forgetAndroid(appId: Int) {
        directory.listFiles { file -> file.name.endsWith("_$appId.json") }?.forEach { it.delete() }
    }

    @Synchronized
    fun clear() {
        directory.deleteRecursively()
    }

    private fun file(accountId: Long, appId: Int) = File(directory, "${accountId}_$appId.json")
}
