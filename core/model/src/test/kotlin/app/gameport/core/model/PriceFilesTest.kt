package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceFilesTest {
    private val index = PriceIndex.parse("""{"v":1,"updated":"2026-10-11","currencies":{"us":"USD","fr":"EUR","gb":"GBP","ch":"CHF"}}""")!!

    @Test
    fun `a device gets the prices of its own country, the euro ones in the euro zone, the dollar ones elsewhere`() {
        assertEquals("fr", index.countryFor("FR"))
        assertEquals("gb", index.countryFor("GB"))
        assertEquals("gb", index.countryFor("UK"))
        assertEquals("fr", index.countryFor("DE"))
        assertEquals("us", index.countryFor("JP"))
        assertEquals("us", index.countryFor(null))
    }

    @Test
    fun `the currency of the account wins over the region of the device`() {
        assertEquals("gb", index.countryFor("FR", "GBP"))
        assertEquals("fr", index.countryFor("FR", "EUR"))
        assertEquals("ch", index.countryFor("FR", "CHF"))
        assertEquals("fr", index.countryFor("FR", "ZZZ"))
    }

    @Test
    fun `an index of another version, or that is not one, is refused`() {
        assertNull(PriceIndex.parse(null))
        assertNull(PriceIndex.parse("""{"v":2,"currencies":{"us":"USD"}}"""))
        assertNull(PriceIndex.parse("""{"v":1,"currencies":{}}"""))
    }

    private val now = PriceNow.parse("""{"v":1,"country":"fr","currency":"EUR","prices":{"5":[1000,2000,1792688400],"6":[1950,1950],"7":[0,0]}}""")!!

    @Test
    fun `the prices of a country have the share off, and a zero price is free`() {
        assertEquals(StorePrice.Paid("EUR", 1000, 2000, 50), now.priceOf(5))
        assertEquals(StorePrice.Paid("EUR", 1950, 1950, 0), now.priceOf(6))
        assertEquals(StorePrice.Free, now.priceOf(7))
        assertNull(now.priceOf(99))
        assertEquals(3, now.all().size)
    }

    @Test
    fun `a file of prices of another version is refused`() {
        assertNull(PriceNow.parse("""{"v":2,"country":"fr","currency":"EUR","prices":{}}"""))
        assertNull(PriceNow.parse("<html>"))
    }

    @Test
    fun `the history of a game has its lines by country, with a line that says it is no longer sold`() {
        val history = PriceHistory.parse("""{"v":1,"appid":5,"lines":{"fr":[["2026-10-01",1000,2000,1792688400],["2026-10-09",null,null]]}}""")!!
        val lines = history.getValue("fr")
        assertEquals(PricePoint("2026-10-01", 1000, 2000, 1792688400), lines[0])
        assertEquals(PricePoint("2026-10-09", null, null), lines[1])
        assertNull(PriceHistory.parse("""{"v":3,"lines":{}}"""))
    }
}
