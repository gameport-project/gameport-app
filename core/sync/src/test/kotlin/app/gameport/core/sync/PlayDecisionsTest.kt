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
    fun `a cancelled launch is remembered for a while, for the game that was cancelled only`() {
        decisions.ask("a.b", "Game")
        decisions.resolve("a.b", PlayDecisions.Choice.QUIT)
        val before = System.currentTimeMillis()
        assertFalse(decisions.wasQuitRecently("a.b", now = before)) // not before the answer was read
        decisions.answerFor("a.b")
        val answered = System.currentTimeMillis()
        assertTrue(decisions.wasQuitRecently("a.b", now = answered))
        assertTrue(decisions.wasQuitRecently("a.b", now = answered + 119_000)) // still, when the end is told a second time
        assertFalse(decisions.wasQuitRecently("a.b", now = answered + 121_000)) // not for ever: the next launch is a game played
        assertFalse(decisions.wasQuitRecently("c.d", now = answered))
    }

    @Test
    fun `playing anyway or taking over is not a cancelled launch`() {
        for (choice in listOf(PlayDecisions.Choice.PLAY, PlayDecisions.Choice.KICK)) {
            decisions.ask("a.b", "Game")
            decisions.resolve("a.b", choice)
            decisions.answerFor("a.b")
            assertFalse(decisions.wasQuitRecently("a.b"))
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
