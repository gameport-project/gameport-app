package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceNeedsTest {
    private val gb = 1_000_000_000L

    @Test
    fun `an install needs the size of its files, not twice that`() {
        // Escape Simulator: 18.16 GB for the base and about 2 GB for one extra, whose APK is only 200 MB.
        val needed = SpaceNeeds.toInstall(20_100_000_000L, alreadyThere = 0)
        assertEquals(20_100_000_000L + SpaceNeeds.MARGIN_BYTES, needed)
        assertTrue(needed < 21 * gb)
    }

    @Test
    fun `what a resumed download already holds is not needed again`() {
        assertEquals(10 * gb + SpaceNeeds.MARGIN_BYTES, SpaceNeeds.toInstall(20 * gb, alreadyThere = 10 * gb))
    }

    @Test
    fun `patching needs a copy of the APKs only`() {
        assertEquals(200_600_000L + SpaceNeeds.MARGIN_BYTES, SpaceNeeds.toPatch(200_600_000L))
    }
}
