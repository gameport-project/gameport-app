package app.gameport.core.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPolicyTest {
    @Test
    fun `a lost connection is retried with growing pauses`() {
        assertEquals(listOf(3_000L, 10_000L, 30_000L, 60_000L, 120_000L), reconnectPauses(replaced = false))
    }

    @Test
    fun `a session taken by another logon is left to it for a long while`() {
        assertEquals(listOf(REPLACED_PAUSE_MS), reconnectPauses(replaced = true))
        assertTrue(REPLACED_PAUSE_MS >= 60_000L)
    }

    @Test
    fun `the session is left alone only for a while after it was taken`() {
        assertFalse(recentlyReplaced(now = 1_000_000L, replacedAt = 0L))
        assertTrue(recentlyReplaced(now = 1_000_000L, replacedAt = 1_000_000L - REPLACED_PAUSE_MS + 1))
        assertFalse(recentlyReplaced(now = 1_000_000L, replacedAt = 1_000_000L - REPLACED_PAUSE_MS))
    }
}
