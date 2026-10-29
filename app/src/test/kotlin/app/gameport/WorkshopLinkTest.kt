package app.gameport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which links GamePort takes: the Workshop page of a game on Steam's community, nothing else. */
class WorkshopLinkTest {
    @Test
    fun `the Workshop page of a game gives the game`() {
        assertEquals(1261500, workshopAppId("https", "steamcommunity.com", listOf("app", "1261500", "workshop")))
        assertEquals(1261500, workshopAppId("https", "www.steamcommunity.com", listOf("app", "1261500", "workshop", "browse")))
        assertEquals(1261500, workshopAppId("http", "STEAMCOMMUNITY.COM", listOf("app", "1261500", "workshop")))
    }

    @Test
    fun `other pages and other sites are left to the browser`() {
        assertNull(workshopAppId("https", "steamcommunity.com", listOf("app", "1261500")))
        assertNull(workshopAppId("https", "steamcommunity.com", listOf("app", "1261500", "discussions")))
        assertNull(workshopAppId("https", "steamcommunity.com", listOf("sharedfiles", "filedetails")))
        assertNull(workshopAppId("https", "steamcommunity.com.evil.example", listOf("app", "1261500", "workshop")))
        assertNull(workshopAppId("ftp", "steamcommunity.com", listOf("app", "1261500", "workshop")))
        assertNull(workshopAppId("https", "steamcommunity.com", listOf("app", "abc", "workshop")))
        assertNull(workshopAppId("https", null, listOf("app", "1", "workshop")))
    }
}
