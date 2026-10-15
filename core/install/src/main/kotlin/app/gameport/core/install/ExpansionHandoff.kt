package app.gameport.core.install

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The expansion files of a game, kept by GamePort until the game places them itself.
 *
 * Some devices do not let GamePort write the folder of expansion files of another app (`Android/obb/<package>`), whatever access it is given. The game itself may write
 * its own folder. So the files wait here, under the name the game looks for, and the game (through the hook GamePort put in it) copies them from GamePort into its own
 * folder: see [deliver]. The files are served to that game, and only to it, by the provider of GamePort while a delivery is asked.
 */
internal class HandoffStaging(private val root: File) {
    private fun folder(packageName: String): File? = if (SAFE_PACKAGE.matches(packageName)) File(root, packageName) else null

    /** Moves the expansion files of [downloaded] here, then names them for the version code [code] (see [ExpansionFiles.align]). Returns the names kept. */
    fun stage(downloaded: File, packageName: String, skipped: String, code: Long?, problems: MutableList<String>): List<String> {
        val target = folder(packageName) ?: return emptyList()
        val placed = ExpansionFiles.place(downloaded, target, packageName, skipped, problems)
        if (code != null) ExpansionFiles.align(target, packageName, code)
        return placed
    }

    /** The files that wait for [packageName]'s game, by name. */
    fun staged(packageName: String): List<File> =
        folder(packageName)?.listFiles { file -> file.isFile && !file.name.startsWith(".") }?.sortedBy { it.name }.orEmpty()

    /** One staged file by its name, or null: a name is never a path. */
    fun file(packageName: String, name: String): File? {
        if (name.isEmpty() || name.startsWith(".") || '/' in name || '\\' in name) return null
        return folder(packageName)?.let { File(it, name) }?.takeIf { it.isFile }
    }

    fun clear(packageName: String) {
        folder(packageName)?.deleteRecursively()
    }

    private companion object {
        val SAFE_PACKAGE = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")
    }
}

@Singleton
class ExpansionHandoff @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val staging by lazy { HandoffStaging(context.getExternalFilesDir("obb-handoff") ?: File(context.filesDir, "obb-handoff")) }
    private val requests = ConcurrentHashMap<String, Request>()
    private val lastFailure = ConcurrentHashMap<String, String>()

    private class Request {
        val finished = CompletableDeferred<Boolean>()

        @Volatile var lastActivity: Long = SystemClock.elapsedRealtime()

        @Volatile var done: Long = 0

        @Volatile var total: Long = 0
    }

    internal fun stage(downloaded: File, packageName: String, skipped: String, code: Long?, problems: MutableList<String>): List<String> =
        staging.stage(downloaded, packageName, skipped, code, problems)

    fun hasStaged(packageName: String): Boolean = staging.staged(packageName).isNotEmpty()

    fun staged(packageName: String): List<File> = staging.staged(packageName)

    fun clear(packageName: String) {
        requests.remove(packageName)
        staging.clear(packageName)
    }

    /** What went wrong the last time the game was asked to place its files, for the event log. */
    fun failureOf(packageName: String): String? = lastFailure[packageName]

    /** True while the game of [packageName] is being asked to place its files: only then does GamePort serve them. */
    fun isRequested(packageName: String): Boolean = requests.containsKey(packageName)

    /** The file the game asks for, only while a delivery is asked for that game. */
    fun fileForGame(packageName: String, name: String): File? = if (isRequested(packageName)) staging.file(packageName, name) else null

    /** The game says how far it is: the delivery is alive. */
    fun reportProgress(packageName: String, done: Long, total: Long) {
        requests[packageName]?.let {
            it.done = done
            it.total = total
            it.lastActivity = SystemClock.elapsedRealtime()
        }
    }

    /** The game has placed its files, or says why it could not. */
    fun reportDone(packageName: String, ok: Boolean, reason: String?) {
        if (!ok) lastFailure[packageName] = reason ?: "no reason given"
        requests[packageName]?.finished?.complete(ok)
    }

    /**
     * Asks the game of [packageName] to place the staged files in its own folder, and waits for it. The provider of the game's hook starts the game's process (and for
     * nothing else: no screen opens); the hook copies the files from here. True when they are all in place. A delivery that shows no sign of life for a while fails.
     */
    suspend fun deliver(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasStaged(packageName)) return@withContext true
        lastFailure.remove(packageName)
        val request = Request().also { requests[packageName] = it }
        // Held open while the files travel, so the system does not stop the game's process meanwhile; unstable, so that GamePort is not stopped with it.
        val uri = Uri.parse("content://$packageName.gameporthook")
        val client = context.contentResolver.acquireUnstableContentProviderClient(uri)
        try {
            val answer = runCatching { client?.call("obb_pull", null, null) }.onFailure { lastFailure[packageName] = "the game did not answer: $it" }.getOrNull()
            if (answer?.getString("status") != "STARTED") {
                lastFailure.putIfAbsent(packageName, "the game was patched by an older GamePort and cannot place its own files")
                return@withContext false
            }
            while (!request.finished.isCompleted) {
                delay(POLL_MS)
                if (SystemClock.elapsedRealtime() - request.lastActivity > STALL_MS) {
                    lastFailure[packageName] = "the game stopped answering"
                    return@withContext false
                }
            }
            request.finished.await()
        } finally {
            client?.close()
            requests.remove(packageName)
        }
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val STALL_MS = 120_000L
    }
}
