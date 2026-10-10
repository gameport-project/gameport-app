package app.gameport.feature.compat

import app.gameport.core.model.CompatCounts
import app.gameport.core.model.DeviceCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class CompatFilterTest {
    private fun entry(works: Int, offlineOnly: Int, fails: Int) = CompatEntry(
        CompatCounts(1, works, fails, offlineOnly = offlineOnly, devices = mapOf("quest" to DeviceCounts(works, fails, offlineOnly = offlineOnly))),
        name = "Game",
        inLibrary = false,
    )

    @Test
    fun `no filter lets every game through`() {
        assertEquals(true, matches(entry(0, 0, 0), CompatFilter.ALL, "quest"))
    }

    @Test
    fun `a game that works, even only offline, is on the side of what works`() {
        assertEquals(true, matches(entry(4, 0, 0), CompatFilter.WORKS, "quest"))
        assertEquals(true, matches(entry(0, 4, 0), CompatFilter.WORKS, "quest"))
        assertEquals(false, matches(entry(4, 0, 0), CompatFilter.FAILS, "quest"))
    }

    @Test
    fun `a game that fails is on the other side`() {
        assertEquals(true, matches(entry(1, 0, 3), CompatFilter.FAILS, "quest"))
        assertEquals(false, matches(entry(1, 0, 3), CompatFilter.WORKS, "quest"))
    }

    @Test
    fun `too few answers or divided opinions are on neither side`() {
        assertEquals(false, matches(entry(1, 0, 1), CompatFilter.WORKS, "quest"))
        assertEquals(false, matches(entry(1, 0, 1), CompatFilter.FAILS, "quest"))
        assertEquals(false, matches(entry(2, 0, 2), CompatFilter.WORKS, "quest"))
        assertEquals(false, matches(entry(2, 0, 2), CompatFilter.FAILS, "quest"))
        // Answers of another kind of device do not count for this one.
        assertEquals(false, matches(entry(4, 0, 0), CompatFilter.WORKS, "pico"))
    }
}
