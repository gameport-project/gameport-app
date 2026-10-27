package app.gameport.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the developer's console tells the app about the games, as the relay publishes it (see its README). For now one setting: the games that declare the headset's
 * passthrough ([passthrough]), which the app applies where the player left the choice on automatic. A setting this version does not know is left out, and so is a
 * value it does not know.
 */
data class GameDefaultsList(val passthrough: Set<Int> = emptySet()) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The relay's answer, or null when it is not one: a bad answer never replaces the list in use. */
        fun fromRelay(text: String?): GameDefaultsList? = runCatching {
            val root = json.parseToJsonElement(text.orEmpty()).jsonObject
            require(root.getValue("v").jsonPrimitive.int == 1)
            GameDefaultsList(
                passthrough = root.getValue("games").jsonObject.mapNotNull { (id, settings) ->
                    id.toIntOrNull()?.takeIf { settings.jsonObject["passthrough"]?.jsonPrimitive?.contentOrNull == "on" }
                }.toSet(),
            )
        }.getOrNull()
    }
}
