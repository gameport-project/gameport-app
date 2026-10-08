package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatTest {
    private fun counts(works: Int, fails: Int, offline: Int = 0, device: String = "quest") =
        CompatCounts(1, works, fails, offline, mapOf(device to DeviceCounts(works, fails, offline)))

    private fun level(works: Int, fails: Int) = CompatRules.of(counts(works, fails), "quest")?.level

    @Test
    fun `nothing is said about a game with too few answers`() {
        assertNull(CompatRules.of(null, "quest"))
        assertNull(level(0, 0))
        assertNull(level(1, 0))
        assertNull(level(2, 0))
        assertNull(level(1, 1))
        assertNull(level(0, 2))
    }

    @Test
    fun `a game that works for most players is said to work`() {
        assertEquals(CompatLevel.WORKS, level(3, 0))
        assertEquals(CompatLevel.WORKS, level(3, 1))
        assertEquals(CompatLevel.WORKS, level(30, 10))
    }

    @Test
    fun `a game that fails for most players is said to fail`() {
        assertEquals(CompatLevel.FAILS, level(0, 3))
        assertEquals(CompatLevel.FAILS, level(1, 3))
        assertEquals(CompatLevel.FAILS, level(10, 30))
    }

    @Test
    fun `a game with divided players is said to be mixed`() {
        assertEquals(CompatLevel.MIXED, level(2, 1))
        assertEquals(CompatLevel.MIXED, level(2, 2))
        assertEquals(CompatLevel.MIXED, level(5, 3))
        assertEquals(CompatLevel.MIXED, level(2, 3))
    }

    private fun offline(works: Int, offlineOnly: Int, fails: Int) =
        CompatRules.of(CompatCounts(1, works, fails, 0, mapOf("quest" to DeviceCounts(works, fails, 0, offlineOnly)), offlineOnly), "quest")?.level

    @Test
    fun `a game that works but only offline for at least half of its players is told so`() {
        assertEquals(CompatLevel.OFFLINE_ONLY, offline(0, 3, 0))
        assertEquals(CompatLevel.OFFLINE_ONLY, offline(1, 2, 0))
        assertEquals(CompatLevel.OFFLINE_ONLY, offline(2, 2, 0))
        assertEquals(CompatLevel.OFFLINE_ONLY, offline(1, 3, 1))
    }

    @Test
    fun `a game that works offline only for a few players is said to work`() {
        assertEquals(CompatLevel.WORKS, offline(2, 1, 0))
        assertEquals(CompatLevel.WORKS, offline(5, 2, 1))
    }

    @Test
    fun `the answer offline only is on the side of the players for whom it works`() {
        // 1 works, 1 offline only, 1 fails: two thirds are happy, which is mixed, as 2 works and 1 fails would be.
        assertEquals(CompatLevel.MIXED, offline(1, 1, 1))
        assertEquals(CompatLevel.FAILS, offline(0, 1, 3))
        assertNull(offline(1, 1, 0))
        assertEquals(3, CompatRules.of(CompatCounts(1, 1, 0, 0, mapOf("quest" to DeviceCounts(1, 0, 0, 2)), 2), "quest")!!.players)
    }

    @Test
    fun `only the players of the same kind of device count`() {
        val game = CompatCounts(
            1, 7, 4, 0,
            mapOf("quest" to DeviceCounts(6, 0), "pico" to DeviceCounts(1, 4), "phone" to DeviceCounts(0, 0)),
        )
        assertEquals(CompatLevel.WORKS, CompatRules.of(game, "quest")?.level)
        assertEquals(CompatLevel.FAILS, CompatRules.of(game, "pico")?.level)
        assertNull(CompatRules.of(game, "phone"))
        assertNull(CompatRules.of(game, "tablet"))
        assertEquals("quest", CompatRules.of(game, "quest")?.device)
    }

    @Test
    fun `a relay that does not tell the devices says nothing`() {
        assertNull(CompatRules.of(CompatCounts(1, 9, 0, 0), "quest"))
    }

    @Test
    fun `the offline mode is only told when enough players tried it`() {
        assertFalse(CompatRules.of(counts(7, 1, offline = 2), "quest")!!.offlineTested)
        assertTrue(CompatRules.of(counts(7, 1, offline = 3), "quest")!!.offlineTested)
    }

    @Test
    fun `offline players cannot be more than the players that worked`() {
        assertEquals(4, CompatRules.of(counts(4, 0, offline = 9), "quest")!!.worksOffline)
        assertEquals(0, CompatRules.of(counts(4, 0, offline = -2), "quest")!!.worksOffline)
    }

    @Test
    fun `the summary of the relay is read, with the devices and the offline count optional`() {
        val summary = CompatSummary.parse(
            """{"v":1,"games":[{"appId":1125240,"works":7,"offlineOnly":2,"fails":1,"worksOffline":3,"devices":{"quest":{"works":6,"offlineOnly":2,"fails":0,"worksOffline":3},"pico":{"works":1,"fails":1}}},{"appId":5,"works":0,"fails":2}]}""",
        )!!
        assertEquals(2, summary.of(1125240)?.offlineOnly)
        assertEquals(2, summary.of(1125240)?.devices?.get("quest")?.offlineOnly)
        assertEquals(DeviceCounts(6, 0, 3, 2), summary.of(1125240)?.devices?.get("quest"))
        assertEquals(DeviceCounts(1, 1, 0), summary.of(1125240)?.devices?.get("pico"))
        assertEquals(CompatCounts(5, 0, 2, 0), summary.of(5))
        assertNull(summary.of(99))
    }

    @Test
    fun `a summary that cannot be read, or that is of another version, is nothing`() {
        assertNull(CompatSummary.parse(null))
        assertNull(CompatSummary.parse("not json"))
        assertNull(CompatSummary.parse("""{"error":"not found"}"""))
        assertEquals(emptyList<CompatCounts>(), CompatSummary.parse("""{"v":1,"games":[]}""")!!.games)
        assertNull(CompatSummary.parse("""{"v":2,"games":[]}"""))
    }
}
