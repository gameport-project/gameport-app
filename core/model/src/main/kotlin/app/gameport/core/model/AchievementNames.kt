package app.gameport.core.model

/** What to call an achievement when only the name the game gave to Steam is known. */
object AchievementNames {
    private val prefixes = listOf("achievement_", "achiev_", "ach_")

    /** `ach_ready_to_rock` is shown as "Ready To Rock". */
    fun readable(name: String): String {
        var text = name.trim()
        prefixes.firstOrNull { text.startsWith(it, ignoreCase = true) }?.let { text = text.substring(it.length) }
        return text.split('_', '-', ' ').filter { it.isNotEmpty() }.joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
            .ifEmpty { name }
    }
}
