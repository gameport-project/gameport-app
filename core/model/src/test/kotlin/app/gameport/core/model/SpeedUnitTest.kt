package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedUnitTest {
    @Test
    fun `megabytes per second is bytes over a million`() {
        assertEquals(12.5, SpeedUnit.MEGABYTES_PER_SECOND.valueOf(12_500_000), 0.0001)
    }

    @Test
    fun `megabits per second is eight times as many`() {
        assertEquals(100.0, SpeedUnit.MEGABITS_PER_SECOND.valueOf(12_500_000), 0.0001)
    }

    @Test
    fun `french speakers start with bytes and everyone else with bits`() {
        assertEquals(SpeedUnit.MEGABYTES_PER_SECOND, defaultSpeedUnit("fr"))
        assertEquals(SpeedUnit.MEGABYTES_PER_SECOND, defaultSpeedUnit("fr-CA"))
        assertEquals(SpeedUnit.MEGABITS_PER_SECOND, defaultSpeedUnit("en-US"))
        assertEquals(SpeedUnit.MEGABITS_PER_SECOND, defaultSpeedUnit("de"))
    }
}
