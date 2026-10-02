package app.gameport.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitReasonsTest {
    @Test
    fun `crashes and hangs count`() {
        assertTrue(ExitReasons.isCrash("time=1 reason=CRASH status=0 description="))
        assertTrue(ExitReasons.isCrash("time=1 reason=CRASH_NATIVE status=0 description="))
        assertTrue(ExitReasons.isCrash("time=1 reason=ANR status=0 description="))
    }

    @Test
    fun `a fault signal counts, other signals do not`() {
        assertTrue(ExitReasons.isCrash("time=1 reason=SIGNALED status=11 importance=100"))
        assertTrue(ExitReasons.isCrash("time=1 reason=SIGNALED status=6 importance=100"))
        assertFalse(ExitReasons.isCrash("time=1 reason=SIGNALED status=9 importance=100"))
    }

    @Test
    fun `quitting by itself is not a crash`() {
        assertFalse(ExitReasons.isCrash("time=1 reason=EXIT_SELF status=0 importance=100"))
        assertFalse(ExitReasons.isCrash(""))
    }
}
