package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Test

class VerificationProgressTest {
    private var read = 1_000L

    @Test
    fun `the share of the bytes to check that were read since the start`() {
        val progress = VerificationProgress(bytesToCheck = 1_000) { read }
        assertEquals(0f, progress.fraction(), 0.0001f)
        read += 250
        assertEquals(0.25f, progress.fraction(), 0.0001f)
        read += 500
        assertEquals(0.75f, progress.fraction(), 0.0001f)
    }

    @Test
    fun `it never reaches 100 percent, that is when the downloading starts`() {
        val progress = VerificationProgress(bytesToCheck = 1_000) { read }
        read += 5_000
        assertEquals(0.99f, progress.fraction(), 0.0001f)
    }

    @Test
    fun `nothing to check, or a system that does not say, gives zero`() {
        assertEquals(0f, VerificationProgress(bytesToCheck = 0) { read }.fraction(), 0f)
        assertEquals(0f, VerificationProgress(bytesToCheck = 1_000) { null }.fraction(), 0f)
    }

    @Test
    fun `the read counter of the process is read from its io file`() {
        assertEquals(12_345L, parseReadBytes("rchar: 12345\nwchar: 99\nsyscr: 3\n"))
        assertEquals(null, parseReadBytes("wchar: 99\n"))
    }
}
