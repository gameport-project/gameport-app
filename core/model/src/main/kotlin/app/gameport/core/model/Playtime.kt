package app.gameport.core.model

/** How long a game was played: on this device (counted by GamePort) and in total on the Steam account (null until Steam answered). */
data class Playtime(
    val deviceMillis: Long = 0L,
    val steamMinutes: Int? = null,
)

/**
 * Counts the time a game is really on screen. The game says when it comes to the screen, regularly
 * while it stays, and when it leaves; time without a sign (a device asleep, a game stuck behind) is
 * never counted: each sign adds at most [maxStepMillis] since the previous one.
 */
class PlaytimeMeter(
    private val maxStepMillis: Long = 45_000L,
    private val staleAfterMillis: Long = 100_000L,
) {
    private var lastSign = 0L
    var running = false
        private set

    /** The game came to the screen: a new stretch starts, nothing is added. */
    fun resumed(now: Long) {
        running = true
        lastSign = now
    }

    /** A sign of life while on screen; returns the milliseconds to add. */
    fun alive(now: Long): Long {
        if (!running) {
            resumed(now)
            return 0L
        }
        return step(now)
    }

    /** The game left the screen; returns the milliseconds to add. */
    fun paused(now: Long): Long {
        if (!running) return 0L
        val added = step(now)
        running = false
        return added
    }

    /** Running, but no sign for a long time: the game is gone without saying so. */
    fun isStale(now: Long): Boolean = running && now - lastSign > staleAfterMillis

    /** Ends a stale stretch where it was last seen; nothing is added for the silence. */
    fun drop() {
        running = false
    }

    private fun step(now: Long): Long {
        val added = (now - lastSign).coerceIn(0L, maxStepMillis)
        lastSign = now
        return added
    }
}
