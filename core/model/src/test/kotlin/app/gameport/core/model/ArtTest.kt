package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtTest {
    @Test
    fun `candidates keep the order and drop blanks and repeats`() {
        assertEquals(listOf("a", "b", "c"), Art("a", listOf("", "b", "a", "c")).candidates)
        assertEquals(emptyList<String>(), Art("").candidates)
    }

    @Test
    fun `a demo with artwork under hashed names falls back to its full game's cover`() {
        val demo = Game(10, "Demo", Ownership.OWNED, null, AppKind.DEMO, Artwork(capsule = "abc/library_capsule.jpg"), parentAppId = 20)
        val candidates = demo.capsuleArt.candidates
        assertEquals(SteamImages.asset(10, "abc/library_capsule.jpg"), candidates.first())
        assertTrue(SteamImages.asset(20, "library_600x900.jpg") in candidates)
    }

    @Test
    fun `a game with no artwork of its own still gets the standard names`() {
        val game = Game(7, "Plain", Ownership.OWNED, null)
        assertEquals(SteamImages.asset(7, "library_600x900.jpg"), game.capsuleArt.url)
        assertEquals(SteamImages.asset(7, "library_hero.jpg"), game.heroArt.url)
    }

    @Test
    fun `a store entry prefers the slots it is asked for, then the others, then the standard names`() {
        val app = AndroidApp(5, "Entry", cover = "cover", banner = "banner", header = "header", capsule = "capsule")
        assertEquals(listOf("cover", "header", "banner", "capsule"), app.art(ArtSlot.COVER, ArtSlot.HEADER, ArtSlot.BANNER).candidates.take(4))
        assertEquals("banner", app.art(ArtSlot.BANNER, ArtSlot.HEADER, ArtSlot.COVER).url)
        val bare = AndroidApp(6, "Bare")
        assertEquals(Covers.capsule(6), bare.art(ArtSlot.COVER))
    }

    @Test
    fun `an app known only by its number gets the standard cover and header`() {
        assertEquals(
            listOf(SteamImages.asset(3, "library_600x900.jpg"), SteamImages.asset(3, "header.jpg")),
            Covers.capsule(3).candidates,
        )
    }
}
