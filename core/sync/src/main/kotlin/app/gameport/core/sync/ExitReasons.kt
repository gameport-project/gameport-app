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
}
