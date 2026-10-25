package app.gameport.core.sync

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val file = """{"v":1,"count":1,"apps":{"7":{"s":["aa"]}}}"""
    private var clock = 1_000_000_000L

    private class Place(var answer: CatalogAnswer) : CatalogTransport {
        var asked = 0
        override suspend fun get(path: String, etag: String?): CatalogAnswer {
            asked++
            return answer
        }
    }

    private fun repository(place: Place) = MediaRepository(File(temp.root, "files").also { it.mkdirs() }, place, null) { clock }

    @Test
    fun `nothing shows until an answer comes, then the pictures of the app`() = runBlocking {
        val repo = repository(Place(CatalogAnswer.Body(file, "e1")))
        assertNull(repo.observe(7).first())
        repo.refreshIfStale()
        assertEquals(listOf("aa"), repo.observe(7).first()?.s)
        assertNull(repo.observe(8).first())
    }

    @Test
    fun `an answer that is not a media file never replaces the one kept`() = runBlocking {
        val place = Place(CatalogAnswer.Body(file, "e1"))
        val repo = repository(place)
        repo.refreshIfStale()
        clock += 4 * 60 * 60 * 1000L
        place.answer = CatalogAnswer.Body("nonsense", "e2")
        repo.refreshIfStale()
        assertEquals(listOf("aa"), repo.observe(7).first()?.s)
    }

    @Test
    fun `a fresh file is not asked for again`() = runBlocking {
        val place = Place(CatalogAnswer.Body(file, "e1"))
        val repo = repository(place)
        repo.refreshIfStale()
        repo.refreshIfStale()
        assertEquals(1, place.asked)
    }
}
