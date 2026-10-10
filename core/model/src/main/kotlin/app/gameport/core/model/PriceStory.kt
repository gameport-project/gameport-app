package app.gameport.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A time a game was on sale: the share off, the price then, from the day it began to the day it ended; [to] is null while it lasts and its end is not known. */
data class SalePeriod(val discountPercent: Int, val priceCents: Long, val from: LocalDate, val to: LocalDate?, val ongoing: Boolean)

/**
 * What the history of the price of a game tells, for its page: the lowest price seen and the day it began, the usual price, and the last time the game was on
 * sale. [lines] is how many lines the history has: with one, nothing has changed yet since the history began on [since].
 */
data class PriceStory(
    val currency: String,
    val lowestCents: Long,
    val lowestFrom: LocalDate,
    /** The share off at the lowest price, when it was a sale. */
    val lowestDiscount: Int?,
    val regularCents: Long,
    val lastSale: SalePeriod?,
    val since: LocalDate,
    val lines: Int,
) {
    companion object {
        /** The story of a history, or null when it has no price (nothing read yet, no longer sold) or the game was always free. */
        fun of(history: List<PricePoint>, currency: String): PriceStory? {
            val priced = history.filter { it.finalCents != null }
            if (priced.isEmpty() || priced.all { it.finalCents == 0L }) return null
            val lowest = priced.minByOrNull { it.finalCents!! }!!
            val sales = history.mapIndexedNotNull { i, line ->
                val final = line.finalCents ?: return@mapIndexedNotNull null
                val initial = line.initialCents ?: return@mapIndexedNotNull null
                if (initial <= final) return@mapIndexedNotNull null
                val last = i == history.lastIndex
                val end = when {
                    !last -> LocalDate.parse(history[i + 1].day)
                    line.endsAt != null -> Instant.ofEpochSecond(line.endsAt).atZone(ZoneOffset.UTC).toLocalDate()
                    else -> null
                }
                SalePeriod(((initial - final) * 100 / initial).toInt(), final, LocalDate.parse(line.day), end, ongoing = last)
            }
            val now = priced.last()
            return PriceStory(
                currency = currency,
                lowestCents = lowest.finalCents!!,
                lowestFrom = LocalDate.parse(lowest.day),
                lowestDiscount = lowest.initialCents?.takeIf { it > lowest.finalCents!! }?.let { ((it - lowest.finalCents!!) * 100 / it).toInt() },
                regularCents = maxOf(now.initialCents ?: 0, now.finalCents!!),
                lastSale = sales.lastOrNull(),
                since = LocalDate.parse(history.first().day),
                lines = history.size,
            )
        }
    }

    /**
     * Whether the price now is worth it, by what the history has seen: [PriceVerdict.LOWEST] when it is the lowest seen, [PriceVerdict.GREAT] within 15 % of it,
     * [PriceVerdict.GOOD] on sale within 35 % of it, [PriceVerdict.FAIR] on sale but seen lower, [PriceVerdict.WAIT] at full price while sales happened, and
     * [PriceVerdict.USUAL] at full price when none did. Null when there is no price to judge or too little history (one line).
     */
    fun verdict(now: StorePrice?): PriceVerdict? {
        val paid = now as? StorePrice.Paid ?: return null
        if (lines < 2) return null
        val price = paid.finalCents
        val onSale = paid.discountPercent > 0
        return when {
            price <= lowestCents -> PriceVerdict.LOWEST
            // Full price, and no sale ever seen: nothing to compare with, it is the usual price.
            !onSale && lastSale == null -> PriceVerdict.USUAL
            price * 100 <= lowestCents * 115 -> PriceVerdict.GREAT
            onSale && price * 100 <= lowestCents * 135 -> PriceVerdict.GOOD
            onSale -> PriceVerdict.FAIR
            lastSale != null -> PriceVerdict.WAIT
            else -> PriceVerdict.USUAL
        }
    }

    /** An amount of this story's currency, as a price. */
    fun amount(cents: Long) = StorePrice.Paid(currency, cents, cents, 0).finalText()
}
