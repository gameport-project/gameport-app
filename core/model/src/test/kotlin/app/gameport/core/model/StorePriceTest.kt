package app.gameport.core.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StorePriceTest {
    @Test
    fun `the amount is written in the words of the country`() {
        val price = StorePrice.Paid("EUR", finalCents = 1950, initialCents = 1950, discountPercent = 0)
        assertEquals("19,50\u00a0\u20ac", price.finalText(Locale.FRANCE))
        assertEquals("\u20ac19.50", price.finalText(Locale.US))
    }

    @Test
    fun `the price before a discount is written the same way`() {
        val price = StorePrice.Paid("EUR", finalCents = 599, initialCents = 1499, discountPercent = 60)
        assertEquals("14,99\u00a0\u20ac", price.initialText(Locale.FRANCE))
    }
}
