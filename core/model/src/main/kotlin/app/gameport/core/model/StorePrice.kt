package app.gameport.core.model

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** What Steam sells a game for in one country, as the history the catalog keeps says (see [PriceNow]). */
sealed interface StorePrice {
    data object Free : StorePrice

    /** Amounts in hundredths of [currency] (an ISO code such as `EUR`); [initialCents] is the price before a discount. */
    data class Paid(val currency: String, val finalCents: Long, val initialCents: Long, val discountPercent: Int) : StorePrice {
        /** The amount in the words of [locale]: "19,50 €" in France, "$19.50" in the United States. */
        fun format(cents: Long, locale: Locale = Locale.getDefault()): String {
            val format = NumberFormat.getCurrencyInstance(locale)
            runCatching { format.currency = Currency.getInstance(currency) }
            return format.format(cents / 100.0)
        }

        fun finalText(locale: Locale = Locale.getDefault()) = format(finalCents, locale)

        fun initialText(locale: Locale = Locale.getDefault()) = format(initialCents, locale)
    }
}
