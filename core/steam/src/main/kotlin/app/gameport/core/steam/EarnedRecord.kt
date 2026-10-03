package app.gameport.core.steam

import app.gameport.core.model.Achievement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The record where the Steamworks shim keeps the achievements a game unlocked: a JSON object keyed by the name the game uses,
 * `{"name": {"earned": true, "earned_time": seconds}}`. [merge] adds to it what the Steam account has, so a game starts knowing the
 * achievements earned elsewhere (on a PC, for example) and does not unlock them again.
 */
object EarnedRecord {
    /**
     * [current] is the record as the game has it (empty when there is none yet), [steam] the achievements of the account. Returns the
     * record with the account's unlocked achievements added, or null when there is nothing to add or [current] cannot be read, in which
     * case the file is left as it is. Nothing in [current] is changed or removed: an achievement the game has stays, whatever Steam says.
     */
    fun merge(current: String, steam: List<Achievement>): String? {
        val existing: Map<String, kotlinx.serialization.json.JsonElement> = when {
            current.isBlank() -> emptyMap()
            else -> runCatching { Json.parseToJsonElement(current).jsonObject }.getOrNull() ?: return null
        }
        val byLowerCase = existing.keys.associateBy { it.lowercase() }
        val added = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        for (achievement in steam) {
            if (!achievement.unlocked) continue
            val key = byLowerCase[achievement.name.lowercase()]
            val already = key?.let { (existing[it] as? JsonObject)?.get("earned")?.let { flag -> runCatching { (flag as JsonPrimitive).boolean }.getOrDefault(false) } } == true
            if (already) continue
            // 1 means Steam has the achievement without a time.
            val time = achievement.unlockedAt.takeIf { it > 1L } ?: 0L
            added[key ?: achievement.name] = buildJsonObject {
                put("earned", true)
                put("earned_time", time)
            }
        }
        if (added.isEmpty()) return null
        return JsonObject(existing + added).toString()
    }
}
