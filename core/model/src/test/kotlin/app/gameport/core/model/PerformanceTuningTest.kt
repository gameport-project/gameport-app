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
    fun `the side of the image follows the choice, even and within the limits`() {
        assertEquals(2160, PerformanceTuning.scaledSide(2160, 100))
        assertEquals(3240, PerformanceTuning.scaledSide(2160, 150))
        assertEquals(1080, PerformanceTuning.scaledSide(2160, 50))
        assertEquals(2700, PerformanceTuning.scaledSide(2160, 125))
        assertEquals(4096, PerformanceTuning.scaledSide(2160, 200))
    }
}
