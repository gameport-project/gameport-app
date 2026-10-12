package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncompatibleListTest {
    private val list = IncompatibleList.fromRelay(
        """{"v":1,"reasons":{"anti-tamper":{"title":{"en":"Protected","fr":"Protégé"},"text":{"en":"it closes","fr":"il se ferme"}}},""" +
            """"games":[{"appId":438100,"name":"VRChat","reason":"anti-tamper"}]}""",
    )!!

    @Test
    fun `a game is found by its app id, with a reason in both languages`() {
        assertEquals("VRChat", list.game(438100)?.name)
        assertNotNull(list.reasonOf(438100)?.text?.get("fr"))
        assertNull(list.game(1))
    }

    @Test
    fun `a list that comes from the relay is complete in every language`() {
        val problems = list.problems(listOf("en", "fr"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `an unknown reason, a missing language and a twin are refused`() {
        val bad = IncompatibleList(
            reasons = mapOf("a" to IncompatibleReason(mapOf("en" to "A"), mapOf("en" to "A; b", "fr" to "A"))),
            games = listOf(IncompatibleGame(1, "One", "a"), IncompatibleGame(1, "Twin", "a"), IncompatibleGame(2, "Two", "nope")),
        )
        val problems = bad.problems(listOf("en", "fr")).joinToString("\n")
        assertTrue("listed twice" in problems)
        assertTrue("unknown reason nope" in problems)
        assertTrue("no text in fr" in problems)
        assertTrue("semicolon" in problems)
    }

    @Test
    fun `the list of the relay gives each game the reason of the table of reasons, in both languages`() {
        val relay = IncompatibleList.fromRelay(
            """{"v":1,"reasons":{"anti-tamper":{"title":{"en":"Protected","fr":"Protégé"},"text":{"en":"it closes","fr":"il se ferme"}}},""" +
                """"games":[{"appId":438100,"name":"VRChat","reason":"anti-tamper"}]}""",
        )!!
        assertEquals("VRChat", relay.game(438100)?.name)
        assertEquals("Protégé", relay.reasonOf(438100)?.title?.get("fr"))
        assertTrue(relay.problems(listOf("en", "fr")).isEmpty())
    }

    @Test
    fun `an empty list of the relay is a list, so a game made compatible is no longer hidden`() {
        assertEquals(0, IncompatibleList.fromRelay("""{"v":1,"games":[]}""")!!.games.size)
    }

    @Test
    fun `an answer that is not the list gives nothing`() {
        assertNull(IncompatibleList.fromRelay("""{"error":"not found"}"""))
        assertNull(IncompatibleList.fromRelay("""{"v":2,"games":[]}"""))
        assertNull(IncompatibleList.fromRelay("not json"))
    }
}
