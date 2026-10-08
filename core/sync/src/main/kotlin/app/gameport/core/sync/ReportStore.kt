package app.gameport.core.sync

import android.content.Context
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import java.util.zip.GZIPInputStream
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why GamePort thinks a game had a problem. */
enum class Suspicion { SHORT_SESSION, CRASH }

/**
 * What the patched games hand to GamePort for problem reports (the last lines of their log, how the run before
 * ended), and the sign that a game just had a problem: it closed within seconds of starting, or it crashed.
 * The game cannot be read from outside, so it sends this itself.
 */
@Singleton
class ReportStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory = File(context.filesDir, "problem-reports")
    private val prefs = context.getSharedPreferences("gameport_problems", Context.MODE_PRIVATE)
    private val launched = HashMap<String, Long>()
    private val _suspected = MutableStateFlow(readSuspicions())

    /** Games that look like they had a problem, by package, until the player acts on it. */
    val suspected: StateFlow<Map<String, Suspicion>> = _suspected.asStateFlow()

    /** What the patched game handed over: see `SessionLog` in the hook. Text comes gzip-compressed to fit in one call. */
    fun saveGameData(packageName: String, extras: Bundle) {
        val folder = File(directory, safe(packageName)).apply { mkdirs() }
        // A new run of the game: what the one before left is kept apart, as it is the run that explains how it ended and would be written over.
        RunArchive.noteRun(folder, extras.getInt("pid", 0), mapOf(LOG_START to PREVIOUS_LOG_START, HOOK_LOG to PREVIOUS_HOOK_LOG))
        extras.getByteArray("headGz")?.let { gunzip(it)?.let { text -> File(folder, LOG_START).writeBytes(text) } }
        extras.getByteArray("logGz")?.let { gunzip(it)?.let { text -> File(folder, HOOK_LOG).writeBytes(text) } }
        extras.getByteArray("performanceGz")?.let { gunzip(it)?.let { text -> File(folder, PERFORMANCE).writeBytes(text) } }
        extras.getByteArray("engineGz")?.let { gunzip(it)?.let { text -> File(folder, ENGINE_LOG).writeBytes(text) } }
        extras.getString("libraries")?.takeIf { it.isNotEmpty() }?.let { File(folder, LIBRARIES).writeText(it.takeLast(MAX_CHARS)) }
        extras.getString("launch")?.takeIf { it.isNotEmpty() }?.let { File(folder, LAUNCH).writeText(it.takeLast(MAX_CHARS)) }
        extras.getByteArray("traceGz")?.let { gz ->
            gunzip(gz, MAX_TRACE_BYTES)?.let { trace ->
                File(folder, TOMBSTONE).writeBytes(trace)
                File(folder, TOMBSTONE_INFO).writeText("kind=${extras.getString("traceKind")} time=${extras.getLong("traceTime")} bytes=${trace.size}\n")
            }
        }
        val previousExit = extras.getString("previousExit")
        if (!previousExit.isNullOrEmpty()) {
            val file = File(folder, PREVIOUS_EXIT)
            val changed = !file.exists() || file.readText() != previousExit
            file.writeText(previousExit.takeLast(MAX_CHARS))
            if (changed && ExitReasons.isCrash(previousExit.lineSequence().firstOrNull().orEmpty())) suspect(packageName, Suspicion.CRASH)
        }
    }

    /** Deletes what the games handed over more than [cutoffMillis] ago, and the folders of games no longer installed. */
    fun purge(cutoffMillis: Long, installedPackages: Set<String>) {
        directory.listFiles()?.forEach { folder ->
            if (folder.name !in installedPackages) {
                folder.deleteRecursively()
                return@forEach
            }
            folder.listFiles()?.forEach { file -> if (file.lastModified() < cutoffMillis) file.delete() }
            if (folder.list().isNullOrEmpty()) folder.delete()
        }
    }

    /** Forgets everything kept for a game that was uninstalled. */
    fun forget(packageName: String) {
        File(directory, safe(packageName)).deleteRecursively()
        prefs.edit().remove(packageName).remove(SESSION_PREFIX + packageName).apply()
        _suspected.value = _suspected.value - packageName
        synchronized(this) { launched.remove(packageName) }
    }

    /** A stored file as text, or empty when the game has not sent it. */
    fun text(packageName: String, name: String): String =
        runCatching { File(File(directory, safe(packageName)), name).readText() }.getOrDefault("")

    fun bytes(packageName: String, name: String): ByteArray? =
        runCatching { File(File(directory, safe(packageName)), name).readBytes() }.getOrNull()

    fun hookLog(packageName: String): String = text(packageName, HOOK_LOG)

    fun previousExit(packageName: String): String = text(packageName, PREVIOUS_EXIT)

    /** The start of the log of the run before the last one, or empty when there is none. */
    fun previousLogStart(packageName: String): String = text(packageName, PREVIOUS_LOG_START)

    fun previousHookLog(packageName: String): String = text(packageName, PREVIOUS_HOOK_LOG)

    /** When the game last handed something over, in milliseconds, or null: a report says how old what it holds is. */
    fun lastDataMillis(packageName: String): Long? =
        File(directory, safe(packageName)).listFiles()?.filter { it.isFile }?.maxOfOrNull { it.lastModified() }

    private fun gunzip(bytes: ByteArray, limit: Int = MAX_CHARS * 4): ByteArray? = runCatching {
        GZIPInputStream(bytes.inputStream()).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (out.size() < limit) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()

    /** The game just started. */
    @Synchronized
    fun launched(packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        launched[packageName] = nowMillis
    }

    /** The game left the screen or closed: closing within seconds of the start is a sign of trouble. */
    @Synchronized
    fun left(packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        val start = launched.remove(packageName) ?: return
        prefs.edit().putLong(SESSION_PREFIX + packageName, nowMillis - start).apply()
        if (nowMillis - start < SHORT_SESSION_MS) suspect(packageName, Suspicion.SHORT_SESSION)
    }

    /** How long the game's last run lasted before it left the screen or closed, in milliseconds, or null if unknown. */
    fun lastSessionMillis(packageName: String): Long? = prefs.getLong(SESSION_PREFIX + packageName, -1L).takeIf { it >= 0 }

    fun dismiss(packageName: String) {
        prefs.edit().remove(packageName).apply()
        _suspected.value = _suspected.value - packageName
    }

    private fun suspect(packageName: String, suspicion: Suspicion) {
        // A crash outranks a short session.
        if (_suspected.value[packageName] == Suspicion.CRASH && suspicion == Suspicion.SHORT_SESSION) return
        prefs.edit().putString(packageName, suspicion.name).apply()
        _suspected.value = _suspected.value + (packageName to suspicion)
    }

    private fun readSuspicions(): Map<String, Suspicion> =
        prefs.all.filterKeys { !it.startsWith(SESSION_PREFIX) }.mapNotNull { (key, value) -> (value as? String)?.let { runCatching { Suspicion.valueOf(it) }.getOrNull() }?.let { key to it } }.toMap()

    private fun safe(packageName: String) = packageName.filter { it.isLetterOrDigit() || it == '.' || it == '_' }

    private companion object {
        const val HOOK_LOG = "hook.log"
        const val PREVIOUS_LOG_START = "previous-log-start.txt"
        const val PREVIOUS_HOOK_LOG = "previous-hook.log"
        const val LOG_START = "log-start.txt"
        const val ENGINE_LOG = "engine.log"
        const val PERFORMANCE = "performance.txt"
        const val LIBRARIES = "libraries.txt"
        const val LAUNCH = "launch.txt"
        const val TOMBSTONE = "tombstone.pb"
        const val TOMBSTONE_INFO = "tombstone-info.txt"
        const val MAX_TRACE_BYTES = 2_000_000
        const val PREVIOUS_EXIT = "previous-exit.txt"
        const val MAX_CHARS = 600_000
        const val SHORT_SESSION_MS = 30_000L
        const val SESSION_PREFIX = "session:"
    }
}
