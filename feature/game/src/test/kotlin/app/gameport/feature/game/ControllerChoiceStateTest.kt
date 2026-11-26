package app.gameport.feature.game

import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.ControllerProfile
import app.gameport.core.model.PresentedControllers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerChoiceStateTest {
    private val touch = ControllerProfile("/interaction_profiles/oculus/touch_controller", 30)
    private val frame = ControllerProfile("/interaction_profiles/valve/frame_controller_valve", 33)
    private val index = ControllerProfile("/interaction_profiles/valve/index_controller", 8)
    private val simple = ControllerProfile("/interaction_profiles/khr/simple_controller", 6)

    private fun state(mapping: ControllerMapping, family: String? = "pico") = ControllerChoiceState.of(mapping, family)

    @Test
    fun `never launched or an old patch shows the single switch and a grey line on a headset`() {
        val s = state(ControllerMapping())
        assertFalse(s.choosable)
        assertTrue(s.waitingForLaunch)
    }

    @Test
    fun `a choice kept with no profile known any more still shows its cards, so it can be taken back`() {
        val s = state(ControllerMapping(presented = PresentedControllers.TOUCH))
        assertTrue(s.choosable)
        assertFalse(s.waitingForLaunch)
        assertTrue(PresentedControllers.AUTO in s.offered && PresentedControllers.TOUCH in s.offered)
    }

    @Test
    fun `a device with no VR shows the single switch and says nothing more`() {
        val s = state(ControllerMapping(profiles = listOf(touch, frame)), family = null)
        assertFalse(s.choosable)
        assertFalse(s.waitingForLaunch)
    }

    @Test
    fun `touch is offered on a Pico when the game has a Touch table`() {
        val s = state(ControllerMapping(profiles = listOf(touch, index, simple, frame)))
        assertEquals(listOf(PresentedControllers.AUTO, PresentedControllers.FRAME, PresentedControllers.TOUCH), s.offered)
        assertFalse(s.nothingToChoose)
        assertEquals(4, s.listed.size)
    }

    @Test
    fun `touch is not offered on a Meta device where it is native`() {
        val s = state(ControllerMapping(profiles = listOf(touch, frame)), family = "meta")
        assertEquals(listOf(PresentedControllers.AUTO, PresentedControllers.FRAME), s.offered)
    }

    @Test
    fun `a game with only the Steam Frame table has nothing to choose, and the page says it is normal`() {
        val s = state(ControllerMapping(profiles = listOf(frame)))
        assertTrue(s.nothingToChoose)
        // One set of controls: nothing to choose, so no choice is offered.
        assertEquals(listOf(PresentedControllers.AUTO), s.offered)
        // The generic profiles a game gives next to it (eye gaze, simple controller) are not tables to choose from.
        val withGeneric = state(ControllerMapping(profiles = listOf(frame, simple)))
        assertEquals(listOf(PresentedControllers.AUTO), withGeneric.offered)
    }

    @Test
    fun `a game with its own table and no Steam Frame table has nothing to choose either`() {
        val pico = state(ControllerMapping(profiles = listOf(touch, index, simple)))
        assertEquals(listOf(PresentedControllers.AUTO), pico.offered)
        val meta = state(ControllerMapping(profiles = listOf(touch, index)), family = "meta")
        assertEquals(listOf(PresentedControllers.AUTO), meta.offered)
    }

    @Test
    fun `a game with its own table and the Steam Frame's has a real choice, no explanation`() {
        val s = state(ControllerMapping(profiles = listOf(touch, frame)))
        assertEquals(listOf(PresentedControllers.AUTO, PresentedControllers.FRAME, PresentedControllers.TOUCH), s.offered)
        assertEquals(listOf(PresentedControllers.AUTO, PresentedControllers.FRAME), state(ControllerMapping(profiles = listOf(touch, frame)), family = "meta").offered)
    }

    @Test
    fun `a choice kept from before still shows its cards for a game that has nothing to choose now`() {
        val s = state(ControllerMapping(profiles = listOf(frame), presented = PresentedControllers.TOUCH))
        assertTrue(PresentedControllers.TOUCH in s.offered && s.offered.size > 1)
    }

    @Test
    fun `a game with a Touch table only on a Meta device has nothing to choose`() {
        val s = state(ControllerMapping(profiles = listOf(touch, index)), family = "meta")
        assertEquals(listOf(PresentedControllers.AUTO), s.offered)
        assertTrue(s.nothingToChoose)
    }

    @Test
    fun `a choice made before stays visible even when nothing else is offered`() {
        val s = state(ControllerMapping(profiles = listOf(frame), presented = PresentedControllers.FRAME))
        assertFalse(s.nothingToChoose)
        assertTrue(PresentedControllers.FRAME in s.offered)
        val t = state(ControllerMapping(profiles = listOf(touch, index), presented = PresentedControllers.TOUCH), family = "meta")
        assertTrue(PresentedControllers.TOUCH in t.offered)
    }

    @Test
    fun `the table used at the last start is reported from used and not from the source`() {
        val s = state(ControllerMapping(source = "valve", profiles = listOf(touch, frame), used = "touch"))
        assertEquals("touch", s.used)
        assertEquals(null, state(ControllerMapping(profiles = listOf(touch, frame))).used)
    }

    @Test
    fun `a refused choice is told only while a choice is made`() {
        val refused = ControllerMapping(profiles = listOf(touch, frame), presented = PresentedControllers.TOUCH, presentedResult = "fallback")
        assertTrue(state(refused).fallback)
        assertFalse(state(refused.copy(presented = PresentedControllers.AUTO)).fallback)
        assertFalse(state(refused.copy(presentedResult = "applied")).fallback)
    }

    private val pico = ControllerProfile("/interaction_profiles/bytedance/pico4_controller", 30)

    @Test
    fun `touch is not offered when the game has a Pico profile of its own`() {
        val s = state(ControllerMapping(profiles = listOf(pico, touch, frame)))
        assertEquals(listOf(PresentedControllers.AUTO, PresentedControllers.FRAME), s.offered)
    }

    @Test
    fun `a choice the layer ignored is told in grey, not as a fallback`() {
        val s = state(ControllerMapping(profiles = listOf(touch, frame), presented = PresentedControllers.FRAME, presentedResult = "ignored"))
        assertTrue(s.ignored)
        assertFalse(s.fallback)
        assertFalse(state(ControllerMapping(profiles = listOf(touch, frame), presentedResult = "ignored")).ignored)
    }

    @Test
    fun `automatic says which controllers the game read at its last start and through which profile`() {
        val pico = ControllerProfile("/interaction_profiles/bytedance/pico4_controller", 40)
        val frameUsed = state(ControllerMapping(profiles = listOf(touch, index, frame), used = "frame")).usedTable
        assertEquals(ControllerChoiceState.UsedTable(ControllerProfile.Kind.FRAME), frameUsed)
        val touchUsed = state(ControllerMapping(profiles = listOf(touch, frame), used = "touch")).usedTable
        assertEquals(ControllerChoiceState.UsedTable(ControllerProfile.Kind.TOUCH), touchUsed)
        // The game's own table for a Pico is its Pico profile; for a Meta device, its Touch one.
        assertEquals(ControllerChoiceState.UsedTable(ControllerProfile.Kind.PICO), state(ControllerMapping(profiles = listOf(touch, pico, frame), used = "native")).usedTable)
        assertEquals(ControllerChoiceState.UsedTable(ControllerProfile.Kind.TOUCH), state(ControllerMapping(profiles = listOf(touch, pico, frame), used = "native"), family = "meta").usedTable)
    }

    @Test
    fun `nothing is said in brackets while the table used is not known or has no matching profile`() {
        assertEquals(null, state(ControllerMapping(profiles = listOf(touch, frame))).usedTable)
        assertEquals(null, state(ControllerMapping(profiles = listOf(touch, frame), used = "none")).usedTable)
        assertEquals(null, state(ControllerMapping(profiles = listOf(touch), used = "frame")).usedTable)
        // A native table with no profile of this device's controllers among the ones the game gave.
        assertEquals(null, state(ControllerMapping(profiles = listOf(frame, simple), used = "native")).usedTable)
    }

    @Test
    fun `the warning about no Steam Frame control comes back when the frame is asked for`() {
        val s = state(ControllerMapping(profiles = listOf(touch), source = "none", presented = PresentedControllers.FRAME))
        assertTrue(s.noFrameControlsWarning)
        assertFalse(state(ControllerMapping(profiles = listOf(touch), source = "none")).noFrameControlsWarning)
        assertFalse(state(ControllerMapping(profiles = listOf(touch, frame), source = "valve", presented = PresentedControllers.FRAME)).noFrameControlsWarning)
    }
}
