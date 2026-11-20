package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayRefreshTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun `the durations are the ones agreed`() {
        assertEquals(24 * hour, RelayRefresh.SUMMARY_MS)
        assertEquals(1 * hour, RelayRefresh.INCOMPATIBLE_MS)
        assertEquals(10 * 60 * 1000L, RelayRefresh.DETAILS_MS)
        assertEquals(24 * hour, RelayRefresh.GAME_DEFAULTS_MS)
        assertEquals(20 * 1000L, RelayRefresh.DEBUG_MS)
    }

    @Test
    fun `nothing kept yet means the relay is asked`() {
        assertTrue(RelayRefresh.due(null, RelayRefresh.SUMMARY_MS))
        assertTrue(RelayRefresh.due(null, RelayRefresh.INCOMPATIBLE_MS))
    }

    @Test
    fun `a copy younger than the duration is kept without asking`() {
        assertFalse(RelayRefresh.due(0, RelayRefresh.INCOMPATIBLE_MS))
        assertFalse(RelayRefresh.due(59 * 60 * 1000L, RelayRefresh.INCOMPATIBLE_MS))
        assertFalse(RelayRefresh.due(23 * hour, RelayRefresh.SUMMARY_MS))
        // The old rule asked again after ten minutes: a copy of eleven minutes is now kept for the totals and the list.
        assertFalse(RelayRefresh.due(11 * 60 * 1000L, RelayRefresh.SUMMARY_MS))
        assertFalse(RelayRefresh.due(11 * 60 * 1000L, RelayRefresh.INCOMPATIBLE_MS))
    }

    @Test
    fun `a copy as old as the duration, or older, is asked again`() {
        assertTrue(RelayRefresh.due(hour, RelayRefresh.INCOMPATIBLE_MS))
        assertTrue(RelayRefresh.due(25 * hour, RelayRefresh.SUMMARY_MS))
        assertTrue(RelayRefresh.due(11 * 60 * 1000L, RelayRefresh.DETAILS_MS))
    }

    @Test
    fun `a copy dated in the future (a clock set back) is asked again`() {
        assertTrue(RelayRefresh.due(-5_000L, RelayRefresh.SUMMARY_MS))
    }

    @Test
    fun `the list from the relay keeps the codes of the devices, and an answer that cannot be read is nothing`() {
        val text = """{"v":1,"reasons":{"r":{"kind":"blocks","title":{"en":"t"},"text":{"en":"x"}}},"games":[{"appId":7,"name":"G","reason":"r","devices":["Pico 4"],"codes":["A8110","PICOA8110"]}]}"""
        val list = IncompatibleList.fromRelay(text)
        assertNotNull(list)
        assertEquals(listOf("A8110", "PICOA8110"), list!!.games.single().codes)
        // A list from a relay that does not send the codes yet is read too.
        val withoutCodes = text.replace(",\"codes\":[\"A8110\",\"PICOA8110\"]", "")
        assertEquals(emptyList<String>(), IncompatibleList.fromRelay(withoutCodes)!!.games.single().codes)
        assertNull(IncompatibleList.fromRelay("not json"))
        assertNull(IncompatibleList.fromRelay("""{"v":2,"reasons":{},"games":[]}"""))
    }
}
