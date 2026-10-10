package app.gameport.core.sync

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CatalogTextsRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun texts(code: String, summary: String) = """{"v":1,"lang":"$code","tags":{"19":"Tag"},"summaries":{"5":"$summary"}}"""

    private class Place(var answer: CatalogAnswer) : CatalogTransport {
        val asked = mutableListOf<String>()
        override suspend fun get(path: String, etag: String?): CatalogAnswer {
            asked += path
            return answer
        }
    }

    private var clock = 1_000_000_000L
    private fun repository(place: Place, shipped: Map<String, String> = mapOf("en" to texts("en", "Shipped"))) =
        CatalogTextsRepository(File(temp.root, "files").also { it.mkdirs() }, { shipped[it] }, place) { clock }

    private fun CatalogTextsRepository.summary(code: String) = runBlocking { observe(code).first()?.summary(5) }

    @Test
    fun `the languages shipped with the app show at once, the others when they are read`() = runBlocking {
        val place = Place(CatalogAnswer.Body(texts("de", "Frisch"), null))
        val repository = repository(place)
        assertEquals("Shipped", repository.summary("en"))
        assertNull(repository.summary("de"))
        repository.refreshIfStale("de")
        assertEquals("Frisch", repository.summary("de"))
        assertEquals(listOf("v1/text/de.json"), place.asked)
    }

    @Test
    fun `a file for another language than the one asked is not kept`() = runBlocking {
        val repository = repository(Place(CatalogAnswer.Body(texts("fr", "Autre"), null)))
        repository.refreshIfStale("de")
        assertNull(repository.summary("de"))
    }

    @Test
    fun `what was read shows at the next start`() = runBlocking {
        repository(Place(CatalogAnswer.Body(texts("fr", "Gardé"), null))).refreshIfStale("fr")
        assertEquals("Gardé", repository(Place(CatalogAnswer.Unavailable)).summary("fr"))
    }

    @Test
    fun `a day-old file is asked again, a fresh one is not`() = runBlocking {
        val place = Place(CatalogAnswer.Body(texts("en", "Fresh"), null))
        val repository = repository(place)
        repository.refreshIfStale("en")
        clock += 60 * 60 * 1000L
        repository.refreshIfStale("en")
        assertEquals(1, place.asked.size)
        clock += 24 * 60 * 60 * 1000L
        repository.refreshIfStale("en")
        assertEquals(2, place.asked.size)
    }
}
