package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayDecisionsTest {
    private val decisions = PlayDecisions()

    @Test
    fun `a question stays open until the player answers`() {
        decisions.ask("a.b", "Game")
        assertEquals("PENDING", decisions.answerFor("a.b"))
        assertEquals("Game", decisions.pending.value["a.b"]?.gameName)
        assertEquals("NONE", decisions.answerFor("unknown"))
    }

    @Test
    fun `an answer is given once, and the question is closed`() {
        decisions.ask("a.b", "Game")
        decisions.resolve("a.b", PlayDecisions.Choice.KICK)
        assertEquals("KICK", decisions.answerFor("a.b"))
        assertNull(decisions.pending.value["a.b"])
        assertEquals("NONE", decisions.answerFor("a.b"))
    }

    @Test
    fun `asking again does not erase an answer, and a game that gives up closes its question`() {
        decisions.ask("a.b", "Game")
        decisions.resolve("a.b", PlayDecisions.Choice.PLAY)
        decisions.ask("a.b", "Game")
        assertEquals(PlayDecisions.Choice.PLAY, decisions.pending.value["a.b"]?.choice)
        decisions.clear("a.b")
        assertNull(decisions.pending.value["a.b"])
    }
}
