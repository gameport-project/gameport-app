package app.gameport.core.model

import kotlinx.serialization.Serializable

/**
 * How steadily a run of a game went, as the hook of GamePort counted it: the seconds that count (the session in focus, after its first minute), how many of them held the frame rate the screen
 * asked for, and that frame rate. It goes to the project with the answer only when the player agrees, after seeing it; nothing in it says who played, or what.
 */
@Serializable
data class Steadiness(val seconds: Int, val held: Int, val target: Int) {
    /** The share of the counted seconds that held the frame rate, from 0 to 100. */
    val percent: Int get() = if (seconds > 0) held * 100 / seconds else 0

    /** What the relay takes: enough play to say, and numbers that fit together. */
    val usable: Boolean get() = seconds >= MIN_SECONDS && held in 0..seconds && target in MIN_TARGET..MAX_TARGET

    /** Written as three numbers separated by spaces, to be kept between two runs of GamePort. */
    fun write(): String = "$seconds $held $target"

    companion object {
        /** Under this much play the figure says too little (the relay asks for the same). */
        const val MIN_SECONDS = 180
        private const val MIN_TARGET = 24
        private const val MAX_TARGET = 240

        /** What [write] made, or null when the text is not three numbers. */
        fun parse(text: String?): Steadiness? {
            val parts = text?.trim()?.split(' ')?.mapNotNull { it.toIntOrNull() } ?: return null
            return if (parts.size == 3) Steadiness(parts[0], parts[1], parts[2]) else null
        }
    }
}
