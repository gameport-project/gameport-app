package app.gameport.core.sync

/** Reads the lines the game writes about how its runs ended (see `SessionLog` in the hook). */
object ExitReasons {
    private val crashReasons = listOf("reason=CRASH", "reason=CRASH_NATIVE", "reason=ANR", "reason=INITIALIZATION_FAILURE")

    /** SIGILL, SIGABRT, SIGBUS, SIGFPE, SIGSEGV. */
    private val faultSignals = setOf(4, 6, 7, 8, 11)

    /** True for a crash, a hang, or the system killing the game on a fault signal; a game quitting by itself is not one. */
    fun isCrash(line: String): Boolean {
        if (crashReasons.any { line.contains(it) }) return true
        val status = Regex("status=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
        return line.contains("reason=SIGNALED") && status in faultSignals
    }

    /** The name Android gives an end of process, from its number (the ones Android names after 13 are named here, as the hook names them). */
    fun name(reason: Int): String = when (reason) {
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "OTHER_$reason"
    }

    /** The lines with the time Android gave in milliseconds followed by the date and time on this device, as the player lived them. */
    fun withTimes(text: String, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.ROOT).apply { timeZone = zone }
        return text.lines().joinToString("\n") { line ->
            val millis = Regex("^time=(\\d{10,})").find(line)?.groupValues?.get(1)?.toLongOrNull()
            if (millis == null) line else line.replaceFirst("time=$millis", "time=$millis (${format.format(java.util.Date(millis))})")
        }
    }
}
