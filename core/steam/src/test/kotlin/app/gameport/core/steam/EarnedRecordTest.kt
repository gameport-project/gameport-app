package app.gameport.core.steam

import app.gameport.core.model.Achievement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnedRecordTest {
    private fun achievement(name: String, unlocked: Boolean, at: Long = 0L) =
        Achievement(name = name, title = name, description = "", icon = null, iconGray = null, hidden = false, unlocked = unlocked, unlockedAt = at)

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `what the account has is added to a record that lacks it`() {
        val merged = parse(EarnedRecord.merge("", listOf(achievement("ACH_ONE", true, 1700000000), achievement("ACH_TWO", false)))!!)
        assertEquals(setOf("ACH_ONE"), merged.keys)
        assertEquals(true, merged.getValue("ACH_ONE").jsonObject.getValue("earned").jsonPrimitive.boolean)
        assertEquals(1700000000L, merged.getValue("ACH_ONE").jsonObject.getValue("earned_time").jsonPrimitive.long)
    }

    @Test
    fun `what the game already has is never removed or changed, whatever the account says`() {
        val current = """{"ACH_GAME":{"earned":true,"earned_time":42},"ACH_ONE":{"earned":true,"earned_time":7}}"""
        val merged = parse(EarnedRecord.merge(current, listOf(achievement("ACH_TWO", true, 900), achievement("ACH_ONE", true, 5)))!!)
        assertEquals(setOf("ACH_GAME", "ACH_ONE", "ACH_TWO"), merged.keys)
        assertEquals(42L, merged.getValue("ACH_GAME").jsonObject.getValue("earned_time").jsonPrimitive.long)
        // The time the game recorded is kept.
        assertEquals(7L, merged.getValue("ACH_ONE").jsonObject.getValue("earned_time").jsonPrimitive.long)
    }

    @Test
    fun `an account with fewer achievements than the game removes nothing`() {
        assertNull(EarnedRecord.merge("""{"ACH_GAME":{"earned":true,"earned_time":42}}""", emptyList()))
        assertNull(EarnedRecord.merge("""{"ACH_GAME":{"earned":true,"earned_time":42}}""", listOf(achievement("ACH_OTHER", false))))
    }

    @Test
    fun `a name is matched whatever its case, and keeps the spelling the game uses`() {
        val current = """{"ach_one":{"earned":true,"earned_time":7}}"""
        assertNull(EarnedRecord.merge(current, listOf(achievement("ACH_ONE", true, 5))))
        val merged = parse(EarnedRecord.merge("""{"ach_one":{"earned":false,"earned_time":0}}""", listOf(achievement("ACH_ONE", true, 5)))!!)
        assertEquals(setOf("ach_one"), merged.keys)
        assertTrue(merged.getValue("ach_one").jsonObject.getValue("earned").jsonPrimitive.boolean)
    }

    @Test
    fun `a record that cannot be read is left alone`() {
        assertNull(EarnedRecord.merge("{not json", listOf(achievement("ACH_ONE", true, 5))))
        assertNull(EarnedRecord.merge("[1,2]", listOf(achievement("ACH_ONE", true, 5))))
    }

    @Test
    fun `an achievement without a time is written with an unknown time`() {
        val merged = parse(EarnedRecord.merge("", listOf(achievement("ACH_ONE", true, 1L)))!!)
        assertEquals(0L, merged.getValue("ACH_ONE").jsonObject.getValue("earned_time").jsonPrimitive.long)
    }
}
