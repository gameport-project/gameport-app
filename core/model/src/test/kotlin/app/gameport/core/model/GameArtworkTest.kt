package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameArtworkTest {
    private val base = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps"

    @Test
    fun `the published file name wins over the old one`() {
        val game = Game(10, "Demo", Ownership.OWNED, null, AppKind.DEMO, Artwork(capsule = "abc/library_capsule.jpg"))
        assertEquals("$base/10/abc/library_capsule.jpg", game.capsuleUrl)
        assertEquals("$base/10/library_600x900.jpg", game.capsuleFallbacks.first())
    }

    @Test
    fun `without published names the old file names are used`() {
        val game = Game(10, "Game", Ownership.OWNED, null)
        assertEquals("$base/10/library_600x900.jpg", game.capsuleUrl)
        assertEquals("$base/10/library_hero.jpg", game.heroUrl)
        assertEquals("$base/10/header.jpg", game.headerUrl)
    }

    @Test
    fun `the full game's artwork comes last for a demo`() {
        val game = Game(10, "Demo", Ownership.OWNED, null, AppKind.DEMO, parentAppId = 7)
        assertEquals("$base/7/library_600x900.jpg", game.capsuleFallbacks[game.capsuleFallbacks.size - 2])
        assertEquals("$base/7/header.jpg", game.capsuleFallbacks.last())
    }

    @Test
    fun `a game with no parent has no parent artwork`() {
        val game = Game(10, "Game", Ownership.OWNED, null)
        assertTrue(game.capsuleFallbacks.none { it.contains("/apps/7/") })
    }
}
