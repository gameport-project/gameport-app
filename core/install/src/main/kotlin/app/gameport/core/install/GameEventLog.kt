package app.gameport.core.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What GamePort did for each game (downloads, patches, installs, their failures), kept for problem reports: the
 * errors of a patch are otherwise shown once and lost. A text file; lines older than a week are dropped when the
 * app starts ([purgeOlderThan]), and a game's lines go when it is uninstalled ([forget]). See [EventLogText].
 */
@Singleton
class GameEventLog @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "game-events.log")
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun note(appId: Int, message: String) {
        runCatching { file.appendText("${format.format(Date())} app=$appId $message\n") }
    }

    /** A failure with where it happened: the exception and its first frames. */
    fun failure(appId: Int, what: String, error: Throwable) {
        note(appId, "$what failed: $error\n" + error.stackTrace.take(8).joinToString("\n") { "    at $it" })
    }

    @Synchronized
    fun read(appId: Int): String = runCatching { EventLogText.entriesOf(file.readText(), appId) }.getOrDefault("")

    @Synchronized
    fun purgeOlderThan(cutoffMillis: Long) = rewrite { EventLogText.purgeOlderThan(it, cutoffMillis) }

    @Synchronized
    fun forget(appId: Int) = rewrite { EventLogText.forget(it, appId) }

    private fun rewrite(change: (String) -> String) {
        runCatching { if (file.exists()) file.writeText(change(file.readText())) }
    }
}
