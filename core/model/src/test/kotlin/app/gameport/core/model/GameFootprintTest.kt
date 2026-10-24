package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFootprintTest {
    @Test
    fun `the three parts add up`() {
        assertEquals(1_000L + 2_000L + 300L, GameFootprint(appBytes = 1_000, expansionBytes = 2_000, keptBytes = 300).knownBytes)
    }

    @Test
    fun `an expansion that cannot be read adds nothing and says it is unknown`() {
        val unknown = GameFootprint(appBytes = 1_000, expansionBytes = null, keptBytes = 0)
        assertEquals(1_000L, unknown.knownBytes)
        assertTrue(unknown.expansionUnknown)
    }

    @Test
    fun `an expansion that is read and empty is known`() {
        val none = GameFootprint(appBytes = 1_000, expansionBytes = 0, keptBytes = 0)
        assertFalse(none.expansionUnknown)
    }

    @Test
    fun `the total counts every game and the ones with an unknown part`() {
        val total = listOf(
            GameFootprint(100, 50, 0),
            GameFootprint(200, null, 10),
            GameFootprint(300, null, 0),
        ).total()
        assertEquals(FootprintTotal(bytes = 100 + 50 + 200 + 10 + 300, withUnknownExpansion = 2), total)
    }

    @Test
    fun `no game weighs nothing`() {
        assertEquals(FootprintTotal(0, 0), emptyList<GameFootprint>().total())
    }

    @Test
    fun `a size noted at the install counts like a measured one and is told apart`() {
        val noted = GameFootprint(appBytes = 100, expansionBytes = 2_000, keptBytes = 0, expansionRecorded = true)
        assertEquals(2_100L, noted.knownBytes)
        assertFalse(noted.expansionUnknown)
        assertTrue(noted.expansionRecorded)
    }
}
