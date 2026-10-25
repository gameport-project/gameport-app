package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFigureTest {
    private val total = 19_000_000_000L

    @Test
    fun `a claim well above what the device holds is contradicted, whatever the claim`() {
        assertTrue(DownloadFigure.contradicts(0.99f, 13_300_000_000L, total))
        // 99 % shown with 88 % written: what a player saw.
        assertTrue(DownloadFigure.contradicts(0.99f, 16_720_000_000L, total))
        assertTrue(DownloadFigure.contradicts(0.60f, 2_000_000_000L, total))
    }

    @Test
    fun `a claim within the margin of what the device holds is kept`() {
        assertFalse(DownloadFigure.contradicts(0.99f, 18_700_000_000L, total))
        assertFalse(DownloadFigure.contradicts(0.50f, 9_500_000_000L, total))
        assertFalse(DownloadFigure.contradicts(0.30f, 9_000_000_000L, total))
    }

    @Test
    fun `a disk that says nothing, or an unknown total, never contradicts`() {
        assertFalse(DownloadFigure.contradicts(0.99f, 0L, total))
        assertFalse(DownloadFigure.contradicts(0.99f, 5_000_000_000L, 0L))
    }

    @Test
    fun `the figure of the disk is a share of the total, kept between 0 and 1`() {
        assertEquals(0.5f, DownloadFigure.fractionOf(5L, 10L), 0.0001f)
        assertEquals(1f, DownloadFigure.fractionOf(30L, 10L), 0f)
    }
}
