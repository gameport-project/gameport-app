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
        RunArchive.noteRun(
            folder, extras.getInt("pid", 0),
            archived = mapOf(LOG_START to PREVIOUS_LOG_START, HOOK_LOG to PREVIOUS_HOOK_LOG, DIGEST to PREVIOUS_DIGEST, PERFORMANCE to PREVIOUS_PERFORMANCE),
            // Everything the hook writes for a run is replaced when the next run hands over, or goes: a file the new run never sent (the stacks, the threads, the traffic) stays no more.
            discarded = { name -> name.startsWith(DIAG_PREFIX) || name in RUN_FILES },
        )
        // The record the hook kept in the game's own folder, which the run before left: it says what the run did while GamePort could not be reached. Kept apart, never replaced by this run's files.
        extras.getByteArray("blackBoxGz")?.let { gunzip(it, 400_000)?.let { text -> File(folder, PREVIOUS_BLACKBOX).writeBytes(text) } }
        extras.getByteArray("headGz")?.let { gunzip(it)?.let { text -> File(folder, LOG_START).writeBytes(text) } }
        extras.getByteArray("logGz")?.let { gunzip(it)?.let { text -> File(folder, HOOK_LOG).writeBytes(text) } }
        extras.getByteArray("performanceGz")?.let { gunzip(it)?.let { text -> File(folder, PERFORMANCE).writeBytes(text) } }
        extras.getByteArray("networkGz")?.let { gunzip(it)?.let { text -> File(folder, NETWORK).writeBytes(text) } }
        extras.getByteArray("threadsGz")?.let { gunzip(it)?.let { text -> File(folder, THREADS).writeBytes(text) } }
        extras.getByteArray("diagGz")?.let { gunzip(it)?.let { text -> saveDiagnostics(folder, String(text, Charsets.UTF_8)) } }
        extras.getByteArray("engineGz")?.let { gunzip(it)?.let { text -> File(folder, ENGINE_LOG).writeBytes(text) } }
        // How steadily the run went, as the hook counted it so far: replaced at every hand-over, as it counts the run from its start.
        if (extras.containsKey("steadinessSeconds")) {
            val counted = app.gameport.core.model.Steadiness(extras.getInt("steadinessSeconds"), extras.getInt("steadinessHeld"), extras.getInt("steadinessTarget"))
            prefs.edit().putString(STEADINESS_PREFIX + packageName, counted.write()).apply()
        }
        extras.getString("libraries")?.takeIf { it.isNotEmpty() }?.let { File(folder, LIBRARIES).writeText(it.takeLast(MAX_CHARS)) }
        extras.getString("launch")?.takeIf { it.isNotEmpty() }?.let { File(folder, LAUNCH).writeText(it.takeLast(MAX_CHARS)) }
        // Said at every hand-over, empty when the run has met none: what an earlier run found must not stay as if this one had.
        extras.getString("nativeGaps")?.let { gaps -> if (gaps.isEmpty()) File(folder, NATIVE_GAPS).delete() else File(folder, NATIVE_GAPS).writeText(gaps.take(MAX_CHARS)) }
        // What the whole log of the run says; replaced at every hand-over, as it counts the run from its first line.
        extras.getString("digest")?.let { digest -> if (digest.isEmpty()) File(folder, DIGEST).delete() else File(folder, DIGEST).writeText(digest.take(MAX_CHARS)) }
        // The calls the game made to Steam (the shim's table, read by the hook); replaced at every hand-over, it counts the whole run.
        extras.getString("steamCalls")?.let { calls -> if (calls.isEmpty()) File(folder, STEAM_CALLS).delete() else File(folder, STEAM_CALLS).writeText(calls.take(MAX_CHARS)) }
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

    /** The diagnostic files of a hand-over: each starts with a line holding U+0001 and its name, and runs to the next such line. */
    private fun saveDiagnostics(folder: File, text: String) {
        text.split('\u0001').drop(1).forEach { block ->
            val name = block.substringBefore('\n').filter { it.isLetterOrDigit() || it == '-' }.take(40)
            if (name.isNotEmpty()) File(folder, "$DIAG_PREFIX$name.txt").writeText(block.substringAfter('\n').take(MAX_CHARS))
        }
    }

    /** The diagnostic files the game handed over, by report file name. */
    fun diagnostics(packageName: String): Map<String, String> =
        File(directory, safe(packageName)).listFiles { file -> file.isFile && file.name.startsWith(DIAG_PREFIX) }.orEmpty().sortedBy { it.name }
            .associate { it.name to runCatching { it.readText() }.getOrDefault("") }

    /**
     * Every call the game made to GamePort, one line each: the time, the moment in the run, the process, the kind of call and how it ended. It says whether the hook still reached GamePort
     * after a given moment, and which process each file of a report comes from, which nothing else in the report tells. Bounded: the oldest lines go.
     */
    @Synchronized
    fun noteCall(packageName: String, method: String, callingPid: Int, outcome: String, nowMillis: Long = System.currentTimeMillis()) {
        runCatching {
            val folder = File(directory, safe(packageName)).apply { mkdirs() }
            val file = File(folder, CALLS)
            // The moment in the run is counted from the first call of this process (the hook begins a second or two after the process): the launch time GamePort keeps is set again
            // by the game's own calls in the course of a run, and would make the moments start over.
            val run = prefs.getString(CALLRUN_PREFIX + packageName, null)?.split(":")
            val start = if (run != null && run.size == 2 && run[0] == callingPid.toString()) run[1].toLongOrNull() else null
            val runStart = start ?: nowMillis.also { prefs.edit().putString(CALLRUN_PREFIX + packageName, "$callingPid:$nowMillis").apply() }
            val since = java.lang.String.format(java.util.Locale.ROOT, "+%.1fs", (nowMillis - runStart) / 1000.0)
            val clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date(nowMillis))
            if (file.length() > MAX_CALLS_BYTES) file.writeText(file.readLines().takeLast(CALLS_KEPT).joinToString("\n", postfix = "\n"))
            file.appendText("$clock $since pid=$callingPid $method $outcome\n")
        }
    }

    /** The calls the game made to GamePort, as [noteCall] kept them, or empty. */
    fun calls(packageName: String): String = text(packageName, CALLS)

    /** What the run before the last one left in its own record in the game's folder, handed over by the last run, or empty. */
    fun previousBlackBox(packageName: String): String = text(packageName, PREVIOUS_BLACKBOX)

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
        prefs.edit().remove(packageName).remove(SESSION_PREFIX + packageName).remove(LAUNCH_PREFIX + packageName).remove(CALLRUN_PREFIX + packageName).remove(LAUNCH_PID_PREFIX + packageName).apply()
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
        File(directory, safe(packageName)).listFiles()?.filter { it.isFile && it.name != CALLS }?.maxOfOrNull { it.lastModified() }

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

    /**
     * The game just started. The moment is also kept on disk: a game that is killed, crashes or runs out of memory never says it closed, and GamePort may be stopped meanwhile.
     * [pid] is the process that says it: the game's cloud sync begins again every half minute of a run, and each of them says so too, which must not count as a new launch (the session
     * would then be counted from the last of them, and any run longer than half a minute would look like a game that closed at once).
     */
    @Synchronized
    fun launched(packageName: String, pid: Int = 0, nowMillis: Long = System.currentTimeMillis()) {
        val recorded = launched.containsKey(packageName) || prefs.contains(LAUNCH_PREFIX + packageName)
        if (!RunStarts.isNew(prefs.getInt(LAUNCH_PID_PREFIX + packageName, 0), pid, recorded)) return
        launched[packageName] = nowMillis
        prefs.edit().putLong(LAUNCH_PREFIX + packageName, nowMillis).putInt(LAUNCH_PID_PREFIX + packageName, pid).apply()
    }

    /** The game left the screen or closed, or its process is gone: closing within seconds of the start is a sign of trouble. */
    @Synchronized
    fun left(packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        val start = launched.remove(packageName) ?: prefs.getLong(LAUNCH_PREFIX + packageName, -1L).takeIf { it >= 0 } ?: return
        prefs.edit().remove(LAUNCH_PREFIX + packageName).putLong(SESSION_PREFIX + packageName, nowMillis - start).apply()
        if (nowMillis - start < SHORT_SESSION_MS) suspect(packageName, Suspicion.SHORT_SESSION)
    }

    /** How steadily the game's last run went, as the hook counted it, or null when nothing was handed over. It may be too short to say: see [app.gameport.core.model.Steadiness.usable]. */
    fun steadiness(packageName: String): app.gameport.core.model.Steadiness? = app.gameport.core.model.Steadiness.parse(prefs.getString(STEADINESS_PREFIX + packageName, null))

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
        prefs.all.filterKeys { !it.startsWith(SESSION_PREFIX) && !it.startsWith(LAUNCH_PREFIX) && !it.startsWith(CALLRUN_PREFIX) && !it.startsWith(LAUNCH_PID_PREFIX) }.mapNotNull { (key, value) -> (value as? String)?.let { runCatching { Suspicion.valueOf(it) }.getOrNull() }?.let { key to it } }.toMap()

    private fun safe(packageName: String) = packageName.filter { it.isLetterOrDigit() || it == '.' || it == '_' }

    private companion object {
        const val HOOK_LOG = "hook.log"
        const val PREVIOUS_LOG_START = "previous-log-start.txt"
        const val PREVIOUS_HOOK_LOG = "previous-hook.log"
        const val LOG_START = "log-start.txt"
        const val ENGINE_LOG = "engine.log"
        const val PERFORMANCE = "performance.txt"
        const val NETWORK = "network.txt"
        const val THREADS = "threads.txt"
        const val DIAG_PREFIX = "diag-"
        const val LIBRARIES = "libraries.txt"
        const val NATIVE_GAPS = "native-gaps.txt"
        const val DIGEST = "log-digest.txt"
        const val STEAM_CALLS = "steam-calls.txt"
        const val CALLS = "hook-calls.txt"
        const val PREVIOUS_BLACKBOX = "previous-run-blackbox.txt"
        const val PREVIOUS_DIGEST = "previous-log-digest.txt"
        const val PREVIOUS_PERFORMANCE = "previous-performance.txt"
        const val MAX_CALLS_BYTES = 60_000L
        const val CALLS_KEPT = 300
        /** The files of one run that the next run replaces or discards when it begins (the diagnostics are told apart by their prefix). */
        val RUN_FILES = setOf(NETWORK, THREADS, LIBRARIES, NATIVE_GAPS, ENGINE_LOG, DIGEST, PERFORMANCE, STEAM_CALLS, LAUNCH)
        const val LAUNCH = "launch.txt"
        const val TOMBSTONE = "tombstone.pb"
        const val TOMBSTONE_INFO = "tombstone-info.txt"
        const val MAX_TRACE_BYTES = 2_000_000
        const val PREVIOUS_EXIT = "previous-exit.txt"
        const val MAX_CHARS = 600_000
        const val SHORT_SESSION_MS = 30_000L
        const val SESSION_PREFIX = "session:"
        const val STEADINESS_PREFIX = "steadiness:"
        const val LAUNCH_PREFIX = "launch:"
        const val CALLRUN_PREFIX = "callrun:"
        const val LAUNCH_PID_PREFIX = "launchpid:"
    }
}
