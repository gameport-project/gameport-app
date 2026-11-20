package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceTuningTest {
    @Test
    fun `the first choice of each setting leaves the graphics as they are`() {
        assertEquals(0, PerformanceTuning.DEFAULT_RESOLUTION)
        assertEquals(0, GameSettings().refreshRate)
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
        assertEquals(0, PerformanceTuning.refreshRate(7))
        assertEquals(0, PerformanceTuning.refreshRate(9000))
        assertEquals(72, PerformanceTuning.refreshRate(72))
        assertEquals(144, PerformanceTuning.refreshRate(144))
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
    fun `any rate between the bounds is a valid choice, a rate that no screen has is not`() {
        assertEquals(120, PerformanceTuning.refreshRate(120))
        assertEquals(60, PerformanceTuning.refreshRate(60))
        assertEquals(0, PerformanceTuning.refreshRate(10))
    }

    @Test
    fun `the rates offered are the round ones the device exposes, up to 144, and the fallback when it exposes none`() {
        assertEquals(listOf(60, 72, 80, 90), PerformanceTuning.offeredRates((60..90).toList(), listOf(72, 90)))
        assertEquals(listOf(72, 80, 90, 100, 120, 144), PerformanceTuning.offeredRates((72..240).toList(), listOf(72, 90)))
        assertEquals(listOf(72, 80, 90, 120), PerformanceTuning.offeredRates(listOf(72, 80, 90, 120), listOf(72, 90)))
        assertEquals(listOf(72, 90), PerformanceTuning.offeredRates(emptyList(), listOf(72, 90)))
    }

    @Test
    fun `the largest image a smaller mode can show is a share of the screen, by steps`() {
        // A Quest 3 at 240 Hz: 3104 x 1664 for both eyes is 1552 x 1664 for one, against 2064 x 2208 at the full mode.
        assertEquals(75, PerformanceTuning.capForMode(1552 to 1664, 2064 to 2208))
        assertEquals(100, PerformanceTuning.capForMode(2064 to 2208, 2064 to 2208))
        assertEquals(PerformanceTuning.MIN_RESOLUTION, PerformanceTuning.capForMode(100 to 100, 2064 to 2208))
        assertEquals(null, PerformanceTuning.capForMode(null, 2064 to 2208))
        assertEquals(null, PerformanceTuning.capForMode(1552 to 1664, null))
    }
}
