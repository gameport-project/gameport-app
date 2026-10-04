package app.gameport.core.steam

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {
    private val bytes = mapOf(1 to 18_000L, 2 to 500L, 3 to 500L)

    @Test
    fun `small depots that are done do not make a big one look nearly done`() {
        // The 18 GB depot is at 3 %, the two small ones are finished: about 8 % of the bytes, not 67 %.
        val progress = DownloadProgress.overall(listOf(1, 2, 3), mapOf(1 to 0.03f, 2 to 1f, 3 to 1f), bytes)
        assertEquals(0.08f, progress, 0.005f)
    }

    @Test
    fun `a depot that has not reported yet counts as nothing done`() {
        assertEquals(0.5f * 18_000f / 19_000f, DownloadProgress.overall(listOf(1, 2, 3), mapOf(1 to 0.5f), bytes), 0.0001f)
    }

    @Test
    fun `without sizes every depot weighs the same, and the end is one`() {
        assertEquals(0.5f, DownloadProgress.overall(listOf(1, 2), mapOf(1 to 1f, 2 to 0f), emptyMap()), 0.0001f)
        assertEquals(1f, DownloadProgress.overall(listOf(1, 2, 3), mapOf(1 to 1f, 2 to 1f, 3 to 1f), bytes), 0.0001f)
        assertEquals(0f, DownloadProgress.overall(emptyList(), emptyMap(), emptyMap()), 0f)
    }
}
