package app.gameport.core.model

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class RefreshOutcomeTest {
    private fun outcome(text: String) = RefreshOutcome.parse(text)!!

    @Test
    fun `a refusal of the runtime is told first`() {
        assertEquals(RefreshOutcome.Verdict.REFUSED, outcome("asked=80\nresult=-1\naccepted=72,90\ngameasked=0\nobserved=38\n").verdict)
    }

    @Test
    fun `a game that asks for its own rate is told`() {
        assertEquals(RefreshOutcome.Verdict.GAME_OWN, outcome("asked=80\nresult=0\naccepted=72,80,90\ngameasked=90\nobserved=90\n").verdict)
    }

    @Test
    fun `frames at another rate than the one asked are told`() {
        assertEquals(RefreshOutcome.Verdict.NOT_KEPT, outcome("asked=80\nresult=0\naccepted=\ngameasked=0\nobserved=90\n").verdict)
    }

    @Test
    fun `a rate that holds, or is not measured yet, says nothing`() {
        assertEquals(RefreshOutcome.Verdict.KEPT, outcome("asked=80\nresult=0\naccepted=80\ngameasked=0\nobserved=79\n").verdict)
        assertEquals(RefreshOutcome.Verdict.KEPT, outcome("asked=80\nresult=0\naccepted=80\ngameasked=0\nobserved=0\n").verdict)
    }

    @Test
    fun `the accepted rates are read, and a text that is no report is not`() {
        assertEquals(listOf(72, 90), outcome("asked=72\nresult=0\naccepted=72,90\n").accepted)
        assertNull(RefreshOutcome.parse("garbage"))
        assertNull(RefreshOutcome.parse(null))
    }

    @Test
    fun `a rate taken off a game is kept with the rates the device accepts`() {
        val reset = RateReset(80, listOf(72, 90))
        assertEquals(reset, RateReset.parse(reset.write()))
        assertNull(RateReset.parse("nothing"))
    }

    @Test
    fun `a refused rate that is not in the accepted list is taken off`() {
        val refused = outcome("asked=80\nresult=-1\naccepted=72,90\ngameasked=0\nobserved=90\n")
        assertEquals(true, refused.shouldTakeOff(currentChoice = 80, needsPatchInstead = false))
    }

    @Test
    fun `a refused rate is kept when the choice changed since, or the patch is what it needs, or the runtime listed nothing`() {
        val refused = outcome("asked=80\nresult=-1\naccepted=72,90\ngameasked=0\nobserved=90\n")
        assertEquals(false, refused.shouldTakeOff(currentChoice = 90, needsPatchInstead = false))
        assertEquals(false, refused.shouldTakeOff(currentChoice = 80, needsPatchInstead = true))
        assertEquals(false, outcome("asked=80\nresult=-1\naccepted=\n").shouldTakeOff(80, false))
    }

    @Test
    fun `a rate the runtime accepted is never taken off`() {
        assertEquals(false, outcome("asked=90\nresult=0\naccepted=72,90\ngameasked=0\nobserved=90\n").shouldTakeOff(90, false))
        // Even refused, a rate that the runtime lists is not taken off: the list is what the runtime says it accepts.
        assertEquals(false, outcome("asked=90\nresult=-1\naccepted=72,90\n").shouldTakeOff(90, false))
    }
}
