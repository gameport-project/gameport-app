package app.gameport.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** What each game costs now in one country: the file of the catalog repository that the lists read. A game it does not have is not sold there. */
class PriceNow(val country: String, val currency: String, private val prices: Map<Int, StorePrice>) {
    fun priceOf(appId: Int): StorePrice? = prices[appId]

    /** Every price of the country, by app id. */
    fun all(): Map<Int, StorePrice> = prices

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The file, or null when it cannot be read or is not a version of the format this app understands. */
        fun parse(text: String?): PriceNow? = runCatching {
            val root = json.parseToJsonElement(text.orEmpty()).jsonObject
            if (root["v"]?.jsonPrimitive?.intOrNull != 1) return null
            val currency = root["currency"]?.jsonPrimitive?.content ?: return null
            val prices = (root["prices"] as? JsonObject).orEmpty().entries.associate { (id, line) -> id.toInt() to price(currency, line.jsonArray) }
            PriceNow(root["country"]?.jsonPrimitive?.content ?: return null, currency, prices)
        }.getOrNull()

        private fun price(currency: String, line: JsonArray): StorePrice {
            val final = line[0].takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull ?: 0L
            val initial = line.getOrNull(1)?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull ?: final
            if (final == 0L) return StorePrice.Free
            val discount = if (initial > final) ((initial - final) * 100 / initial).toInt() else 0
            return StorePrice.Paid(currency, final, maxOf(initial, final), discount)
        }
    }
}
