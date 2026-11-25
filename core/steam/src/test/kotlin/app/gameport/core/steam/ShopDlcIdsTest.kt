package app.gameport.core.steam

import org.junit.Assert.assertEquals
import org.junit.Test

class ShopDlcIdsTest {
    @Test
    fun `the extra content of a game is read in the order the shop lists it`() {
        assertEquals(listOf(11, 12), shopDlcIds("""{"10":{"success":true,"data":{"dlc":[11,12]}}}""", 10))
    }

    @Test
    fun `a game with no extra content, or an answer that says nothing, gives none`() {
        assertEquals(emptyList<Int>(), shopDlcIds("""{"10":{"success":true,"data":{"name":"A"}}}""", 10))
        assertEquals(emptyList<Int>(), shopDlcIds("""{"10":{"success":false}}""", 10))
        assertEquals(emptyList<Int>(), shopDlcIds("<html></html>", 10))
        assertEquals(emptyList<Int>(), shopDlcIds("""{"99":{"success":true,"data":{"dlc":[1]}}}""", 10))
    }
}
