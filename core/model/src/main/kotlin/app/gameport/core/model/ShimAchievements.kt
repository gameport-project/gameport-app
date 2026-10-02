package app.gameport.core.model

/**
 * The two files GamePort bakes into a patched game for the Steamworks shim, as JSON text: what Steam holds about the game's
 * achievements, and which of them the account already unlocked. The texts are in English, the language the shim answers in.
 */
object ShimAchievements {
    /** The shim reads each baked file in one go and refuses one over a mebibyte; larger lists are left out rather than cut. */
    const val MAX_BYTES = 900_000

    /** The definitions, a JSON array in the format of the shim's `steam_settings/achievements.json` (all values are strings). */
    fun definitions(items: List<Achievement>): String? = items.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ",", prefix = "[", postfix = "]") { a ->
            "{" + listOf(
                "name" to a.name,
                "displayName" to a.title,
                "description" to a.description,
                "hidden" to if (a.hidden) "1" else "0",
                "icon" to a.icon.orEmpty(),
                "icon_gray" to a.iconGray.orEmpty(),
            ).joinToString(",") { (key, value) -> "\"$key\":\"${escape(value)}\"" } + "}"
        }
        ?.takeIf { it.toByteArray().size <= MAX_BYTES }

    /** The unlocked ones, a JSON object keyed by name, in the format of the shim's save file of unlocked achievements. */
    fun earned(items: List<Achievement>): String? = items.filter { it.unlocked }.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ",", prefix = "{", postfix = "}") { a ->
            "\"${escape(a.name)}\":{\"earned\":true,\"earned_time\":${a.unlockedAt.coerceAtLeast(0L)}}"
        }
        ?.takeIf { it.toByteArray().size <= MAX_BYTES }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }
}
