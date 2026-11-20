package app.gameport.core.model

/** What a run of a game, once it is over, says of the problem noted for the game before it. */
object ProblemRules {
    /** A run shorter than this closed too fast: a sign of trouble. */
    const val SHORT_SESSION_MS = 30_000L

    /** A run at least this long shows the game works: the problem noted before it is cleared. */
    const val WORKED_MS = 60_000L

    enum class AfterRun { NOTE_SHORT_SESSION, CLEAR, NOTHING }

    /** [lastedMs] is how long the run lasted, [problemNoted] whether a problem is noted for the game. A crash of this run is noted at the next start, and comes back then. */
    fun afterRun(lastedMs: Long, problemNoted: Boolean): AfterRun = when {
        lastedMs < SHORT_SESSION_MS -> AfterRun.NOTE_SHORT_SESSION
        lastedMs >= WORKED_MS && problemNoted -> AfterRun.CLEAR
        else -> AfterRun.NOTHING
    }
}
