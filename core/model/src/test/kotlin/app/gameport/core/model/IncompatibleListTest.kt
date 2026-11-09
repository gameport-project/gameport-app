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

    @Test
    fun `a game limited to some devices is incompatible only on those`() {
        val limited = IncompatibleList.fromRelay(
            """{"v":1,"reasons":{"meta":{"title":{"en":"T","fr":"T"},"text":{"en":"t","fr":"t"}}},""" +
                """"games":[{"appId":1,"name":"Everywhere","reason":"meta"},{"appId":2,"name":"Quests","reason":"meta","devices":["quest"]},""" +
                """{"appId":3,"name":"One model","reason":"meta","devices":["Meta Quest 3"]}]}""",
        )!!
        val quest3 = setOf("quest", "Quest 3", "Quest 3")
        val pico = setOf("pico", "Pico 4", "Pico 4")
        assertEquals(listOf(1, 2, 3), limited.forDevice(quest3).games.map { it.appId })
        assertEquals(listOf(1), limited.forDevice(pico).games.map { it.appId })
        assertEquals(listOf(1, 2), limited.forDevice(setOf("quest", "Quest 2", "Quest 2")).games.map { it.appId })
    }

    @Test
    fun `a game can name a different reason for each device`() {
        val two = IncompatibleList.fromRelay(
            """{"v":1,"reasons":{"a":{"title":{"en":"A","fr":"A"},"text":{"en":"a","fr":"a"}},"b":{"title":{"en":"B","fr":"B"},"text":{"en":"b","fr":"b"}}},""" +
                """"games":[{"appId":7,"name":"G","reason":"a","devices":["quest"]},{"appId":7,"name":"G","reason":"b","devices":["pico"]}]}""",
        )!!
        assertEquals("a", two.forDevice(setOf("quest", "Quest 3")).game(7)?.reason)
        assertEquals("b", two.forDevice(setOf("pico", "Pico 4")).game(7)?.reason)
        assertNull(two.forDevice(setOf("phone")).game(7))
        assertTrue(two.problems(listOf("en", "fr")).isEmpty())
    }

    @Test
    fun `a reason that warns never hides a game, and a reason that says nothing blocks`() {
        val mixed = IncompatibleList.fromRelay(
            """{"v":1,"reasons":{"a":{"title":{"en":"A","fr":"A"},"text":{"en":"a","fr":"a"}},"w":{"kind":"warns","title":{"en":"W","fr":"W"},"text":{"en":"w","fr":"w"}}},""" +
                """"games":[{"appId":1,"name":"Blocked","reason":"a"},{"appId":2,"name":"Warned","reason":"w","devices":["quest"]}]}""",
        )!!
        assertEquals(listOf(1), mixed.games.map { it.appId })
        assertNull(mixed.game(2))
        assertEquals("w", mixed.warning(2)?.reason)
        assertEquals("W", mixed.warningReasonOf(2)?.title?.get("en"))
        assertNull(mixed.warning(1))
        assertEquals("Warned", mixed.forDevice(setOf("quest", "Quest 3")).warning(2)?.name)
        assertNull(mixed.forDevice(setOf("pico")).warning(2))
        assertTrue(mixed.problems(listOf("en", "fr")).isEmpty())
    }
}
