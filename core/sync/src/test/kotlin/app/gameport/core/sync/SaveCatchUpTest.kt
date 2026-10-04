package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveCatchUpTest {
    private val installed = mapOf(1 to "pkg.one", 2 to "pkg.two", 3 to "pkg.three")

    @Test
    fun `only games whose sync did not go through are caught up`() {
        val due = gamesToCatchUp(mapOf(1 to SyncStatus.OK, 2 to SyncStatus.OFFLINE, 3 to SyncStatus.FAILED), installed) { false }
        assertEquals(listOf("pkg.two", "pkg.three"), due)
    }

    @Test
    fun `nothing is done when everything is up to date`() {
        assertEquals(emptyList<String>(), gamesToCatchUp(mapOf(1 to SyncStatus.OK, 2 to SyncStatus.OK), installed) { false })
        assertEquals(emptyList<String>(), gamesToCatchUp(emptyMap(), installed) { false })
    }

    @Test
    fun `a game that is on screen is left alone`() {
        assertEquals(listOf("pkg.three"), gamesToCatchUp(mapOf(2 to SyncStatus.OFFLINE, 3 to SyncStatus.OFFLINE), installed) { it == "pkg.two" })
    }

    @Test
    fun `a game that is no longer installed is skipped`() {
        assertEquals(listOf("pkg.one"), gamesToCatchUp(mapOf(1 to SyncStatus.OFFLINE, 9 to SyncStatus.OFFLINE), installed) { false })
    }

    @Test
    fun `games come in a steady order`() {
        assertEquals(listOf("pkg.one", "pkg.two", "pkg.three"), gamesToCatchUp(mapOf(3 to SyncStatus.OFFLINE, 1 to SyncStatus.OFFLINE, 2 to SyncStatus.FAILED), installed) { false })
    }

    @Test
    fun `saves not sent yet are sent too`() {
        val due = gamesToCatchUp(mapOf(1 to SyncStatus.PENDING, 2 to SyncStatus.OK), installed) { false }
        assertEquals(listOf("pkg.one"), due)
    }
}
