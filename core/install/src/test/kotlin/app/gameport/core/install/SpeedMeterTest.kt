package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMeterTest {
    private var clock = 0L

    @Test
    fun `reports the rate once a second has passed`() {
        val meter = SpeedMeter { clock }
        assertEquals(0, meter.update(0))
        clock = 1_000
        assertEquals(5_000_000, meter.update(5_000_000))
    }

    @Test
    fun `smooths a change in rate`() {
        val meter = SpeedMeter { clock }
        clock = 1_000
        meter.update(10_000_000)
        clock = 2_000
        val next = meter.update(10_000_000)
        assertTrue("fell from 10 MB/s to $next", next in 1..9_999_999)
    }
}
