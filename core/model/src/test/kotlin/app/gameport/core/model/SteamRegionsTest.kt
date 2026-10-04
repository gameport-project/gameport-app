package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamRegionsTest {
    @Test
    fun `each region has its own number and a name, and automatic is not one of them`() {
        assertEquals(SteamRegions.all.size, SteamRegions.all.map { it.first }.toSet().size)
        assertTrue(SteamRegions.all.none { it.first == SteamRegions.AUTOMATIC || it.second.isBlank() })
    }

    @Test
    fun `a region is found by its number, and an unknown one is not`() {
        assertEquals("France - Paris", SteamRegions.nameOf(14))
        assertNull(SteamRegions.nameOf(0))
        assertNull(SteamRegions.nameOf(99999))
    }

    @Test
    fun `the list is in alphabetical order, for the player to find a region`() {
        val names = SteamRegions.all.map { it.second.lowercase() }
        assertEquals(names.sorted(), names)
    }
}
