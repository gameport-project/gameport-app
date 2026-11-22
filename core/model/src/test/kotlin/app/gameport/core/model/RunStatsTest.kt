package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunStatsTest {
    private fun stats(
        recommended: Pair<Int, Int> = 0 to 0,
        image: Pair<Int, Int> = 0 to 0,
        median: Int = 72,
        low: Int = 70,
        samples: Int = 60,
        target: Int = 72,
        slowest: Int = 0,
        peak: Int = 1000,
        free: Int = 1600,
        startup: Int = 4,
        handedOver: Int = 60,
    ) = RunStats(recommended.first, recommended.second, image.first, image.second, median, low, samples, target, slowest, peak, free, startup, handedOver)

    /** The report that began it: the image of an optimiser's "HD+" mode on a Quest 3, one image a second, and a hook that went quiet at 6 s of a run that lasted 41. */
    private val beatTheBeats = stats(recommended = 2800 to 2933, median = 1, low = 1, samples = 2, slowest = 2, peak = 825, free = 1680, startup = 3, handedOver = 6)

    @Test
    fun `a record is read back as it was written`() {
        val original = stats(recommended = 2064 to 2208, image = 1800 to 1900, median = 71, low = 55, samples = 90, slowest = 3, peak = 900, free = 2100, startup = 12, handedOver = 95)
        assertEquals(original, RunStats.parse(original.write()))
    }

    @Test
    fun `a record of another version or shape is not read`() {
        val good = stats().write()
        assertNull(RunStats.parse(null))
        assertNull(RunStats.parse(""))
        assertNull(RunStats.parse("2 " + good.removePrefix("1 ")))
        assertNull(RunStats.parse("$good 5"))
        assertNull(RunStats.parse(good.substringBeforeLast(' ')))
        assertNull(RunStats.parse(good.replace("72", "seventy")))
    }

    @Test
    fun `what the hook hands over is taken only with the right number of values`() {
        val values = IntArray(RunStats.FIELDS) { it + 1 }
        assertEquals(1, RunStats.fromHandOver(values)?.recommendedWidth)
        assertEquals(13, RunStats.fromHandOver(values)?.handedOverSeconds)
        assertNull(RunStats.fromHandOver(null))
        assertNull(RunStats.fromHandOver(IntArray(RunStats.FIELDS - 1)))
        assertNull(RunStats.fromHandOver(IntArray(RunStats.FIELDS + 1)))
    }

    @Test
    fun `the image shown is the game's own, then the recommended one, then none`() {
        assertEquals(1800 to 1900, stats(recommended = 2064 to 2208, image = 1800 to 1900).shownImage)
        assertEquals(2064 to 2208, stats(recommended = 2064 to 2208).shownImage)
        assertNull(stats().shownImage)
    }

    @Test
    fun `a frame rate needs enough seconds and a target to be said`() {
        assertTrue(stats(samples = 5).hasFrameRate)
        assertFalse(stats(samples = 4).hasFrameRate)
        assertFalse(stats(target = 0).hasFrameRate)
    }

    @Test
    fun `only the devices that were measured have a usual image`() {
        assertEquals(2064 to 2208, RunRules.usualImage("Quest 3"))
        assertEquals(2064 to 2208, RunRules.usualImage("Quest_3"))
        assertEquals(2064 to 2208, RunRules.usualImage(" quest 3 "))
        assertEquals(1920 to 1920, RunRules.usualImage("A9210"))
        assertNull(RunRules.usualImage("Quest 2"))
        assertNull(RunRules.usualImage("unknown"))
        assertNull(RunRules.usualImage(null))
    }

    @Test
    fun `the image of the beat the beats report is about 1,8 times the usual pixels, 134 percent of its size`() {
        val ratio = RunRules.pixelRatio(beatTheBeats, "Quest 3")!!
        assertEquals(1.80, ratio, 0.01)
        assertEquals(134, RunRules.scalePercent(beatTheBeats, "Quest 3"))
        assertTrue(RunRules.highResolution(beatTheBeats, "Quest 3"))
    }

    @Test
    fun `the usual image is not a high resolution, and nor is a little more`() {
        assertFalse(RunRules.highResolution(stats(recommended = 2064 to 2208), "Quest 3"))
        assertFalse(RunRules.highResolution(stats(recommended = 2200 to 2350), "Quest 3"))
        assertTrue(RunRules.highResolution(stats(recommended = 2600 to 2700), "Quest 3"))
    }

    @Test
    fun `the image the game drew is judged, not the one the runtime recommended`() {
        // A player's scale of 175 % raises the image, not the recommendation: the card must say so.
        val s = stats(recommended = 1920 to 1920, image = 3780 to 3780)
        assertTrue(RunRules.highResolution(s, "A9210"))
        assertEquals(196, RunRules.scalePercent(s, "A9210"))
        assertFalse(RunRules.highResolution(stats(recommended = 2600 to 2700, image = 2064 to 2208), "Quest 3"))
    }

    @Test
    fun `no resolution is judged for a device that is not known or a run that measured no image`() {
        assertNull(RunRules.pixelRatio(beatTheBeats, "Quest 2"))
        assertNull(RunRules.pixelRatio(stats(), "Quest 3"))
        assertNull(RunRules.scalePercent(stats(), "Quest 3"))
        assertFalse(RunRules.highResolution(beatTheBeats, "Quest 2"))
    }

    @Test
    fun `a stretch of slow seconds in a row, or a slow median over enough seconds, is a slow run`() {
        assertTrue(RunRules.slow(stats(slowest = RunRules.SLOW_STRETCH_SECONDS)))
        assertFalse(RunRules.slow(stats(slowest = RunRules.SLOW_STRETCH_SECONDS - 1)))
        assertTrue(RunRules.slow(stats(median = 30, samples = 30)))
        assertFalse(RunRules.slow(stats(median = 36, samples = 30)))
        // Too few seconds to say anything of a median.
        assertFalse(RunRules.slow(stats(median = 1, samples = 3, slowest = 3)))
    }

    @Test
    fun `a hook that goes quiet while the run goes on is a game that stopped answering`() {
        assertTrue(RunRules.stoppedAnswering(beatTheBeats, 41))
        assertTrue(RunRules.stoppedAnswering(stats(handedOver = 30), 45))
        assertFalse(RunRules.stoppedAnswering(stats(handedOver = 31), 45))
        // A short run, or a hook that never handed anything over, says nothing.
        assertFalse(RunRules.stoppedAnswering(stats(handedOver = 2), RunRules.MIN_RUN_SECONDS - 1))
        assertFalse(RunRules.stoppedAnswering(stats(handedOver = 0), 100))
    }

    @Test
    fun `the beat the beats run could not keep up`() {
        assertTrue(RunRules.couldNotKeepUp(beatTheBeats, 41))
    }

    @Test
    fun `a run that was killed while it ran fine is not one that could not keep up`() {
        assertFalse(RunRules.couldNotKeepUp(stats(median = 72, samples = 60, handedOver = 10), 120))
    }

    @Test
    fun `a short run, or one that printed no frame rate, is not judged`() {
        assertFalse(RunRules.couldNotKeepUp(stats(slowest = 12), RunRules.MIN_RUN_SECONDS - 1))
        assertFalse(RunRules.couldNotKeepUp(stats(median = 0, samples = 0, target = 0, slowest = 0, handedOver = 5), 100))
    }

    @Test
    fun `the advice is given for the beat the beats run`() {
        assertTrue(RunRules.adviseLowerResolution(beatTheBeats, 41, "Quest 3", chosenPercent = 0, crashed = false))
    }

    @Test
    fun `no advice after a crash, which explains the run by itself`() {
        assertFalse(RunRules.adviseLowerResolution(beatTheBeats, 41, "Quest 3", 0, crashed = true))
    }

    @Test
    fun `no advice to a player who already lowered the resolution, and one for a game's own or full resolution`() {
        assertFalse(RunRules.adviseLowerResolution(beatTheBeats, 41, "Quest 3", 75, false))
        assertFalse(RunRules.adviseLowerResolution(beatTheBeats, 41, "Quest 3", RunRules.ALREADY_LOWERED_PERCENT, false))
        assertTrue(RunRules.adviseLowerResolution(beatTheBeats, 41, "Quest 3", RunRules.ALREADY_LOWERED_PERCENT + 1, false))
        assertTrue(RunRules.adviseLowerResolution(beatTheBeats, 41, "Quest 3", 100, false))
    }

    @Test
    fun `no advice when the resolution is not high, whatever the game did`() {
        val normal = beatTheBeats.copy(recommendedWidth = 2064, recommendedHeight = 2208)
        assertFalse(RunRules.adviseLowerResolution(normal, 41, "Quest 3", 0, false))
    }

    @Test
    fun `no advice for a device that was not measured`() {
        assertFalse(RunRules.adviseLowerResolution(beatTheBeats, 41, "Quest 2", 0, false))
        assertFalse(RunRules.adviseLowerResolution(beatTheBeats, 41, null, 0, false))
    }

    @Test
    fun `no advice when the run was slow but short`() {
        assertFalse(RunRules.adviseLowerResolution(beatTheBeats, 15, "Quest 3", 0, false))
    }

    @Test
    fun `a healthy run on a high resolution is not advised anything`() {
        val healthy = stats(recommended = 2800 to 2933, median = 72, low = 70, samples = 120)
        assertTrue(RunRules.highResolution(healthy, "Quest 3"))
        assertFalse(RunRules.adviseLowerResolution(healthy, 150, "Quest 3", 0, false))
        assertNotNull(RunRules.scalePercent(healthy, "Quest 3"))
    }
}
