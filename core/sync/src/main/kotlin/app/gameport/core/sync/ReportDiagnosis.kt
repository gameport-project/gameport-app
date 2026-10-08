package app.gameport.core.sync

/**
 * What can be said at once from what a game handed over, so a reader of a problem report does not have to find it in the files. It only
 * says what the files show, and what they cannot show: a list taken early says nothing of what loaded later.
 */
object ReportDiagnosis {
    fun of(libraries: String, startLog: String, previousExit: String, dataAgeSeconds: Long?): String = buildString {
        fun yes(value: Boolean) = if (value) "yes" else "no"
        val known = libraries.isNotBlank() && !libraries.startsWith("(")
        appendLine("hook of GamePort ran in the game: " + if (startLog.isBlank() || startLog.startsWith("(")) "unknown (no log handed over)" else yes(startLog.contains("GPHook")))
        if (known) {
            appendLine("OpenXR loader loaded: " + yes(libraries.contains("libopenxr_loader")))
            appendLine("OpenXR layer of GamePort loaded: " + yes(libraries.contains("XrApiLayer_gameport")))
            appendLine("Steam library loaded: " + yes(libraries.contains("libsteam_api") || libraries.contains("libsteamclient")))
        } else {
            appendLine("loaded libraries: not handed over")
        }
        dataAgeSeconds?.let {
            appendLine("last data from the game: $it s before this report")
            if (it > STALE_SECONDS) appendLine("  (old: what the lists hold may be from the start of the run, not from now)")
        }
        val exits = previousExit.lineSequence().filter { it.startsWith("time=") }.toList()
        if (exits.isEmpty()) {
            appendLine("last exits: none recorded")
        } else {
            val summary = exits.groupingBy { line -> field(line, "reason") + (field(line, "status").takeIf { line.contains("reason=SIGNALED") }?.let { "/$it" } ?: "") }.eachCount()
            appendLine("last exits (newest first, as Android recorded them): " + summary.entries.joinToString(", ") { "${it.key} x${it.value}" })
            appendLine("  A game closed from the system menu can also end as SIGNALED/9: this alone does not say it failed.")
        }
    }

    private fun field(line: String, key: String): String = Regex("$key=(\\S+)").find(line)?.groupValues?.get(1).orEmpty()

    private const val STALE_SECONDS = 120L
}
