package app.gameport.core.steam

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppNameRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val file get() = File(temp.root, "names.json")

    private class Steam(private val answers: Map<Int, String>?) : AppNameLookup {
        val asked = mutableListOf<List<Int>>()
        override suspend fun names(appIds: List<Int>): Map<Int, String>? {
            asked += appIds
            return answers?.filterKeys { it in appIds }
        }
    }

    @Test
    fun `a name found is kept, and read again after a restart without asking Steam`() = runBlocking {
        val steam = Steam(mapOf(846470 to "Moss"))
        val first = AppNameRepository(file, steam)
        first.resolve(listOf(846470))
        assertEquals(mapOf(846470 to "Moss"), first.names.value)

        val later = Steam(emptyMap())
        val second = AppNameRepository(file, later)
        second.resolve(listOf(846470))
        assertEquals("Moss", second.names.value[846470])
        assertEquals(emptyList<List<Int>>(), later.asked)
    }

    @Test
    fun `only the names not known are asked for`() = runBlocking {
        val steam = Steam(mapOf(1 to "A", 2 to "B"))
        val repository = AppNameRepository(file, steam)
        repository.resolve(listOf(1))
        repository.resolve(listOf(1, 2))
        assertEquals(listOf(listOf(1), listOf(2)), steam.asked)
    }

    @Test
    fun `an app Steam does not name is not asked about again, but a failed ask is retried`() = runBlocking {
        val unnamed = Steam(emptyMap())
        val repository = AppNameRepository(file, unnamed)
        repository.resolve(listOf(7))
        repository.resolve(listOf(7))
        assertEquals(1, unnamed.asked.size)

        val offline = Steam(null)
        val other = AppNameRepository(File(temp.root, "other.json"), offline)
        other.resolve(listOf(7))
        other.resolve(listOf(7))
        assertEquals(2, offline.asked.size)
    }
}
