package app.gameport.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionKeeperTest {
    private val grace = 5_000L

    @Test
    fun `GamePort is quit once nothing uses it for the grace`() {
        assertTrue(isQuit(inUse = false, quitForMs = grace, graceMs = grace))
    }

    @Test
    fun `a screen or a game running means it is not quit`() {
        assertFalse(isQuit(inUse = true, quitForMs = 10 * grace, graceMs = grace))
    }

    @Test
    fun `a dialog of the system in front for a moment is not leaving`() {
        assertFalse(isQuit(inUse = false, quitForMs = grace - 1, graceMs = grace))
    }

    @Test
    fun `the connection is closed when quit and nothing is still being patched or synced`() {
        assertTrue(shouldRelease(quit = true, working = false))
        assertFalse(shouldRelease(quit = true, working = true))
        assertFalse(shouldRelease(quit = false, working = false))
    }
}
