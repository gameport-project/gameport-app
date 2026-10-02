package app.gameport.core.steam.session

import app.gameport.core.model.Achievement
import `in`.dragonbra.javasteam.types.KeyValue

private fun KeyValue.child(name: String): KeyValue? = children.firstOrNull { it.name.equals(name, ignoreCase = true) }

/**
 * A text of the schema in [language]. A node with sub-keys holds one text per language; a node with only a value holds
 * the same text for every language. When the language is missing it falls back to English, then to the first one there is.
 */
private fun KeyValue?.text(language: String): String {
    if (this == null) return ""
    if (children.isEmpty()) return value.orEmpty()
    return (child(language) ?: child("english") ?: children.first()).value.orEmpty()
}

/**
 * The achievements a game's schema describes, in the order of the schema (block, then bit). The schema says what exists;
 * [unlockTime] says when the account unlocked the one at [bit] of [statId], 0 when it did not (or has no record at all,
 * as for a game the account never played on Steam: its achievements are still listed, locked).
 */
internal fun parseAchievements(schema: KeyValue, language: String, unlockTime: (statId: Int, bit: Int) -> Long): List<Achievement> {
    // The node of the app holds the stats; the root may be the node itself or one level above it.
    val stats = schema.child("stats") ?: schema.children.firstNotNullOfOrNull { it.child("stats") } ?: return emptyList()
    val found = mutableListOf<Triple<Int, Int, Achievement>>()
    for (block in stats.children) {
        val statId = block.name?.toIntOrNull() ?: continue
        val bits = block.child("bits") ?: continue
        for (bit in bits.children) {
            val index = bit.name?.toIntOrNull() ?: continue
            val name = bit.child("name")?.value?.takeIf { it.isNotEmpty() } ?: continue
            val display = bit.child("display")
            val at = unlockTime(statId, index)
            found += Triple(
                statId,
                index,
                Achievement(
                    name = name,
                    title = display?.child("name").text(language).ifBlank { name },
                    description = display?.child("desc").text(language),
                    icon = display?.child("icon")?.value?.takeIf { it.isNotEmpty() },
                    iconGray = display?.child("icon_gray")?.value?.takeIf { it.isNotEmpty() },
                    hidden = display?.child("hidden")?.value == "1",
                    unlocked = at > 0,
                    unlockedAt = at.coerceAtLeast(0L),
                ),
            )
        }
    }
    return found.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
}
