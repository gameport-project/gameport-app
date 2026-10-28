package app.gameport.core.sync

/**
 * An upload to Steam Cloud that Steam can refuse for a name it no longer accepts for the game's saves, which it does not say. The name Steam describes for the game is
 * the first tried; when it is refused, the next name of each file is tried, up to [MAX_ROUNDS] times in all. Nothing is learnt from a refusal: the first name is tried first again next time.
 */
internal object UploadFallback {
    const val MAX_ROUNDS = 3

    /** [change] is the change number Steam gave, [names] the name sent for each file, in the order of the files. */
    class Result(val change: Long, val round: Int, val names: List<String>)

    /**
     * [names] gives, for each file, the names it can be sent under, best first (none of the lists is empty). [attempt] sends the chosen name for each file and answers the change
     * number, or null when Steam refused. A round that would send what the one before did is not tried again.
     */
    suspend fun run(names: List<List<String>>, attempt: suspend (List<String>) -> Long?): Result? {
        var previous: List<String>? = null
        for (round in 0 until MAX_ROUNDS) {
            val chosen = names.map { it.getOrElse(round) { _ -> it.last() } }
            if (chosen == previous) return null
            previous = chosen
            attempt(chosen)?.let { return Result(it, round, chosen) }
        }
        return null
    }
}
