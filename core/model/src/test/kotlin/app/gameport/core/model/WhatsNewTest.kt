package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    @Test
    fun `a player coming from the version before gets what the new one brings`() {
        val summary = WhatsNew.between(seen = 600, current = 700)
        assertEquals(listOf(WhatsNew.Item.ACHIEVEMENTS, WhatsNew.Item.OFFLINE, WhatsNew.Item.SIGN_IN), summary.items)
        assertTrue("Moss 2" in summary.confirmedGames)
    }

    @Test
    fun `a player who already saw the version is told nothing more`() {
        assertTrue(WhatsNew.between(seen = 700, current = 700).isEmpty)
        assertTrue(WhatsNew.between(seen = 800, current = 700).isEmpty)
    }

    @Test
    fun `a version that is not out yet is not announced`() {
        assertTrue(WhatsNew.between(seen = 500, current = 600).isEmpty)
    }

    @Test
    fun `several versions skipped are told together without repeats`() {
        val games = WhatsNew.between(seen = 0, current = 900).confirmedGames
        assertEquals(games.distinct(), games)
        assertEquals(5, games.size)
    }

    @Test
    fun `the latest version is named like a version`() {
        assertEquals("0.7.0", WhatsNew.latestName)
    }
}
