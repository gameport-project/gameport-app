package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShimAchievementsTest {
    private fun ach(name: String, title: String = name, description: String = "", hidden: Boolean = false, unlocked: Boolean = false, at: Long = 0L) =
        Achievement(name, title, description, icon = "$name.jpg", iconGray = "${name}_g.jpg", hidden = hidden, unlocked = unlocked, unlockedAt = at)

    @Test
    fun `the definitions are a json array of strings in the format of the shim`() {
        val json = ShimAchievements.definitions(listOf(ach("ACH_ONE", "One", "First", hidden = true)))
        assertEquals(
            """[{"name":"ACH_ONE","displayName":"One","description":"First","hidden":"1","icon":"ACH_ONE.jpg","icon_gray":"ACH_ONE_g.jpg"}]""",
            json,
        )
    }

    @Test
    fun `quotes, backslashes and line breaks in the texts are escaped`() {
        val json = ShimAchievements.definitions(listOf(ach("A", title = "He said \"go\"", description = "Line one\nLine two \\ end")))!!
        assertEquals(true, json.contains("""He said \"go\""""))
        assertEquals(true, json.contains("""Line one\nLine two \\ end"""))
    }

    @Test
    fun `a game without achievements has no definitions and no unlocked ones`() {
        assertNull(ShimAchievements.definitions(emptyList()))
        assertNull(ShimAchievements.earned(emptyList()))
    }

    @Test
    fun `only the unlocked ones are listed, with the time they were unlocked`() {
        val json = ShimAchievements.earned(listOf(ach("A", unlocked = true, at = 1234L), ach("B"), ach("C", unlocked = true)))
        assertEquals("""{"A":{"earned":true,"earned_time":1234},"C":{"earned":true,"earned_time":0}}""", json)
    }

    @Test
    fun `nothing unlocked gives no seed`() {
        assertNull(ShimAchievements.earned(listOf(ach("A"), ach("B"))))
    }

    @Test
    fun `a list too big for the shim to read is left out`() {
        val big = List(2000) { ach("A$it", description = "x".repeat(600)) }
        assertNull(ShimAchievements.definitions(big))
    }
}
