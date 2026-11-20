package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceTuningTest {
    @Test
    fun `the first choice of each setting leaves the graphics as they are`() {
        assertEquals(0, PerformanceTuning.DEFAULT_RESOLUTION)
        assertEquals(0, PerformanceTuning.REFRESH_RATES.first())
        assertEquals(-1, PerformanceTuning.LEVELS.first())
        val untouched = GameSettings()
        assertEquals(0, untouched.resolutionPercent)
        assertEquals(0, untouched.refreshRate)
        assertEquals(-1, untouched.cpuLevel)
        assertEquals(-1, untouched.gpuLevel)
    }

    @Test
    fun `a stored value that is not one of the choices means no choice was made`() {
        assertEquals(0, PerformanceTuning.resolution(333))
        assertEquals(80, PerformanceTuning.resolution(80))
        assertEquals(0, PerformanceTuning.refreshRate(60))
        assertEquals(72, PerformanceTuning.refreshRate(72))
        assertEquals(-1, PerformanceTuning.level(12))
        assertEquals(75, PerformanceTuning.level(75))
    }

    @Test
    fun `the resolution runs from 70 to 200 percent by 5, or stays on the default`() {
        assertEquals(0, PerformanceTuning.resolution(0))
        assertEquals(70, PerformanceTuning.resolution(70))
        assertEquals(135, PerformanceTuning.resolution(135))
        assertEquals(200, PerformanceTuning.resolution(200))
        assertEquals(0, PerformanceTuning.resolution(65))
        assertEquals(0, PerformanceTuning.resolution(72))
        assertEquals(0, PerformanceTuning.resolution(205))
        assertEquals(true, PerformanceTuning.CUSTOM_START in PerformanceTuning.MIN_RESOLUTION..PerformanceTuning.MAX_RESOLUTION)
    }

    @Test
    fun `the size of the image follows the choice, even and within the limits`() {
        assertEquals(2160 to 2160, PerformanceTuning.scaledSizes(2160, 2160, 100))
        assertEquals(3240 to 3240, PerformanceTuning.scaledSizes(2160, 2160, 150))
        assertEquals(1080 to 1080, PerformanceTuning.scaledSizes(2160, 2160, 50))
        assertEquals(2700 to 2700, PerformanceTuning.scaledSizes(2160, 2160, 125))
        assertEquals(4096 to 4096, PerformanceTuning.scaledSizes(2160, 2160, 200))
    }

    @Test
    fun `a screen that is not square keeps its proportions, also when the limit is reached`() {
        assertEquals(2064 to 2208, PerformanceTuning.scaledSizes(2064, 2208, 100))
        assertEquals(3096 to 3312, PerformanceTuning.scaledSizes(2064, 2208, 150))
        // 200 % would make 4128 by 4416: both are brought back in proportion so the largest is 4096.
        assertEquals(3828 to 4096, PerformanceTuning.scaledSizes(2064, 2208, 200))
    }

    @Test
    fun `the refresh rates of a system and the game's own are valid choices`() {
        assertEquals(120, PerformanceTuning.refreshRate(120))
        assertEquals(80, PerformanceTuning.refreshRate(80))
        assertEquals(0, PerformanceTuning.refreshRate(60))
    }
}
