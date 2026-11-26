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

    /** What the player chose for the controllers presented to the game ([presented]: `auto`, `frame` or `touch`) and what became of it the last run ([applied]: `applied`, `fallback`, `ignored`, or empty when not reported). */
    data class ControllerChoice(val presented: String, val applied: String)

    /** The manifest metadata a game installed under a name of GamePort's own carries, holding the package name its APK came with (see the patch `package_rename`). */
    private const val RENAMED_PACKAGE = "app.gameport.original_package"

    /**
     * The checks a headset maker builds into its own store apps, found in the game's manifest and APK. GamePort re-signs the game to patch it, and such a check
     * can refuse the re-signed game at start, without a single line in any log: a Pico's `com.bytedance.pico.matrix` asks Pico's servers about every third-party
     * application it sees, with the package name and the signature of the installed APK, and force-stops the ones whose signature is not the one registered for
     * that name. Installing the game under a package name of GamePort's own takes it past that check.
     */
    fun vendorChecks(permissions: List<String>, metaKeys: List<String>, apkEntries: List<String>): List<String> = buildList {
        if (permissions.any { it.contains("tobactivate", ignoreCase = true) || it.endsWith("AUTH_CHECK") }) add("Pico: the permission ${permissions.first { it.contains("tobactivate", ignoreCase = true) || it.endsWith("AUTH_CHECK") }} (the headset checks the app with its maker)")
        if ("pvr.app.id" in metaKeys) add("Pico: the app id pvr.app.id (the app is registered with the Pico store, whose check looks at the signature)")
        if (apkEntries.any { it.endsWith("libpxrplatformloader.so") }) add("Pico: libpxrplatformloader.so (the Pico platform SDK, which can check the app's licence)")
        if (apkEntries.any { it.endsWith("libovrplatformloader.so") }) add("Meta: libovrplatformloader.so (the Meta platform SDK, which can check the app's entitlement)")
        if (RENAMED_PACKAGE in metaKeys) add("Installed under a package name of GamePort's own, so a headset that refuses a re-signed game lets it start (the name the game came with is kept in $RENAMED_PACKAGE)")
    }

    fun of(libraries: String, startLog: String, previousExit: String, dataAgeSeconds: Long?, expansion: Expansion? = null, launch: Launch? = null, vendorChecks: List<String> = emptyList(), digest: String = "", steamCalls: String = "", sessionMillis: Long? = null, calls: String = "", events: String = "", blackBox: String = "", controllerChoice: ControllerChoice? = null): String = buildString {
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
            // The list of libraries is read late: a game that makes an OpenXR instance only to look (Unity does) has let go of the layer by then. The log of its start says it was there.
            val layerAtStart = startLog.contains("XR_APILAYER_GAMEPORT") || startLog.contains("GamePort OpenXR layer active")
            appendLine(
                "OpenXR layer of GamePort loaded: " + when {
                    libraries.contains("XrApiLayer_gameport") -> "yes"
                    layerAtStart -> "yes, at the start of the run (the game's log says so; it is no longer in the list of loaded libraries, which is read later)"
                    else -> "no"
                },
            )
            appendLine("Steam library of the game loaded: " + yes(libraries.contains("libsteam_api")))
            // The game's library loads the shim only when the game starts Steam: not there, the game did not get that far.
            // The list is read at the end of the run, after the game let the stand-in go: the table of the calls it answered says it was there all the same.
            val shimLoaded = libraries.contains("libsteamclient")
            val shimAnswered = !shimLoaded && steamCalls.isNotBlank() && CALL_LINE.containsMatchIn(steamCalls)
            appendLine("Steam stand-in of GamePort (shim) loaded: " + if (shimAnswered) "yes, during the run (it answered the game's calls, see steam-calls.txt; it is no longer in the list, which is read at the end)" else yes(shimLoaded))
        } else {
            appendLine("loaded libraries: not handed over")
        }
        dataAgeSeconds?.let {
            appendLine("last data from the game: $it s before this report")
            if (it > STALE_SECONDS) appendLine("  (old: what the lists hold may be from the start of the run, not from now)")
        }
        launch?.let { appendLaunch(it, vendorChecks.isNotEmpty()) }
        appendCoverage(sessionMillis, calls)
        appendBlackBox(blackBox)
        appendDigest(digest, libraries)
        appendSteamCalls(steamCalls)
        if (vendorChecks.isNotEmpty()) {
            appendLine("checks built into the game by the headset's maker (a re-signed game can be refused by them, at start and without a log line):")
            vendorChecks.forEach { appendLine("  $it") }
        }
        expansion?.let { appendExpansion(it) }
        appendControllers(startLog, controllerChoice)
        val exits = previousExit.lineSequence().filter { it.startsWith("time=") }.toList()
        // A system that does not record how a game ended (before Android 11) still prints a native crash in the log: it is told here, from the log itself.
        val crashes = nativeCrashes(startLog)
        if (exits.isEmpty()) {
            appendLine("last exits: none recorded" + if (crashes.isNotEmpty()) " (this system records none; the log of the game shows a native crash, below)" else "")
        } else {
            val summary = exits.groupingBy { line -> field(line, "reason") + (field(line, "status").takeIf { line.contains("reason=SIGNALED") }?.let { "/$it" } ?: "") }.eachCount()
            appendLine("last exits (newest first, as Android recorded them): " + summary.entries.joinToString(", ") { "${it.key} x${it.value}" })
            appendLine("  A game closed from the system menu can also end as SIGNALED/9: this alone does not say it failed.")
            appendExitsAgainstLaunches(exits, events, calls)
            // A crash of long ago says nothing of the run that is reported: when it was, and whether the game ran again since, is what a reader needs to weigh it.
            val newest = exits.firstOrNull()?.let { field(it, "time").toLongOrNull() }
            val now = launch?.nowMillis
            if (newest != null && now != null && now >= newest) {
                appendLine("  The newest of these ended on ${stamp(newest)}, ${ago(now - newest)} before this report (made ${stamp(now)}, the time of the device).")
                if (launch.launchedAtMillis != null && launch.launchedAtMillis > newest) appendLine("  The game was started again since: that end does not explain the run of this report.")
            }
        }
        if (crashes.isNotEmpty()) {
            appendLine("native crash seen in the log of the game: " + crashes.joinToString(" | "))
            appendLine("  It says where the game died, not whose fault it is: the frames of the crash (the library that faulted) are in game-log-start.txt when the game handed them over.")
        }
    }

    /** A line of the file of the calls of the game to GamePort: the time of the device, the moment in the run, the process, the kind of call and how it ended. */
    private val CALL_RECORD = Regex("""^\S+ \+([\d.]+)s pid=(\d+) (\w+) (\S+)""")

    /**
     * What the calls of the game to GamePort say (hook-calls.txt): which process they came from, how many of each kind and when the last of each was. Compared with how long the run
     * lasted, it tells a game whose hook stopped handing over from one that was stopped: the files of the report describe only what was handed over.
     */
    private fun StringBuilder.appendCoverage(sessionMillis: Long?, calls: String) {
        val all = calls.lineSequence().mapNotNull { CALL_RECORD.find(it) }.map { Triple(it.groupValues[1].toDouble(), it.groupValues[2], it.groupValues[3]) }.toList()
        if (all.isEmpty()) {
            appendLine("calls of the game to GamePort: none recorded (hook-calls.txt): the hook of the game has not reached GamePort since this was kept")
            return
        }
        // Only the process that called last: a run before has its own moments, which would be mixed with this one's.
        val pid = all.last().second
        val rows = all.takeLastWhile { it.second == pid }
        val byKind = rows.groupBy { it.third }.entries.joinToString(", ") { (kind, list) -> "$kind x${list.size} (last at +${list.maxOf { it.first }.toLong()} s)" }
        appendLine("calls of the game to GamePort, process $pid: $byKind")
        val lastAny = rows.maxOf { it.first }
        val lastLog = rows.filter { it.third == "log" }.maxOfOrNull { it.first }
        val session = sessionMillis?.let { it / 1000.0 }
        when {
            lastLog != null && lastAny - lastLog >= SILENCE_SECONDS ->
                appendLine("  The game still called GamePort until +${lastAny.toLong()} s, but handed no log after +${lastLog.toLong()} s: it was running, and the hook stopped handing over. The files below describe the run only up to +${lastLog.toLong()} s.")
            session != null && session - lastAny >= SILENCE_SECONDS && session >= LONG_RUN_SECONDS ->
                appendLine("  Nothing came from the game after +${lastAny.toLong()} s, though the run lasted ${session.toLong()} s: the game was stopped or frozen from then, or GamePort was stopped. The files below describe the run only up to +${lastAny.toLong()} s.")
        }
    }

    private val LAUNCH_EVENT = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) .*launch requested from GamePort""")

    /** Each end Android recorded, with the launch it follows: the end of a run is told only at the next launch, and which launch it belongs to is what makes it readable. */
    private fun StringBuilder.appendExitsAgainstLaunches(exits: List<String>, events: String, calls: String) {
        // What each process said to GamePort: a process that never said the game came to the screen ("resumed") is one GamePort woke to fetch the saves or the sizes of its files, not a run that was played.
        val processes = calls.lineSequence().mapNotNull { CALL_WITH_CLOCK.find(it) }.groupBy({ it.groupValues[2] }, { it.groupValues[1] to it.groupValues[3] })
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
        val launches = events.lineSequence().mapNotNull { LAUNCH_EVENT.find(it)?.groupValues?.get(1) }
            .mapNotNull { runCatching { format.parse(it)?.time }.getOrNull() }.toList().sorted()
        if (launches.isEmpty()) return
        exits.take(5).forEach { line ->
            val end = field(line, "time").toLongOrNull() ?: return@forEach
            val own = field(line, "pid").let { processes[it] }
            if (own != null && own.none { it.second == "resumed" }) {
                appendLine("  process ${field(line, "pid")} ended ${format.format(java.util.Date(end))}: ${field(line, "reason")}, a short process GamePort started to fetch the saves (${own.size} call(s) to GamePort), not a run that was played")
                return@forEach
            }
            val start = launches.lastOrNull { it <= end } ?: return@forEach
            val pid = field(line, "pid").takeIf { it.isNotEmpty() }?.let { " (process $it)" }.orEmpty()
            appendLine("  the run launched ${format.format(java.util.Date(start))} ended ${format.format(java.util.Date(end))}$pid, ${(end - start) / 1000} s after its launch: ${field(line, "reason")}, importance ${field(line, "importance")}")
        }
    }

    private val CALL_WITH_CLOCK = Regex("""^(\d{2}:\d{2}:\d{2}) \S+ pid=(\d+) (\w+)""")
    private val BLACKBOX_TIME = Regex("""^\+([\d.]+)s """)
    private val BLACKBOX_SENDS = Regex("""sends=(\d+)/(\d+)""")

    /**
     * What the record the hook kept in the game's own folder says of the run before (previous-run-blackbox.txt): how long it went on, how many hand-overs to GamePort worked and failed,
     * and the stretches where the process did not run. That record does not depend on GamePort being there, so it is what tells a run whose hand-overs stopped from one that was stopped.
     */
    private fun StringBuilder.appendBlackBox(blackBox: String) {
        if (blackBox.isBlank()) return
        val lines = blackBox.lines().filter { it.isNotBlank() }
        val last = lines.mapNotNull { BLACKBOX_TIME.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }.maxOrNull() ?: return
        val sends = lines.lastOrNull { it.contains(" beat ") }?.let { BLACKBOX_SENDS.find(it) }
        val gaps = lines.filter { it.contains(" GAP ") }
        appendLine(
            "record the hook kept for the run before (previous-run-blackbox.txt): it goes on to +${last.toLong()} s" +
                (sends?.let { ", hand-overs to GamePort ${it.groupValues[1]} worked and ${it.groupValues[2]} failed" } ?: "") +
                ", " + if (gaps.isEmpty()) "no stretch where the process did not run" else "${gaps.size} stretch(es) where the process did not run",
        )
        gaps.take(3).forEach { appendLine("  $it") }
    }

    private val TABLE_TIMES = Regex("""(?m)^\s*\d+x\s+\+\s*[\d.]+ s\s+\+\s*([\d.]+) s""")

    /** When the Steam stand-in last answered the game, in this run (as far as the last hand-over) and in the run before: the game was alive until then, whatever its log says. */
    private fun StringBuilder.appendSteamLife(steamCalls: String) {
        val now = steamCalls.substringBefore("== The run before this one")
        val before = steamCalls.substringAfter("== The run before this one", "")
        val lastNow = TABLE_TIMES.findAll(now).maxOfOrNull { it.groupValues[1].toDouble() }
        val lastBefore = TABLE_TIMES.findAll(before).maxOfOrNull { it.groupValues[1].toDouble() }
        if (lastNow == null && lastBefore == null) return
        appendLine("the Steam stand-in answered the game until: " + listOfNotNull(
            lastNow?.let { "+${it.toLong()} s in this run (as of the last hand-over)" },
            lastBefore?.let { "+${it.toLong()} s in the run before (it was alive that long)" },
        ).joinToString(", "))
    }

    private val FATAL_SIGNAL = Regex("""Fatal signal (\d+) \((\w+)\)[^\n]*?fault addr (0x[0-9a-f]+) in tid \d+ \(([^)]*)\)""")

    /** What the game's log says of its native crashes: the signal, the address that faulted and the name of the thread, once for each (a run that crashed twice is two). */
    internal fun nativeCrashes(startLog: String): List<String> =
        FATAL_SIGNAL.findAll(startLog).map { "signal ${it.groupValues[1]} (${it.groupValues[2]}), fault address ${it.groupValues[3]}, thread ${it.groupValues[4]}" }.distinct().toList()

    private val FOCUSED = Regex("""focused reached: (yes|no)""")
    private val STATES = Regex("""OpenXR session states reached: (.+)""")
    private val FRAME_RATE = Regex("""Frame rate \(.*?\): median (\d+), lowest tenth (\d+), over (\d+) s, target (\d+)""")
    private val SIGN_IN = Regex("""Sign-in problems: (\d+) line""")
    private val ENGINE_LINES = Regex("""lines of the engine's own log tag \(UE, UE4, Unity\): (\d+)""")
    private val EXCEPTION_LINE = Regex("""(?m)^  (\d+)x  \(first at [^)]*\)  (.+)$""")

    /**
     * What the whole log says (log-digest.txt, counted by the game's hook over every line), in the few facts that tell a reader where to look: whether the OpenXR session
     * ever had focus, a frame rate far under its target, sign-in problems, an Unreal game that wrote nothing of its own, and the exception that came most often.
     */
    private fun StringBuilder.appendDigest(digest: String, libraries: String) {
        if (digest.isBlank()) return
        val states = STATES.find(digest)?.groupValues?.get(1)?.trim().orEmpty()
        if (states.isNotEmpty() && !states.startsWith("none")) {
            val focused = FOCUSED.find(digest)?.groupValues?.get(1) == "yes"
            appendLine("OpenXR session reached focus: " + if (focused) "yes" else "no (states seen: $states): the game was not given the controllers' input as the active application")
        }
        FRAME_RATE.find(digest)?.let { match ->
            val (median, low, seconds, target) = match.destructured
            // A few seconds of measure are the start of the run, which loads things: a figure from them says nothing of how the game runs.
            if (seconds.toInt() < FRAME_RATE_MIN_SECONDS) {
                appendLine("frame rate: too few measures to say ($seconds s of the runtime's line, taken at the start of the run): see performance.txt for the line itself")
                return@let
            }
            val slow = target.toInt() > 0 && median.toInt() < target.toInt() * 8 / 10
            appendLine("frame rate: median $median of $target, lowest tenth $low, over $seconds s" + if (slow) " (far under the target)" else "")
        }
        SIGN_IN.find(digest)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { appendLine("sign-in problems in the log: $it line(s) (the first ones are in log-digest.txt)") }
        val engineLines = ENGINE_LINES.find(digest)?.groupValues?.get(1)?.toLongOrNull()
        val unreal = libraries.contains("libUnreal.so") || libraries.contains("libUE4.so")
        if (unreal && engineLines == 0L) {
            appendLine("the game is an Unreal game and its engine wrote no line to the system log (a build without the engine's logs): a failure of its sign-in or network is not told by it.")
            appendLine("  Its exceptions and the lines that repeat the most are in log-digest.txt.")
        }
        EXCEPTION_LINE.find(digest)?.let { appendLine("exception named most often in the log: ${it.groupValues[1]}x ${it.groupValues[2].take(160)}") }
    }

    private val CALL_LINE = Regex("""(?m)^\s*(\d+)x\s+\+\s*[\d.]+ s\s+\+\s*[\d.]+ s\s+(.+?)(?:\s+\[\d+ timed.*)?$""")

    /**
     * What the game asked of Steam (steam-calls.txt, kept by the shim): whether it ever asked for a ticket and for which service. A game that signs in online and never asks
     * Steam for one takes another way (a check on the device it runs on, a store account of its own), which no log line says.
     */
    private fun StringBuilder.appendSteamCalls(steamCalls: String) {
        if (steamCalls.isBlank()) return
        val current = steamCalls.substringBefore("== The run before this one")
        val calls = CALL_LINE.findAll(current).associate { it.groupValues[2] to it.groupValues[1].toLong() }
        if (calls.isEmpty()) return
        appendSteamLife(steamCalls)
        val sessionAsked = calls.entries.filter { it.key.startsWith("Steam_User::GetAuthSessionTicket") }.sumOf { it.value }
        val webServices = calls.entries.filter { it.key.startsWith("web ticket asked for service: ") }.map { it.key.removePrefix("web ticket asked for service: ") to it.value }
        val given = calls.entries.filter { it.key.startsWith("session ticket read from GamePort") || it.key.startsWith("web ticket read from GamePort") }.sumOf { it.value }
        val notGiven = calls.entries.filter { it.key.contains("NOT given by GamePort") || it.key.contains("refused") }.sumOf { it.value }
        // The ticket an online service checks with the publisher's key: GamePort's stand-in makes one itself, which carries no such proof, so a service that verifies it refuses it.
        val encryptedAsked = calls.entries.filter { it.key.startsWith("Steam_User::RequestEncryptedAppTicket") }.sumOf { it.value }
        if (encryptedAsked > 0) {
            appendLine("encrypted app ticket asked by the game: x$encryptedAsked. GamePort cannot give a real one yet: the stand-in builds a ticket without the publisher's encryption, so an online service that verifies it refuses the sign-in.")
            appendLine("  Many requests one after the other (about one every few seconds) read as a sign-in that is refused and tried again. The service's own answer is not in any log.")
        }
        if (sessionAsked == 0L && webServices.isEmpty()) {
            appendLine(
                if (encryptedAsked > 0) "Steam session and web tickets asked by the game: none (the game asked the encrypted app ticket above instead)"
                else "Steam tickets asked by the game: none (it never asked Steam for a session ticket or a web ticket in this run: if its online sign-in fails, it does not go through Steam)",
            )
        } else {
            val parts = buildList {
                if (sessionAsked > 0) add("session ticket x$sessionAsked")
                webServices.forEach { (service, count) -> add("web ticket for \"$service\" x$count") }
            }
            appendLine("Steam tickets asked by the game: ${parts.joinToString(", ")}; given to it: $given" + if (notGiven > 0) ", NOT given: $notGiven" else "")
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
                appendLine("  On a Pico, the system itself refuses a re-signed game whose package name is one the Pico store knows, before the game runs: com.bytedance.pico.matrix asks Pico's servers about every third-party application it sees, sending the package name and the signature of the installed APK, and force-stops the ones whose signature is not the one registered for that name (the dialog says the entitlement check failed, and offers the store).")
                appendLine("  It was seen on games whose manifest carries no Pico marker, so a game without the checks below can be refused as well. Installing it under a package name of GamePort's own (the package name choice of the game's compatibility page) takes it past that check.")
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
    private val DECISION = Regex("""GPXR\s*: (.*(?:nothing to translate|controls translated|taken for|present(?:ed)?=).*)""")

    /**
     * What the game asked of the controllers, read from the start of its log: the profiles it suggested (with how many controls and actions, and what the
     * runtime answered), the profile the system made active, and what GamePort's layer did about it. A game that has controls for the device's own controllers
     * is left alone by the layer; one that has none is translated. The three lines are what tells the two apart in a report.
     */
    private fun StringBuilder.appendControllers(startLog: String, choice: ControllerChoice?) {
        // What the player chose is told whatever the log holds: it is the setting, not a line of the run.
        choice?.let { appendLine("controllers, what the player chose: presented=${it.presented}, applied=${it.applied.ifEmpty { "not reported" }}") }
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
        expansion.allFilesAccess?.let { appendLine("access to all files given to GamePort: ${if (it) "yes" else "no (GamePort stops before installing a game whose expansion files it cannot place without it)"}") }
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

    /** A moment as the device wrote it, with the date: a report is read days after it was made, so "28 h ago" alone would not say when. */
    internal fun stamp(millis: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date(millis))

    /** A stretch of time in the unit a reader weighs it in: seconds, minutes, hours or days. */
    internal fun ago(millis: Long): String = when {
        millis < 120_000L -> "${millis / 1000} s"
        millis < 2 * 3_600_000L -> "${millis / 60_000L} min"
        millis < 2 * 86_400_000L -> "${millis / 3_600_000L} h"
        else -> "${millis / 86_400_000L} days"
    }

    private fun field(line: String, key: String): String = Regex("$key=(\\S+)").find(line)?.groupValues?.get(1).orEmpty()

    private const val STALE_SECONDS = 120L
    private const val FRAME_RATE_MIN_SECONDS = 10
    private const val SILENCE_SECONDS = 10.0
    private const val LONG_RUN_SECONDS = 20.0
}
