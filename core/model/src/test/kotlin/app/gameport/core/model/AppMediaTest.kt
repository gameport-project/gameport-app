package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppMediaTest {
    private val file = """{"v":1,"count":1,"apps":{"7":{"s":["aa/ss_aa","ss_bb"],"t":[{"n":"T","p":"9/x/movie_600x337.jpg","h":"7/1/hls_264_master.m3u8","m":"7/1/microtrailer.mp4"}]}}}"""

    @Test
    fun `a screenshot path gives the thumbnail and the full size`() {
        val media = MediaCatalog.parse(file)!!.of(7)!!
        assertEquals(listOf("aa/ss_aa", "ss_bb"), media.s)
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/7/aa/ss_aa.600x338.jpg", media.screenshotThumbnail(7, "aa/ss_aa"))
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/7/aa/ss_aa.1920x1080.jpg", media.screenshotFull(7, "aa/ss_aa"))
        // An older app keeps its screenshots directly under its folder.
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/7/ss_bb.600x338.jpg", media.screenshotThumbnail(7, "ss_bb"))
    }

    @Test
    fun `a trailer plays its stream first, then its loop, and has a thumbnail`() {
        val trailer = MediaCatalog.parse(file)!!.of(7)!!.t.single()
        assertEquals(
            listOf("https://video.akamai.steamstatic.com/store_trailers/7/1/hls_264_master.m3u8", "https://video.akamai.steamstatic.com/store_trailers/7/1/microtrailer.mp4"),
            trailer.sources,
        )
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/9/x/movie_600x337.jpg", trailer.thumbnail)
    }

    @Test
    fun `an app with no entry, an empty one, or a file that is not ours gives nothing`() {
        assertNull(MediaCatalog.parse(file)!!.of(8))
        assertNull(MediaCatalog.parse("""{"v":1,"apps":{"1":{}}}""")!!.of(1))
        assertNull(MediaCatalog.parse("not json"))
        assertNull(MediaCatalog.parse("""{"v":2,"apps":{}}"""))
        assertNotNull(MediaCatalog.parse("""{"v":1,"apps":{}}"""))
    }
}
