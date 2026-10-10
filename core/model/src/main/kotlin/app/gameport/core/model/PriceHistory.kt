package app.gameport.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * One line of the history of a price: the day it was first seen, the price, and the price before a discount (the same when there is none), in hundredths. Both are
 * null on the line that says the game is no longer sold there. [endsAt] is the Unix time a discount ends, while one is on.
 */
data class PricePoint(val day: String, val finalCents: Long?, val initialCents: Long?, val endsAt: Long? = null)

/** The file of one game in the catalog repository: its lines in every country, oldest first. */
object PriceHistory {
    private val json = Json { ignoreUnknownKeys = true }

    /** The lines by country, or null when the file cannot be read or is not a version of the format this app understands. */
    fun parse(text: String?): Map<String, List<PricePoint>>? = runCatching {
        val root = json.parseToJsonElement(text.orEmpty()).jsonObject
        if (root["v"]?.jsonPrimitive?.intOrNull != 1) return null
        (root["lines"] as? JsonObject).orEmpty().mapValues { (_, lines) ->
            lines.jsonArray.map { line ->
                val parts = line.jsonArray
                PricePoint(
                    parts[0].jsonPrimitive.content,
                    parts[1].takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull,
                    parts[2].takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull,
                    parts.getOrNull(3)?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull,
                )
            }
        }
    }.getOrNull()
}
