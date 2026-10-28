package app.gameport.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadFallbackTest {
    private val declared = "%Win%a/save"
    private val older = "%Android%a/save"

    private fun run(names: List<List<String>>, accepts: Set<String>): Pair<UploadFallback.Result?, List<List<String>>> {
        val tried = mutableListOf<List<String>>()
        val result = runBlocking {
            UploadFallback.run(names) { chosen ->
                tried += chosen
                if (chosen.all { it in accepts }) 42L else null
            }
        }
        return result to tried
    }

    @Test
    fun `the name Steam describes is tried first and one attempt is enough when it is taken`() {
        val (result, tried) = run(listOf(listOf(declared, older)), accepts = setOf(declared))

        assertEquals(0, result!!.round)
        assertEquals(listOf(declared), result.names)
        assertEquals(1, tried.size)
    }

    @Test
    fun `when Steam refuses the first name, the next one is tried and it works`() {
        val (result, tried) = run(listOf(listOf(declared, older)), accepts = setOf(older))

        assertEquals(1, result!!.round)
        assertEquals(listOf(older), result.names)
        assertEquals(listOf(listOf(declared), listOf(older)), tried)
    }

    @Test
    fun `nothing is tried again when every name was refused`() {
        val (result, tried) = run(listOf(listOf(declared, older)), accepts = emptySet())

        assertNull(result)
        // Two names, so two attempts: the third round would send what the second did.
        assertEquals(2, tried.size)
    }

    @Test
    fun `a file with a single name is sent once, never again under the same name`() {
        val (result, tried) = run(listOf(listOf(declared)), accepts = emptySet())

        assertNull(result)
        assertEquals(1, tried.size)
    }

    @Test
    fun `several files move on together, one that has no other name keeps its own`() {
        val names = listOf(listOf(declared, older), listOf("%Win%b/save"))

        val (result, tried) = run(names, accepts = setOf(older, "%Win%b/save"))

        assertEquals(listOf(older, "%Win%b/save"), result!!.names)
        assertEquals(listOf(listOf(declared, "%Win%b/save"), listOf(older, "%Win%b/save")), tried)
    }

    @Test
    fun `the attempts are bounded`() {
        val many = listOf((1..10).map { "%R$it%x" })

        val (result, tried) = run(many, accepts = emptySet())

        assertNull(result)
        assertEquals(UploadFallback.MAX_ROUNDS, tried.size)
    }
}
