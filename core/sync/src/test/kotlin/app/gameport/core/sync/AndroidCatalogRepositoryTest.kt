package app.gameport.core.sync

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidCatalogRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun catalog(vararg names: String) =
        """{"v":1,"count":${names.size},"apps":[${names.mapIndexed { i, n -> """{"appid":${i + 1},"name":"$n","android":"ready","installable":true}""" }.joinToString(",")}]}"""

    private class Place(var answer: CatalogAnswer) : CatalogTransport {
        val asked = mutableListOf<String?>()
        override suspend fun get(path: String, etag: String?): CatalogAnswer {
            asked += etag
            return answer
        }
    }

    private var clock = 1_000_000_000L
    private fun repository(place: Place, shipped: String? = catalog("Shipped")) =
        AndroidCatalogRepository(File(temp.root, "files").also { it.mkdirs() }, { shipped }, place) { clock }

    private fun AndroidCatalogRepository.names() = runBlocking { observe().first()?.apps?.map { it.name } }

    @Test
    fun `the catalog shipped with the app shows until an answer comes`() {
        assertEquals(listOf("Shipped"), repository(Place(CatalogAnswer.Unavailable)).names())
    }

    @Test
    fun `an answer is kept and replaces the shipped catalog`() = runBlocking {
        val place = Place(CatalogAnswer.Body(catalog("Fresh"), "\"v1\""))
        val first = repository(place)
        first.refreshIfStale()
        assertEquals(listOf("Fresh"), first.names())
        // Another start of the app: what was kept shows at once, before any network.
        assertEquals(listOf("Fresh"), repository(Place(CatalogAnswer.Unavailable)).names())
    }

    @Test
    fun `a file asked again is asked with the tag of the one held, and not read again when it did not change`() = runBlocking {
        val place = Place(CatalogAnswer.Body(catalog("Fresh"), "\"v1\""))
        val repository = repository(place)
        repository.refreshIfStale()
        clock += 4 * 60 * 60 * 1000L
        place.answer = CatalogAnswer.NotModified
        repository.refreshIfStale()
        assertEquals(listOf(null, "\"v1\""), place.asked)
        assertEquals(listOf("Fresh"), repository.names())
    }

    @Test
    fun `a catalog a few hours old is not asked again`() = runBlocking {
        val place = Place(CatalogAnswer.Body(catalog("Fresh"), null))
        val repository = repository(place)
        repository.refreshIfStale()
        clock += 60 * 60 * 1000L
        repository.refreshIfStale()
        assertEquals(1, place.asked.size)
    }

    @Test
    fun `an answer that is not a catalog never replaces the one kept`() = runBlocking {
        val place = Place(CatalogAnswer.Body(catalog("Fresh"), null))
        val repository = repository(place)
        repository.refreshIfStale()
        clock += 4 * 60 * 60 * 1000L
        place.answer = CatalogAnswer.Body("<html>not found</html>", null)
        repository.refreshIfStale()
        assertEquals(listOf("Fresh"), repository.names())
    }

    @Test
    fun `no network keeps what there is and asks again once the pause is over`() = runBlocking {
        val place = Place(CatalogAnswer.Unavailable)
        val repository = repository(place)
        repository.refreshIfStale()
        clock += 11 * 60 * 1000L
        repository.refreshIfStale()
        assertEquals(2, place.asked.size)
        assertEquals(listOf("Shipped"), repository.names())
    }

    @Test
    fun `without a shipped catalog or a kept one there is none`() {
        assertNull(runBlocking { repository(Place(CatalogAnswer.Unavailable), shipped = null).observe().first() })
    }

    @Test
    fun `a network that gave no answer is left alone for a few minutes, then asked again`() = runBlocking {
        val place = Place(CatalogAnswer.Unavailable)
        val repository = repository(place)
        repository.refreshIfStale()
        repository.refreshIfStale()
        repository.refreshIfStale()
        assertEquals(1, place.asked.size)
        clock += 11 * 60 * 1000L
        repository.refreshIfStale()
        assertEquals(2, place.asked.size)
    }

    @Test
    fun `adult apps are left out unless the player may see them`() {
        val text = """{"v":1,"count":2,"apps":[{"appid":1,"name":"Adult","tags":[6650]},{"appid":2,"name":"Other"}]}"""
        val canSee = kotlinx.coroutines.flow.MutableStateFlow(false)
        val repo = AndroidCatalogRepository(File(temp.root, "adult").also { it.mkdirs() }, { text }, Place(CatalogAnswer.Unavailable), canSee) { clock }
        assertEquals(listOf("Other"), runBlocking { repo.observe().first()?.apps?.map { it.name } })
        assertEquals(listOf("Other"), repo.current?.apps?.map { it.name })
        canSee.value = true
        assertEquals(listOf("Adult", "Other"), runBlocking { repo.observe().first()?.apps?.map { it.name } })
    }
}
