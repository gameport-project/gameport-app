package app.gameport.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncompatibleListTest {
    // The real file, as the app ships it.
    private val list = IncompatibleList.parse(File("../settings/src/main/assets/incompatible.json").readText())

    @Test
    fun `VRChat is listed, found by its app id, with a reason in both languages`() {
        assertEquals("VRChat", list.game(438100)?.name)
        assertNotNull(list.reasonOf(438100)?.text?.get("fr"))
        assertNull(list.game(1))
    }

    @Test
    fun `the shipped file is complete in every language`() {
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
}
