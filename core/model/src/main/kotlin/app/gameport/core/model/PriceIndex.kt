package app.gameport.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the catalog repository's price index says (see its README): the countries it keeps prices for, each with its currency. There is one country for each currency
 * Steam bills in, so the question an app asks is which country's prices fit the account.
 */
class PriceIndex(val currencies: Map<String, String>) {
    /**
     * The country whose prices fit: the one that has the [currency] the account is billed in, if it is known; otherwise the one of the [region] (an ISO code such as
     * `FR`), the euro one for the euro zone, and the dollar one for the rest. The region of the device wins among the countries that share the currency.
     */
    fun countryFor(region: String?, currency: String? = null): String? {
        val code = region.orEmpty().lowercase()
        if (!currency.isNullOrBlank()) {
            val own = currencies.filterValues { it == currency.uppercase() }.keys
            if (code in own) return code
            own.firstOrNull()?.let { return it }
        }
        return when {
            code in currencies -> code
            code == "uk" -> "gb".takeIf { it in currencies }
            code in EURO_ZONE -> currencies.entries.firstOrNull { it.value == "EUR" }?.key
            else -> null
        } ?: "us".takeIf { it in currencies } ?: currencies.keys.firstOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val EURO_ZONE = setOf("de", "fr", "es", "it", "nl", "be", "at", "ie", "pt", "fi", "gr", "lu", "sk", "si", "ee", "lv", "lt", "mt", "cy", "hr")

        /** The index, or null when it cannot be read or is not a version of the format this app understands. */
        fun parse(text: String?): PriceIndex? = runCatching {
            val root = json.parseToJsonElement(text.orEmpty()).jsonObject
            if (root["v"]?.jsonPrimitive?.intOrNull != 1) return null
            val currencies = (root["currencies"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.content }
            currencies.takeIf { it.isNotEmpty() }?.let(::PriceIndex)
        }.getOrNull()
    }
}
