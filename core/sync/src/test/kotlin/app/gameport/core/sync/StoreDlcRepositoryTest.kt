package app.gameport.core.sync

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The extra content of a game as Steam's shop lists it: how its answers are read, and how they are kept. */
class StoreDlcRepositoryTest {
    private val listOfGame = """{"10":{"success":true,"data":{"name":"Game","dlc":[11,12,13]}}}"""
    private fun one(id: Int, name: String, free: Boolean = false, price: String? = null) =
        """{"$id":{"success":true,"data":{"name":"$name","is_free":$free,"header_image":"https://img/$id.jpg"${price?.let { ""","price_overview":{"final_formatted":"$it"}""" }.orEmpty()}}}}"""

    @Test
    fun `the numbers of the extra content come in the order of the list`() {
        assertEquals(listOf(11, 12, 13), StoreDlcParsing.ids(listOfGame, 10))
    }

    @Test
    fun `a game with no extra content, or an answer that says nothing, gives none`() {
        assertTrue(StoreDlcParsing.ids("""{"10":{"success":true,"data":{"name":"Game"}}}""", 10).isEmpty())
        assertTrue(StoreDlcParsing.ids("""{"10":{"success":false}}""", 10).isEmpty())
        assertTrue(StoreDlcParsing.ids("""{"10":{"success":true,"data":[]}}""", 10).isEmpty())
        assertTrue(StoreDlcParsing.ids("not json", 10).isEmpty())
    }

    @Test
    fun `an extra content has its name, its picture, whether it is free and its price`() {
        assertEquals(StoreDlc(11, "Map pack", "https://img/11.jpg", false, "4,99€"), StoreDlcParsing.dlc(one(11, "Map pack", price = "4,99€"), 11))
        assertEquals(StoreDlc(12, "Free skin", "https://img/12.jpg", true, null), StoreDlcParsing.dlc(one(12, "Free skin", free = true), 12))
    }

    @Test
    fun `an extra content the shop does not describe is left out`() {
        assertNull(StoreDlcParsing.dlc("""{"13":{"success":false}}""", 13))
        assertNull(StoreDlcParsing.dlc("""{"13":{"success":true,"data":{"name":""}}}""", 13))
    }

    private fun repository(now: () -> Long, answers: (String) -> String?, asked: MutableList<String> = mutableListOf()) =
        StoreDlcRepository(Files.createTempDirectory("dlc").toFile(), { url -> asked += url; answers(url) }, now)

    private fun shop(url: String): String? = when {
        "appids=10&" in url -> listOfGame
        "appids=11&" in url -> one(11, "Map pack", price = "4,99€")
        "appids=12&" in url -> one(12, "Free skin", free = true)
        "appids=13&" in url -> """{"13":{"success":false}}"""
        else -> null
    }

    @Test
    fun `the list is the extra content the shop describes, in order, asked in the language and the country given`() = runBlocking {
        val asked = mutableListOf<String>()
        val dlc = repository({ 0L }, ::shop, asked).of(10, "FR", "fr")
        assertEquals(listOf(11, 12), dlc.map { it.appId })
        assertTrue(asked.all { "l=french" in it })
        assertTrue(asked.filter { "appids=1" in it && "appids=10" !in it }.all { "cc=FR" in it })
    }

    @Test
    fun `what was kept is not asked again for a day`() = runBlocking {
        var clock = 0L
        val asked = mutableListOf<String>()
        val repository = repository({ clock }, ::shop, asked)
        repository.of(10, "FR", "fr")
        val first = asked.size
        clock = 23L * 60 * 60 * 1000
        assertEquals(2, repository.of(10, "FR", "fr").size)
        assertEquals(first, asked.size)
        clock = 25L * 60 * 60 * 1000
        repository.of(10, "FR", "fr")
        assertTrue(asked.size > first)
    }

    @Test
    fun `a device that cannot reach the shop shows what it kept, however old`() = runBlocking {
        var clock = 0L
        var online = true
        val repository = repository({ clock }, { url -> if (online) shop(url) else null })
        assertEquals(2, repository.of(10, "FR", "fr").size)
        online = false
        clock = 10L * 24 * 60 * 60 * 1000
        assertEquals(2, repository.of(10, "FR", "fr").size)
    }

    @Test
    fun `a question that failed is asked again soon, not in a day`() = runBlocking {
        var clock = 0L
        var failing = true
        val asked = mutableListOf<String>()
        val repository = repository({ clock }, { url -> if (failing && "appids=12&" in url) null else shop(url) }, asked)
        assertEquals(listOf(11), repository.of(10, "FR", "fr").map { it.appId })
        failing = false
        clock = 5L * 60 * 1000
        assertEquals(1, repository.of(10, "FR", "fr").size)
        clock = 11L * 60 * 1000
        assertEquals(listOf(11, 12), repository.of(10, "FR", "fr").map { it.appId })
    }

    @Test
    fun `nothing is known when the shop cannot be reached and nothing was kept`() = runBlocking {
        assertTrue(repository({ 0L }, { null }).of(10, "FR", "fr").isEmpty())
    }

    @Test
    fun `a language the shop names differently is named as the shop does`() {
        assertEquals("schinese", steamLanguageName("zh-CN"))
        assertEquals("brazilian", steamLanguageName("pt-BR"))
        assertEquals("english", steamLanguageName("xx"))
    }
}
