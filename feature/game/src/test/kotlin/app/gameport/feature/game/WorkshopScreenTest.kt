package app.gameport.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Workshop page opens on the web pages of Steam and stays there. */
class WorkshopScreenTest {
    @Test
    fun `the Workshop of a game is the page of its Workshop on the community of Steam`() {
        assertEquals("https://steamcommunity.com/app/1261500/workshop/", workshopUrl(1261500))
    }

    @Test
    fun `the pages of the community and of the store of Steam are followed`() {
        assertTrue(isSteamPage("steamcommunity.com"))
        assertTrue(isSteamPage("store.steampowered.com"))
        assertTrue(isSteamPage("login.steampowered.com"))
        assertTrue(isSteamPage("STEAMCOMMUNITY.COM"))
    }

    @Test
    fun `a link that leaves Steam, or imitates it, is not followed`() {
        assertFalse(isSteamPage("example.com"))
        assertFalse(isSteamPage("steamcommunity.com.evil.example"))
        assertFalse(isSteamPage("notsteamcommunity.com"))
        assertFalse(isSteamPage(null))
    }
}
