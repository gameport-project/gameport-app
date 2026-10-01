package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaytimeMeterTest {
    @Test
    fun `counts the time between signs while the game is on screen`() {
        val meter = PlaytimeMeter()
        meter.resumed(0)
        assertEquals(30_000L, meter.alive(30_000))
        assertEquals(30_000L, meter.alive(60_000))
        assertEquals(10_000L, meter.paused(70_000))
        assertFalse(meter.running)
    }

    @Test
    fun `a silence is never counted, so a device asleep adds nothing`() {
        val meter = PlaytimeMeter()
        meter.resumed(0)
        // The device slept for an hour without a sign: at most one step is added.
        assertEquals(45_000L, meter.alive(3_600_000))
    }

    @Test
    fun `coming back after a pause counts from the return, not from before`() {
        val meter = PlaytimeMeter()
        meter.resumed(0)
        meter.paused(20_000)
        meter.resumed(7_200_000)
        assertEquals(30_000L, meter.alive(7_230_000))
    }

    @Test
    fun `a pause without the game running adds nothing`() = assertEquals(0L, PlaytimeMeter().paused(1_000))

    @Test
    fun `a sign without a start begins a stretch and adds nothing`() {
        val meter = PlaytimeMeter()
        assertEquals(0L, meter.alive(5_000))
        assertTrue(meter.running)
        assertEquals(30_000L, meter.alive(35_000))
    }

    @Test
    fun `a game that stops talking is stale and its stretch is dropped`() {
        val meter = PlaytimeMeter()
        meter.resumed(0)
        assertFalse(meter.isStale(60_000))
        assertTrue(meter.isStale(101_000))
        meter.drop()
        assertFalse(meter.running)
        assertFalse(meter.isStale(500_000))
    }
}
