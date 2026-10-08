package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `a cancelled launch is told once, and only for the game that was cancelled`() {
        decisions.ask("a.b", "Game")
        decisions.resolve("a.b", PlayDecisions.Choice.QUIT)
        assertFalse(decisions.consumeQuit("a.b"))
        decisions.answerFor("a.b")
        assertFalse(decisions.consumeQuit("c.d"))
        assertTrue(decisions.consumeQuit("a.b"))
        assertFalse(decisions.consumeQuit("a.b"))
    }

    @Test
    fun `playing anyway or taking over is not a cancelled launch`() {
        for (choice in listOf(PlayDecisions.Choice.PLAY, PlayDecisions.Choice.KICK)) {
            decisions.ask("a.b", "Game")
            decisions.resolve("a.b", choice)
            decisions.answerFor("a.b")
            assertFalse(decisions.consumeQuit("a.b"))
        }
    }

    @Test
    fun `the window about another device is remembered once for the game it was opened for`() {
        assertFalse(decisions.consumeAsked("a.b"))
        decisions.ask("a.b", "Game")
        assertFalse(decisions.consumeAsked("c.d"))
        assertTrue(decisions.consumeAsked("a.b"))
        assertFalse(decisions.consumeAsked("a.b"))
    }
}
