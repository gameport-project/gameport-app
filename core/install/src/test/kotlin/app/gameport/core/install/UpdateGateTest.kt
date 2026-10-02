package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGateTest {
    @Test
    fun `nothing blocks an idle app`() {
        assertNull(UpdateGate().currentBlocker())
    }

    @Test
    fun `games at work block the update`() {
        val gate = UpdateGate().apply { setGamesBusy(true) }
        assertEquals(UpdateGate.Blocker.GAMES_BUSY, gate.currentBlocker())
        gate.setGamesBusy(false)
        assertNull(gate.currentBlocker())
    }

    @Test
    fun `a running update is visible to the games`() {
        val gate = UpdateGate().apply { setUpdating(true) }
        assertTrue(gate.updating.value)
    }
}
