package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Test

class PatchAllOrderTest {
    @Test
    fun `the games that update without asking go first, each group in the order it came`() {
        val silent = setOf(30, 10, 40)
        assertEquals(listOf(10, 30, 40, 5, 20), PatchAllOrder.silentFirst(listOf(5, 10, 20, 30, 40)) { it in silent })
    }

    @Test
    fun `nothing changes when all of them ask, or none does`() {
        assertEquals(listOf(3, 1, 2), PatchAllOrder.silentFirst(listOf(3, 1, 2)) { false })
        assertEquals(listOf(3, 1, 2), PatchAllOrder.silentFirst(listOf(3, 1, 2)) { true })
        assertEquals(emptyList<Int>(), PatchAllOrder.silentFirst(emptyList()) { true })
    }
}
