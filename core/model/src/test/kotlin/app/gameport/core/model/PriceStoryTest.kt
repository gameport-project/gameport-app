package app.gameport.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceStoryTest {
    private fun p(day: String, final: Long?, initial: Long?, end: Long? = null) = PricePoint(day, final, initial, end)

    @Test
    fun `the lowest price is the cheapest line and the day it began`() {
        val story = PriceStory.of(listOf(p("2025-01-01", 1499, 1499), p("2025-06-01", 599, 1499), p("2025-06-10", 1499, 1499)), "EUR")!!
        assertEquals(599L, story.lowestCents)
        assertEquals(LocalDate.of(2025, 6, 1), story.lowestFrom)
        assertEquals(1499L, story.regularCents)
    }

    @Test
    fun `the last sale is the latest line with a discount, ending the day the next line begins`() {
        val story = PriceStory.of(
            listOf(p("2025-01-01", 1499, 1499), p("2025-06-01", 599, 1499), p("2025-06-10", 1499, 1499), p("2025-11-01", 999, 1499), p("2025-11-08", 1499, 1499)),
            "EUR",
        )!!
        assertEquals(SalePeriod(33, 999, LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 8), ongoing = false), story.lastSale)
    }

    @Test
    fun `a sale that is on takes its end from the discount`() {
        // 1792688400 is 2026-10-22 in UTC.
        val story = PriceStory.of(listOf(p("2026-10-01", 1499, 1499), p("2026-10-05", 599, 1499, 1792688400)), "EUR")!!
        val sale = story.lastSale!!
        assertEquals(60, sale.discountPercent)
        assertTrue(sale.ongoing)
        assertEquals(LocalDate.of(2026, 10, 22), sale.to)
    }

    @Test
    fun `a game never on sale has no sale`() {
        assertNull(PriceStory.of(listOf(p("2025-01-01", 1499, 1499), p("2025-03-01", 1299, 1299)), "EUR")!!.lastSale)
    }

    @Test
    fun `no price and a game always free have no story`() {
        assertNull(PriceStory.of(emptyList(), "EUR"))
        assertNull(PriceStory.of(listOf(p("2025-01-01", 0, 0)), "EUR"))
        assertNull(PriceStory.of(listOf(p("2025-01-01", null, null)), "EUR"))
    }

    @Test
    fun `a line that says the game is not sold does not count as a price`() {
        val story = PriceStory.of(listOf(p("2025-01-01", 999, 999), p("2025-02-01", null, null)), "EUR")!!
        assertEquals(999L, story.lowestCents)
        assertEquals(2, story.lines)
    }

    private fun story(vararg points: PricePoint) = PriceStory.of(points.toList(), "EUR")!!
    private fun paid(final: Long, initial: Long) = StorePrice.Paid("EUR", final, initial, if (initial > final) ((initial - final) * 100 / initial).toInt() else 0)
    private val seen = story(p("2025-01-01", 1000, 1000), p("2025-06-01", 500, 1000), p("2025-06-10", 1000, 1000))

    @Test
    fun `the lowest price seen is the best verdict, and one within 15 percent of it is a great one`() {
        assertEquals(PriceVerdict.LOWEST, seen.verdict(paid(500, 1000)))
        assertEquals(PriceVerdict.GREAT, seen.verdict(paid(570, 1000)))
    }

    @Test
    fun `a sale within 35 percent of the lowest is good, a worse one is only fair`() {
        assertEquals(PriceVerdict.GOOD, seen.verdict(paid(670, 1000)))
        assertEquals(PriceVerdict.FAIR, seen.verdict(paid(800, 1000)))
    }

    @Test
    fun `at full price it is better to wait when sales happened, and it is usual when none did`() {
        assertEquals(PriceVerdict.WAIT, seen.verdict(paid(1000, 1000)))
        val never = story(p("2025-01-01", 1000, 1000), p("2025-03-01", 900, 900))
        assertEquals(PriceVerdict.USUAL, never.verdict(paid(1000, 1000)))
    }

    @Test
    fun `no verdict on a free game or with one line of history`() {
        assertNull(seen.verdict(StorePrice.Free))
        assertNull(PriceStory.of(listOf(p("2025-01-01", 1000, 1000)), "EUR")!!.verdict(paid(1000, 1000)))
    }

    @Test
    fun `the lowest price knows the share off it was a sale at`() {
        val sale = story(p("2025-01-01", 1000, 1000), p("2025-06-01", 250, 1000))
        assertEquals(75, sale.lowestDiscount)
        assertNull(story(p("2025-01-01", 1000, 1000), p("2025-03-01", 800, 800)).lowestDiscount)
    }
}
