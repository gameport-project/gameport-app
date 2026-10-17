package app.gameport.core.sync

/**
 * What can be said at once from what a game handed over, so a reader of a problem report does not have to find it in the files. It only
 * says what the files show, and what they cannot show: a list taken early says nothing of what loaded later.
 */
object ReportDiagnosis {
    /** How the folder of expansion files looks from GamePort: [declared] is what the game says in its manifest. */
    data class Expansion(val declared: Boolean, val folder: Folder, val files: Int, val allFilesAccess: Boolean? = null, val obbFolderWritable: Boolean? = null) {
        enum class Folder { ABSENT, NOT_READABLE, READABLE }
    }

    /**
     * When GamePort last started the game ([launchedAtMillis], null when it never recorded one) and when the game last handed something over ([dataAtMillis]):
     * a game that was started and handed nothing over after that closed, or was refused, before its hook ran.
     */
    data class Launch(val launchedAtMillis: Long?, val dataAtMillis: Long?, val nowMillis: Long, val onPico: Boolean = false)

    /**
     * The checks a headset maker builds into its own store apps, found in the game's manifest and APK. GamePort re-signs the game to patch it, and such a check
     * can refuse the re-signed game at start (Pico shows "illegal signature") without a single line in any log.
     */
    fun vendorChecks(permissions: List<String>, metaKeys: List<String>, apkEntries: List<String>): List<String> = buildList {
        if (permissions.any { it.contains("tobactivate", ignoreCase = true) || it.endsWith("AUTH_CHECK") }) add("Pico: the permission ${permissions.first { it.contains("tobactivate", ignoreCase = true) || it.endsWith("AUTH_CHECK") }} (the headset checks the app with its maker)")
        if ("pvr.app.id" in metaKeys) add("Pico: the app id pvr.app.id (the app is registered with the Pico store, whose check looks at the signature)")
        if (apkEntries.any { it.endsWith("libpxrplatformloader.so") }) add("Pico: libpxrplatformloader.so (the Pico platform SDK, which can check the app's licence)")
        if (apkEntries.any { it.endsWith("libovrplatformloader.so") }) add("Meta: libovrplatformloader.so (the Meta platform SDK, which can check the app's entitlement)")
    }

    fun of(libraries: String, startLog: String, previousExit: String, dataAgeSeconds: Long?, expansion: Expansion? = null, launch: Launch? = null, vendorChecks: List<String> = emptyList()): String = buildString {
        fun yes(value: Boolean) = if (value) "yes" else "no"
        // First of all: a game whose essential controls the runtime refused cannot be played with the controllers.
        leftOutControls(startLog).filter { it.essential.isNotBlank() }.forEach {
            appendLine("WARNING: the runtime refused ESSENTIAL controls of the game for ${it.profile}:${it.essential}. The controllers may not work as they should.")
        }
        val known = libraries.isNotBlank() && !libraries.startsWith("(")
        appendLine("hook of GamePort ran in the game: " + if (startLog.isBlank() || startLog.startsWith("(")) "unknown (no log handed over)" else yes(startLog.contains("GPHook")))
        if (known) {
            val loader = libraries.contains("libopenxr_loader") || libraries.contains("libopenxr_forwardloader")
            appendLine("OpenXR loader loaded: " + if (loader) "yes" else "not in this list (the game may use a vendor SDK instead, which says nothing against it, or load it later)")
            appendLine("OpenXR layer of GamePort loaded: " + yes(libraries.contains("XrApiLayer_gameport")))
            appendLine("Steam library loaded: " + yes(libraries.contains("libsteam_api") || libraries.contains("libsteamclient")))
        } else {
            appendLine("loaded libraries: not handed over")
        }
        dataAgeSeconds?.let {
            appendLine("last data from the game: $it s before this report")
            if (it > STALE_SECONDS) appendLine("  (old: what the lists hold may be from the start of the run, not from now)")
        }
        launch?.let { appendLaunch(it, vendorChecks.isNotEmpty()) }
        if (vendorChecks.isNotEmpty()) {
            appendLine("checks built into the game by the headset's maker (a re-signed game can be refused by them, at start and without a log line):")
            vendorChecks.forEach { appendLine("  $it") }
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

    private fun StringBuilder.appendLaunch(launch: Launch, hasVendorChecks: Boolean) {
        val at = launch.launchedAtMillis
        if (at == null) {
            appendLine("launch from GamePort: none recorded (the game was not started from GamePort, or this GamePort did not record it)")
            return
        }
        appendLine("last launch from GamePort: ${(launch.nowMillis - at) / 1000} s before this report")
        val answered = launch.dataAtMillis != null && launch.dataAtMillis >= at
        appendLine("the game answered after that launch (its hook handed something over): " + if (answered) "yes" else "no")
        if (!answered) {
            appendLine("  The game was started and nothing came back from it: it was closed, or refused by the system, before its hook ran.")
            appendLine("  A dialog of the system leaves no line in any log; ask the player what appeared on screen." + if (hasVendorChecks) " The checks below may explain it." else "")
            if (launch.onPico) {
                appendLine("  On a Pico, the system itself refuses a re-signed game with \"Verification failed: illegal signature\" (Cancel / Buy now), before the game runs.")
                appendLine("  It was seen on games whose manifest carries no Pico marker, so a game without the checks below can be refused as well.")
            }
        }
    }

    /** The controls the layer left out of one table of the game because the runtime refused them, by how much they matter (see the layer's ClassifyControl). */
    data class LeftOut(val profile: String, val essential: String, val plus: String, val dispensable: String)

    private val LEFT_OUT = Regex("""GPXR\s*: controls left out of (\S+): essential \[(.*?) ?\] \| essential plus \[(.*?) ?\] \| dispensable \[(.*?) ?\]""")

    internal fun leftOutControls(startLog: String): List<LeftOut> =
        LEFT_OUT.findAll(startLog).map { LeftOut(it.groupValues[1], it.groupValues[2], it.groupValues[3], it.groupValues[4]) }.distinct().toList()

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
        leftOutControls(startLog).forEach {
            fun say(text: String) = text.trim().ifEmpty { "none" }
            appendLine("controls the runtime refused for ${it.profile} (left out, the others are given): essential: ${say(it.essential)}; essential plus: ${say(it.plus)}; dispensable: ${say(it.dispensable)}")
        }
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
        expansion.allFilesAccess?.let { appendLine("access to all files given to GamePort: ${if (it) "yes" else "no (GamePort asks for it before it installs a game with expansion files)"}") }
        expansion.obbFolderWritable?.let { appendLine("the Android/obb folder can be written by GamePort: ${if (it) "yes" else "no"}") }
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
