package app.gameport.core.model

/**
 * When the app asks the relay again for what it keeps on the device. Everything is asked when a page opens, and only if the copy is old enough: a copy that cannot be replaced (the relay
 * does not answer, the answer cannot be read) stays as it is.
 */
object RelayRefresh {
    /** The totals of the players' answers, which carry the names of the models: they change over days. */
    const val SUMMARY_MS = 24 * 60 * 60 * 1000L

    /** The list of incompatible games: it changes when the developer adds a game. */
    const val INCOMPATIBLE_MS = 60 * 60 * 1000L

    /** The details of a game (the lines of its reports), asked when its window opens: a player looks at them to see what others just wrote. */
    const val DETAILS_MS = 10 * 60 * 1000L

    /** What the developer set for each game. */
    const val GAME_DEFAULTS_MS = 24 * 60 * 60 * 1000L

    /** A debug build asks again after seconds, so what was just sent can be seen. */
    const val DEBUG_MS = 20 * 1000L

    /** Whether to ask the relay: nothing is kept yet ([copyAgeMs] null), or the copy is at least [maxAgeMs] old. */
    fun due(copyAgeMs: Long?, maxAgeMs: Long): Boolean = copyAgeMs == null || copyAgeMs < 0 || copyAgeMs >= maxAgeMs
}
