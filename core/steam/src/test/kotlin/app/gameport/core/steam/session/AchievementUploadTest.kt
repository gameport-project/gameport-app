package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementUploadTest {
    private fun node(name: String, value: String? = null, vararg children: KeyValue) = KeyValue(name, value).also { it.children.addAll(children) }

    private fun bit(index: Int, name: String) = node(index.toString(), null, node("name", name))

    // Two stats hold achievements: 1 has bits 0, 1 and 31; 5 has bit 2. Stat 2 is a plain counter.
    private val schema = node(
        "620", null,
        node(
            "stats", null,
            node("1", null, node("type", "4"), node("bits", null, bit(0, "ACH_ONE"), bit(1, "ACH_TWO"), bit(31, "ACH_LAST"))),
            node("2", null, node("type", "1"), node("name", "kills")),
            node("5", null, node("type", "4"), node("bits", null, bit(2, "ACH_FIVE"))),
        ),
    )
    private val positions = achievementPositions(schema)

    @Test
    fun `the position of an achievement is its stat and its bit, whatever the case of its name`() {
        assertEquals(1 to 0, positions["ach_one"])
        assertEquals(1 to 31, positions["ach_last"])
        assertEquals(5 to 2, positions["ach_five"])
        assertEquals(4, positions.size)
        assertEquals(emptyMap<String, Pair<Int, Int>>(), achievementPositions(node("620")))
    }

    @Test
    fun `a bit is added to what the account has and the other bits stay`() {
        val plan = planUnlocks(positions, mapOf(1 to 0b01), listOf("ACH_TWO"))
        assertEquals(mapOf(1 to 0b11), plan.blocks)
        assertEquals(listOf("ACH_TWO"), plan.toUnlock)
    }

    @Test
    fun `the highest bit is set without touching the others`() {
        val plan = planUnlocks(positions, mapOf(1 to 0b01), listOf("ach_last"))
        assertEquals(mapOf(1 to (0b01 or (1 shl 31))), plan.blocks)
        assertTrue(plan.blocks.getValue(1) < 0)
    }

    @Test
    fun `an achievement the account already has changes nothing`() {
        val plan = planUnlocks(positions, mapOf(1 to 0b11), listOf("ACH_ONE", "ACH_TWO"))
        assertTrue(plan.blocks.isEmpty())
        assertEquals(listOf("ACH_ONE", "ACH_TWO"), plan.alreadyUnlocked)
        assertTrue(plan.toUnlock.isEmpty())
    }

    @Test
    fun `several achievements of one stat are added together, and a stat not yet known starts from nothing`() {
        val plan = planUnlocks(positions, emptyMap(), listOf("ACH_ONE", "ACH_TWO", "ACH_FIVE"))
        assertEquals(mapOf(1 to 0b11, 5 to 0b100), plan.blocks)
        assertEquals(listOf("ACH_ONE", "ACH_TWO", "ACH_FIVE"), plan.toUnlock)
    }

    @Test
    fun `an achievement the schema does not know is left out and told`() {
        val plan = planUnlocks(positions, emptyMap(), listOf("ACH_ONE", "NOPE"))
        assertEquals(mapOf(1 to 1), plan.blocks)
        assertEquals(listOf("NOPE"), plan.unknown)
    }

    @Test
    fun `the same achievement twice is added once`() {
        val plan = planUnlocks(positions, emptyMap(), listOf("ACH_ONE", "ach_one"))
        assertEquals(mapOf(1 to 1), plan.blocks)
        assertEquals(1, plan.toUnlock.size)
    }

    @Test
    fun `a stat that is not an achievement stat is never in the plan`() {
        val plan = planUnlocks(positions, mapOf(2 to 99), listOf("ACH_ONE"))
        assertTrue(2 !in plan.blocks)
    }

    @Test
    fun `an achievement counts as unlocked by its bit, not by its old time`() {
        val times = mapOf(1 to listOf(1000, 2000, 0))
        // Both times are kept by Steam, but only the second bit is set.
        assertEquals(0L, unlockedAt(mapOf(1 to 0b10), times, 1, 0))
        assertEquals(2000L, unlockedAt(mapOf(1 to 0b10), times, 1, 1))
        // Every bit cleared: nothing is unlocked whatever the times say.
        assertEquals(0L, unlockedAt(mapOf(1 to 0), times, 1, 1))
        // A stat Steam does not hold has no unlocked achievement.
        assertEquals(0L, unlockedAt(emptyMap(), times, 1, 0))
    }

    @Test
    fun `a bit set without any time is unlocked at an unknown time`() {
        assertEquals(1L, unlockedAt(mapOf(1 to 0b100), mapOf(1 to listOf(0, 0, 0)), 1, 2))
        assertEquals(1L, unlockedAt(mapOf(1 to 0b100), emptyMap(), 1, 2))
        assertEquals(0L, unlockedAt(mapOf(1 to (1 shl 31)), emptyMap(), 1, 30))
        assertEquals(1L, unlockedAt(mapOf(1 to (1 shl 31)), emptyMap(), 1, 31))
    }
}
