package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMeterTest {
    private var clock = 0L
    private val meter = SpeedMeter({ clock })

    private fun at(time: Long, received: Long): Long {
        clock = time
        return meter.update(received)
    }

    @Test
    fun `nothing is shown before a second of history`() {
        assertEquals(0, at(0, 0))
        assertEquals(0, at(500, 2_500_000))
    }

    @Test
    fun `a steady rate is reported as it is`() {
        at(0, 0)
        assertEquals(5_000_000, at(1_000, 5_000_000))
        assertEquals(5_000_000, at(3_000, 15_000_000))
    }

    @Test
    fun `bytes arriving in bursts give the same figure as the same bytes arriving steadily`() {
        // 10 MB in the first second and nothing in the next four: 2 MB/s over five seconds, however the bytes came.
        at(0, 0)
        at(1_000, 10_000_000)
        assertEquals(2_000_000, at(5_000, 10_000_000))
    }

    @Test
    fun `the figure falls to zero when nothing arrives, it does not stay at the last value`() {
        at(0, 0)
        at(1_000, 8_000_000)
        at(2_000, 16_000_000)
        // Nothing for longer than the window: the old bytes are out of it.
        at(6_000, 16_000_000)
        assertEquals(0, at(13_000, 16_000_000))
    }

    @Test
    fun `only the last seconds count, an earlier fast stretch does not`() {
        at(0, 0)
        at(1_000, 30_000_000)
        at(10_000, 30_000_000)
        // 2 MB in the last 2 seconds: 1 MB/s, not the 30 MB/s of the stretch long before.
        assertEquals(1_000_000, at(12_000, 32_000_000))
    }

    @Test
    fun `asking again without new bytes is harmless`() {
        at(0, 0)
        at(1_000, 5_000_000)
        val a = at(1_100, 5_000_000)
        val b = at(1_200, 5_000_000)
        assertTrue(b <= a)
    }
}
