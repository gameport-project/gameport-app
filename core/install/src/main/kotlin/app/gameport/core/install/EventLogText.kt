package app.gameport.core.install

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The text of the game event log. A line starts with the time and `app=<id> `; the lines that follow it without that
 * start (stack frames) belong to it.
 */
object EventLogText {
    private val header = Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) app=(-?\\d+) ")

    /** Keeps the entries written at or after [cutoffMillis]. An entry whose time cannot be read is kept. */
    fun purgeOlderThan(text: String, cutoffMillis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return filter(text) { time, _ -> runCatching { format.parse(time)!!.time >= cutoffMillis }.getOrDefault(true) }
    }

    /** Drops every entry of [appId]. */
    fun forget(text: String, appId: Int): String = filter(text) { _, id -> id != appId.toString() }

    /** Only the entries of [appId]. */
    fun entriesOf(text: String, appId: Int): String = filter(text) { _, id -> id == appId.toString() }

    private fun filter(text: String, keep: (time: String, appId: String) -> Boolean): String {
        val out = StringBuilder()
        var keeping = true
        for (line in text.lineSequence()) {
            val match = header.find(line)
            if (match != null) keeping = keep(match.groupValues[1], match.groupValues[2])
            if (keeping && !(line.isEmpty() && out.isEmpty())) out.append(line).append('\n')
        }
        return out.toString().trimEnd('\n').let { if (it.isEmpty()) "" else it + "\n" }
    }
}
