package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceTuningTest {
    @Test
    fun `the first choice of each setting leaves the graphics as they are`() {
        assertEquals(100, PerformanceTuning.RESOLUTIONS.first())
        assertEquals(0, PerformanceTuning.REFRESH_RATES.first())
        assertEquals(-1, PerformanceTuning.LEVELS.first())
        val untouched = GameSettings()
        assertEquals(100, untouched.resolutionPercent)
        assertEquals(0, untouched.refreshRate)
        assertEquals(-1, untouched.cpuLevel)
        assertEquals(-1, untouched.gpuLevel)
    }

    @Test
    fun `a stored value that is not one of the choices means no choice was made`() {
        assertEquals(100, PerformanceTuning.resolution(333))
        assertEquals(80, PerformanceTuning.resolution(80))
        assertEquals(0, PerformanceTuning.refreshRate(60))
        assertEquals(72, PerformanceTuning.refreshRate(72))
        assertEquals(-1, PerformanceTuning.level(12))
        assertEquals(75, PerformanceTuning.level(75))
    }

    @Test
    fun `the resolutions go from half to double`() {
        assertEquals(50, PerformanceTuning.RESOLUTIONS.min())
        assertEquals(200, PerformanceTuning.RESOLUTIONS.max())
    }
}
