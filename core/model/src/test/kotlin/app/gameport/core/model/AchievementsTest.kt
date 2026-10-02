package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementsTest {
    private fun ach(name: String, unlocked: Boolean, at: Long = 0L) = Achievement(name, name, "", null, null, hidden = false, unlocked = unlocked, unlockedAt = at)

    private val list = AchievementList(1, "english", listOf(ach("a", true, 100), ach("b", false), ach("c", true, 300), ach("d", true, 200), ach("e", false)))

    @Test
    fun `counts what is unlocked`() {
        assertEquals(5, list.total)
        assertEquals(3, list.unlockedCount)
    }

    @Test
    fun `the latest unlocked come first`() {
        assertEquals(listOf("c", "d"), list.recent(2).map { it.name })
    }

    @Test
    fun `the full list shows the unlocked first and keeps the order of the rest`() {
        assertEquals(listOf("c", "d", "a", "b", "e"), list.ordered().map { it.name })
    }

    @Test
    fun `a game without achievements has an empty list`() {
        assertEquals(emptyList<Achievement>(), AchievementList(1, "english", emptyList()).recent(3))
    }

    @Test
    fun `the language of the device gives Steam's name for it`() {
        assertEquals("french", SteamLanguage.of("fr", "CA"))
        assertEquals("koreana", SteamLanguage.of("ko"))
        assertEquals("german", SteamLanguage.of("de", "AT"))
        assertEquals("spanish", SteamLanguage.of("es", "ES"))
        assertEquals("latam", SteamLanguage.of("es", "MX"))
        assertEquals("brazilian", SteamLanguage.of("pt", "BR"))
        assertEquals("portuguese", SteamLanguage.of("pt", "PT"))
        assertEquals("tchinese", SteamLanguage.of("zh", "TW"))
        assertEquals("schinese", SteamLanguage.of("zh", "CN"))
        assertEquals("norwegian", SteamLanguage.of("nb", "NO"))
    }

    @Test
    fun `a language Steam does not have falls back to English`() {
        assertEquals("english", SteamLanguage.of("sw"))
    }

    @Test
    fun `pictures are looked for on Steam's hosts in order, and not at all without a file`() {
        assertEquals(2, AchievementArtwork.urls(620, "abc.jpg").size)
        assertEquals(emptyList<String>(), AchievementArtwork.urls(620, null))
        assertEquals(emptyList<String>(), AchievementArtwork.urls(620, ""))
    }
}
