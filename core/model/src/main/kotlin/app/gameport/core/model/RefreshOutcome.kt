package app.gameport.core.model

/**
 * What came of the frame rate asked for a game, as the OpenXR layer saw it in the last run: the rate asked, the runtime's answer (negative is a refusal), the rates the runtime
 * accepts, the rate the game asked for on its own (0 when it did not), and the rate the frames really came at (0 when not measured yet).
 */
data class RefreshOutcome(val asked: Int, val result: Int, val accepted: List<Int>, val gameAsked: Int, val observed: Int) {
    enum class Verdict { KEPT, REFUSED, GAME_OWN, NOT_KEPT }

    /** Null when no rate was asked for. The runtime's refusal comes first, then a game that sets its own rate, then frames that come at another rate than the one asked. */
    val verdict: Verdict?
        get() = when {
            asked <= 0 -> null
            result < 0 -> Verdict.REFUSED
            gameAsked > 0 && gameAsked != asked -> Verdict.GAME_OWN
            observed > 0 && kotlin.math.abs(observed - asked) > TOLERANCE -> Verdict.NOT_KEPT
            else -> Verdict.KEPT
        }

    /**
     * Whether the rate chosen for the game is taken off it: the runtime refused it and does not list it among the rates it accepts, and the choice is still the one that was asked. A game
     * [needsPatchInstead] (a Quest game that does not say which Quests it is made for) keeps the choice: the patch makes the runtime accept it.
     */
    fun shouldTakeOff(currentChoice: Int, needsPatchInstead: Boolean): Boolean =
        verdict == Verdict.REFUSED && accepted.isNotEmpty() && asked !in accepted && currentChoice == asked && !needsPatchInstead

    companion object {
        /** Frames per second of difference allowed between the rate asked for and the one measured: the measure is rounded and the display period is announced by the runtime. */
        const val TOLERANCE = 3

        /** What the layer wrote ("asked=80", "result=-1", "accepted=60,72,90", "gameasked=0", "observed=38" lines), or null when it is not a report. */
        fun parse(text: String?): RefreshOutcome? {
            val values = text?.lineSequence()?.mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }?.toMap() ?: return null
            val asked = values["asked"]?.toIntOrNull() ?: return null
            return RefreshOutcome(
                asked = asked,
                result = values["result"]?.toIntOrNull() ?: 0,
                accepted = values["accepted"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
                gameAsked = values["gameasked"]?.toIntOrNull() ?: 0,
                observed = values["observed"]?.toIntOrNull() ?: 0,
            )
        }
    }
}

/** A frame rate GamePort took off a game because the device's runtime does not accept it: the rate that was chosen, and the ones the runtime does accept. */
data class RateReset(val asked: Int, val accepted: List<Int>) {
    /** Written as "asked:72,90" to be kept between two runs of GamePort. */
    fun write(): String = "$asked:${accepted.joinToString(",")}"

    companion object {
        fun parse(text: String?): RateReset? {
            val parts = text?.split(':', limit = 2) ?: return null
            val asked = parts.getOrNull(0)?.toIntOrNull() ?: return null
            return RateReset(asked, parts.getOrNull(1).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() })
        }
    }
}
