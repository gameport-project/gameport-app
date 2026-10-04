package app.gameport.core.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyingDetectorTest {
    private var clock = 0L
    private val detector = VerifyingDetector { clock }

    @Test
    fun `a download that keeps receiving never shows as verifying`() {
        var received = 0L
        repeat(20) {
            clock += 1_000
            received += 2_000_000
            assertFalse(detector.update(received))
        }
    }

    @Test
    fun `a pause between two chunks is not a verification`() {
        detector.update(1_000_000)
        clock += 1_500
        assertFalse(detector.update(1_000_000))
    }

    @Test
    fun `progress without data for a few seconds is a verification, until real data flows`() {
        clock += 5_000
        assertTrue(detector.update(0))
        clock += 1_000
        assertTrue(detector.update(100_000))
        clock += 1_000
        assertFalse(detector.update(1_000_000))
    }

    @Test
    fun `a resumed download shows as verifying from the start, until real data flows`() {
        val resumed = VerifyingDetector(startVerifying = true) { clock }
        assertTrue(resumed.update(0))
        clock += 1_000
        assertTrue(resumed.update(100_000))
        clock += 1_000
        assertFalse(resumed.update(100_000 + 600_000))
    }
}
