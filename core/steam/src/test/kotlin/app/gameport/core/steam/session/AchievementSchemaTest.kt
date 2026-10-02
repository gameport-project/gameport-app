package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementSchemaTest {
    private fun node(name: String, value: String? = null, vararg children: KeyValue) = KeyValue(name, value).also { it.children.addAll(children) }

    private fun bit(index: Int, name: String, title: KeyValue, desc: KeyValue, hidden: String? = null) = node(
        index.toString(), null,
        node("name", name),
        node("display", null, *listOfNotNull(node("name", null, *title.children.toTypedArray()).takeIf { title.children.isNotEmpty() } ?: node("name", title.value), desc, node("icon", "$name.jpg"), node("icon_gray", "${name}_g.jpg"), hidden?.let { node("hidden", it) }).toTypedArray()),
    )

    private fun langs(vararg pairs: Pair<String, String>) = node("x", null, *pairs.map { node(it.first, it.second) }.toTypedArray())

    private val schema = node(
        "620", null,
        node(
            "stats", null,
            node(
                "1", null,
                node("type", "4"),
                node(
                    "bits", null,
                    bit(0, "ACH_ONE", langs("english" to "One", "french" to "Un"), node("desc", null, node("english", "First"), node("french", "Premier"))),
                    bit(1, "ACH_TWO", langs("english" to "Two"), node("desc", "Same for all"), hidden = "1"),
                ),
            ),
            node("2", null, node("type", "1"), node("name", "kills")),
        ),
    )

    @Test
    fun `the achievements are listed from the schema in its order, with the texts of the language`() {
        val list = parseAchievements(schema, "french") { _, _ -> 0L }
        assertEquals(listOf("ACH_ONE", "ACH_TWO"), list.map { it.name })
        assertEquals("Un", list[0].title)
        assertEquals("Premier", list[0].description)
    }

    @Test
    fun `a language the game does not have falls back to English`() {
        val list = parseAchievements(schema, "german") { _, _ -> 0L }
        assertEquals("One", list[0].title)
        assertEquals("First", list[0].description)
    }

    @Test
    fun `a text written once serves every language, and hidden is read`() {
        val second = parseAchievements(schema, "french") { _, _ -> 0L }[1]
        assertEquals("Two", second.title)
        assertEquals("Same for all", second.description)
        assertTrue(second.hidden)
    }

    @Test
    fun `the unlock times say what is unlocked, and no record at all means all locked`() {
        val some = parseAchievements(schema, "english") { stat, bit -> if (stat == 1 && bit == 1) 1234L else 0L }
        assertFalse(some[0].unlocked)
        assertTrue(some[1].unlocked)
        assertEquals(1234L, some[1].unlockedAt)
        assertEquals(2, parseAchievements(schema, "english") { _, _ -> 0L }.size)
    }

    @Test
    fun `a schema without stats lists nothing, and the root may sit one level above the app`() {
        assertEquals(emptyList<Any>(), parseAchievements(node("empty"), "english") { _, _ -> 0L })
        assertEquals(2, parseAchievements(node("root", null, schema), "english") { _, _ -> 0L }.size)
    }

    @Test
    fun `pictures are named after the schema`() {
        val first = parseAchievements(schema, "english") { _, _ -> 0L }[0]
        assertEquals("ACH_ONE.jpg", first.icon)
        assertEquals("ACH_ONE_g.jpg", first.iconGray)
    }
}
