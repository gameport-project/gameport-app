package app.gameport.core.install

/**
 * Which installed apps are asked to close before a game starts, so the game has more memory.
 * Only apps the user installed are asked: the system, the headset's own apps and GamePort stay as they are.
 */
object BackgroundTrim {
    /** An installed app: its package name, and whether it ships with the system (an updated system app counts as one). */
    data class Candidate(val packageName: String, val isSystem: Boolean)

    private val KEPT_PREFIXES = listOf("app.gameport", "com.oculus.", "com.meta.", "com.pico.", "com.picovr.")

    /** The package names to ask to close, leaving out the ones in [keep] (the game about to start, GamePort). */
    fun targets(candidates: List<Candidate>, keep: Set<String>): List<String> = candidates
        .filter { !it.isSystem && it.packageName !in keep && KEPT_PREFIXES.none { prefix -> it.packageName.startsWith(prefix) } }
        .map { it.packageName }
}
