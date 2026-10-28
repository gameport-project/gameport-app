package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledGamesTest {
    @Test
    fun `a game and its demo share a package and the one recorded last owns it`() {
        val recorded = linkedMapOf(3748900 to "com.studio.puzzling", 1435790 to "com.studio.other", 3530820 to "com.studio.puzzling")
        assertEquals(mapOf(1435790 to "com.studio.other", 3530820 to "com.studio.puzzling"), InstalledGames.oneAppPerPackage(recorded))
    }

    @Test
    fun `packages that belong to one app each are left as they are`() {
        val recorded = linkedMapOf(1 to "a", 2 to "b", 3 to "c")
        assertEquals(recorded, InstalledGames.oneAppPerPackage(recorded))
    }

    @Test
    fun `an empty record stays empty`() {
        assertEquals(emptyMap<Int, String>(), InstalledGames.oneAppPerPackage(emptyMap()))
    }
}
