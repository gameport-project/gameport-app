package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerPresetTest {
    private fun ref(key: String) = checkNotNull(ControlRef.parse(key))

    @Test
    fun `a preset sends only the controls the game uses`() {
        val used = listOf(ref("right:x"), ref("right:a"), ref("left:dpad_up"))
        val changes = ControllerPreset.FACE_BUTTONS.changesFor(used)
        assertEquals(setOf(ref("right:x"), ref("right:a")), changes.keys)
        assertEquals(listOf(ref("left:x")), changes.getValue(ref("right:x")))
        assertEquals(listOf(ref("right:a")), changes.getValue(ref("right:a")))
    }

    @Test
    fun `the vertical D-pad goes up on Y and down on X, the horizontal one left on X and right on Y`() {
        val vertical = ControllerPreset.DPAD_VERTICAL.links
        assertEquals(listOf(ref("left:y")), vertical.getValue(ref("left:dpad_up")))
        assertEquals(listOf(ref("left:x")), vertical.getValue(ref("left:dpad_down")))
        val horizontal = ControllerPreset.DPAD_HORIZONTAL.links
        assertEquals(listOf(ref("left:x")), horizontal.getValue(ref("left:dpad_left")))
        assertEquals(listOf(ref("left:y")), horizontal.getValue(ref("left:dpad_right")))
    }

    @Test
    fun `an allocation matches a preset when every control of it that the game uses goes where the preset sends it`() {
        val allocation = mapOf(ref("left:dpad_up") to listOf(ref("left:y")), ref("left:dpad_down") to listOf(ref("left:x")), ref("left:trigger") to listOf(ref("left:trigger")))
        assertTrue(ControllerPreset.DPAD_VERTICAL.matches(allocation))
        assertEquals(ControllerPreset.DPAD_VERTICAL, ControllerPreset.matching(allocation))
    }

    @Test
    fun `one control sent elsewhere is no match, and a game that uses none of the controls matches nothing`() {
        val moved = mapOf(ref("left:dpad_up") to listOf(ref("left:x")), ref("left:dpad_down") to listOf(ref("left:x")))
        assertFalse(ControllerPreset.DPAD_VERTICAL.matches(moved))
        assertNull(ControllerPreset.matching(mapOf(ref("left:trigger") to listOf(ref("left:trigger")))))
    }

    @Test
    fun `applying a preset then reading it back matches it`() {
        val used = listOf(ref("right:x"), ref("right:y"), ref("right:a"), ref("right:b"))
        val mapping = ControllerMapping(detected = used, source = "valve", enabled = true)
        val chosen = ControllerPreset.FACE_BUTTONS.changesFor(used).mapKeys { it.key.key }.mapValues { ControllerLayout.encodeTargets(it.value) }
        val applied = mapping.copy(overrides = chosen)
        assertTrue(ControllerPreset.FACE_BUTTONS.matches(applied.allocation()))
    }
}
