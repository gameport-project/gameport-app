package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadEtaTest {
    private var clock = 0L
    private val eta = DownloadEta { clock }

    private fun at(seconds: Int, progress: Float) = eta.update(progress).also { clock = seconds * 1000L }

    @Test
    fun `nothing is told for the first moments`() {
        assertNull(eta.update(0.10f))
        clock += 10_000
        assertNull(eta.update(0.11f))
    }

    @Test
    fun `a steady download tells what is left over the rate`() {
        eta.update(0.50f)
        clock += 60_000
        // 1 % a minute from 50 %: 49 % left on the next sample, so 49 minutes.
        assertEquals(49 * 60L, eta.update(0.51f))
    }

    @Test
    fun `a download that does not move tells nothing`() {
        eta.update(0.50f)
        clock += 60_000
        assertNull(eta.update(0.50f))
    }

    @Test
    fun `a figure that goes back starts the window again`() {
        eta.update(0.50f)
        clock += 60_000
        assertNull(eta.update(0.20f))
        clock += 30_000
        assertEquals(((1.0 - 0.21) / (0.01 / 30)).toLong(), eta.update(0.21f))
    }

    @Test
    fun `only the last two minutes set the rate`() {
        eta.update(0.00f)
        clock += 100_000
        eta.update(0.40f)
        clock += 100_000
        // From 40 % to 41 % in 100 s: the first 100 s fell out of the window and the fast start no longer counts.
        assertEquals(((1.0 - 0.41) / (0.01 / 100)).toLong(), eta.update(0.41f))
    }

    @Test
    fun `an absurd time is not told`() {
        eta.update(0.10f)
        clock += 60_000
        assertNull(eta.update(0.1000001f))
    }

    @Test
    fun `reset forgets the samples`() {
        eta.update(0.50f)
        clock += 60_000
        eta.reset()
        assertNull(eta.update(0.51f))
    }
}
