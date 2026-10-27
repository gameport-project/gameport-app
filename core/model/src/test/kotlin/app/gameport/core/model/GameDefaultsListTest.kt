package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameDefaultsListTest {
    @Test
    fun `the games the console says declare the passthrough are read`() {
        val list = GameDefaultsList.fromRelay("""{"v":1,"games":{"804530":{"passthrough":"on"},"3748900":{"passthrough":"on"}}}""")
        assertEquals(setOf(804530, 3748900), list?.passthrough)
    }

    @Test
    fun `an empty answer is a list with no game, not a bad answer`() {
        assertEquals(GameDefaultsList(), GameDefaultsList.fromRelay("""{"v":1,"games":{}}"""))
    }

    @Test
    fun `a setting or a value this version does not know is left out, and so is an id that is not a number`() {
        val text = """{"v":1,"games":{"1":{"passthrough":"maybe"},"2":{"future":"on"},"x":{"passthrough":"on"},"3":{"passthrough":"on","future":"on"}}}"""
        assertEquals(setOf(3), GameDefaultsList.fromRelay(text)?.passthrough)
    }

    @Test
    fun `an answer that is not the relay's never replaces the list in use`() {
        for (bad in listOf(null, "", "not json", """{"v":2,"games":{}}""", """{"games":{}}""", """{"v":1}""", """{"v":1,"games":[1]}""", "[]")) {
            assertNull(bad.toString(), GameDefaultsList.fromRelay(bad))
        }
    }
}
