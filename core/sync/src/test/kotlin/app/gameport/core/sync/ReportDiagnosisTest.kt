package app.gameport.core.sync

import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDiagnosisTest {
    @Test
    fun `what loaded and what did not is said, and how the last runs ended`() {
        val text = ReportDiagnosis.of(
            libraries = "/data/app/x/lib/arm64/libunity.so\n/data/app/x/lib/arm64/libopenxr_loader.so\n",
            startLog = "I GPHook : plan: NONE",
            previousExit = "time=1 reason=SIGNALED status=9 importance=100\ntime=2 reason=SIGNALED status=9 importance=100\ntime=3 reason=OTHER_13 status=0 importance=400\n",
            dataAgeSeconds = 300,
        )
        assertTrue(text, text.contains("hook of GamePort ran in the game: yes"))
        assertTrue(text, text.contains("OpenXR loader loaded: yes"))
        assertTrue(text, text.contains("OpenXR layer of GamePort loaded: no"))
        assertTrue(text, text.contains("SIGNALED/9 x2"))
        assertTrue(text, text.contains("OTHER_13 x1"))
        assertTrue(text, text.contains("may be from the start of the run"))
    }

    @Test
    fun `nothing handed over is not said as no`() {
        val text = ReportDiagnosis.of("(the game has not handed it over yet)", "", "", null)
        assertTrue(text, text.contains("loaded libraries: not handed over"))
        assertTrue(text, text.contains("unknown (no log handed over)"))
        assertTrue(text, text.contains("last exits: none recorded"))
    }
}
