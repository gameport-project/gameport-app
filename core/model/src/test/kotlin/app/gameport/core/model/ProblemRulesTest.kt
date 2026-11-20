package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProblemRulesTest {
    @Test
    fun `a run that closes within seconds is noted as a short session`() {
        assertEquals(ProblemRules.AfterRun.NOTE_SHORT_SESSION, ProblemRules.afterRun(5_000, problemNoted = false))
        assertEquals(ProblemRules.AfterRun.NOTE_SHORT_SESSION, ProblemRules.afterRun(29_999, problemNoted = true))
    }

    @Test
    fun `a run that held on clears the problem noted before it`() {
        assertEquals(ProblemRules.AfterRun.CLEAR, ProblemRules.afterRun(60_000, problemNoted = true))
        assertEquals(ProblemRules.AfterRun.CLEAR, ProblemRules.afterRun(600_000, problemNoted = true))
    }

    @Test
    fun `a medium run, or a long one with nothing noted, changes nothing`() {
        assertEquals(ProblemRules.AfterRun.NOTHING, ProblemRules.afterRun(45_000, problemNoted = true))
        assertEquals(ProblemRules.AfterRun.NOTHING, ProblemRules.afterRun(600_000, problemNoted = false))
    }
}
