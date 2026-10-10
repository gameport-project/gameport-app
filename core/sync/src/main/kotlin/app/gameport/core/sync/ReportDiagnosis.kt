package app.gameport.core.sync

/**
 * What can be said at once from what a game handed over, so a reader of a problem report does not have to find it in the files. It only
 * says what the files show, and what they cannot show: a list taken early says nothing of what loaded later.
 */
object ReportDiagnosis {
    /** How the folder of expansion files looks from GamePort: [declared] is what the game says in its manifest. */
    data class Expansion(val declared: Boolean, val folder: Folder, val files: Int) {
        enum class Folder { ABSENT, NOT_READABLE, READABLE }
    }

    fun of(libraries: String, startLog: String, previousExit: String, dataAgeSeconds: Long?, expansion: Expansion? = null): String = buildString {
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
        expansion?.let { appendExpansion(it) }
        appendControllers(startLog)
        val exits = previousExit.lineSequence().filter { it.startsWith("time=") }.toList()
        if (exits.isEmpty()) {
            appendLine("last exits: none recorded")
        } else {
            val summary = exits.groupingBy { line -> field(line, "reason") + (field(line, "status").takeIf { line.contains("reason=SIGNALED") }?.let { "/$it" } ?: "") }.eachCount()
            appendLine("last exits (newest first, as Android recorded them): " + summary.entries.joinToString(", ") { "${it.key} x${it.value}" })
            appendLine("  A game closed from the system menu can also end as SIGNALED/9: this alone does not say it failed.")
        }
    }

    private val SUGGESTION = Regex("""xrSuggestInteractionProfileBindings\(([^,]*), (\d+) bindings(?:, (\d+) actions)?\) -> (-?\d+)""")
    private val ACTIVE_PROFILE = Regex("""IP changed: /user/hand/(left|right)(/interaction_profiles/[^,]+), (-?\d+)""")
    private val DECISION = Regex("""GPXR\s*: (.*(?:nothing to translate|controls translated|taken for).*)""")

    /**
     * What the game asked of the controllers, read from the start of its log: the profiles it suggested (with how many controls and actions, and what the
     * runtime answered), the profile the system made active, and what GamePort's layer did about it. A game that has controls for the device's own controllers
     * is left alone by the layer; one that has none is translated. The three lines are what tells the two apart in a report.
     */
    private fun StringBuilder.appendControllers(startLog: String) {
        if (startLog.isBlank() || startLog.startsWith("(")) return
        val suggested = SUGGESTION.findAll(startLog).map { it.groupValues }.distinctBy { it[1] + it[2] + it[3] }.toList()
        if (suggested.isEmpty()) {
            appendLine("controllers: no line from GamePort's OpenXR layer in the start of the log (the game may not use OpenXR, or the layer is not in it)")
            return
        }
        appendLine("controllers, profiles the game suggested:")
        for ((_, name, bindings, actions, answer) in suggested) {
            val shown = name.ifBlank { "<unnamed profile>" }
            val counts = if (actions.isBlank()) "$bindings controls" else "$bindings controls, $actions actions"
            appendLine("  $shown: $counts, runtime answer $answer" + if (answer == "0") "" else " (refused)")
        }
        val active = ACTIVE_PROFILE.findAll(startLog).filter { it.groupValues[3].toInt() >= 0 }.map { it.groupValues[1] to it.groupValues[2] }.distinct().toList()
        appendLine("controllers, profile the system made active: " + if (active.isEmpty()) "none seen in the start of the log" else active.joinToString(", ") { (hand, profile) -> "$hand $profile" })
        val decisions = DECISION.findAll(startLog).map { it.groupValues[1].trim() }.distinct().toList()
        if (decisions.isEmpty()) appendLine("controllers, what the layer did: nothing said")
        else decisions.forEach { appendLine("controllers, what the layer did: $it") }
    }

    private fun StringBuilder.appendExpansion(expansion: Expansion) {
        val where = when {
            expansion.folder == Expansion.Folder.NOT_READABLE -> "the folder cannot be listed by GamePort"
            expansion.folder == Expansion.Folder.ABSENT -> "no Android/obb folder for this game"
            expansion.files == 0 -> "the Android/obb folder is empty"
            else -> "${expansion.files} file(s) in Android/obb"
        }
        appendLine("expansion files declared by the game: ${if (expansion.declared) "yes" else "no"}; $where")
        if (!expansion.declared || expansion.files > 0) return
        if (expansion.folder == Expansion.Folder.NOT_READABLE) {
            appendLine("  The game announces expansion files, and this report cannot tell whether they are there.")
        } else {
            appendLine("  The game announces expansion files and none are there: it cannot load its data, and stays on a black screen or closes.")
            appendLine("  A game installed by a GamePort older than 0.7.0 lost them at install. The first lines of events.txt say which version installed it,")
            appendLine("  and what the download held besides the APK. Installing the game again downloads them again.")
        }
    }

    private fun field(line: String, key: String): String = Regex("$key=(\\S+)").find(line)?.groupValues?.get(1).orEmpty()

    private const val STALE_SECONDS = 120L
}
