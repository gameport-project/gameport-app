package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A record written by the version before the choice of controllers presented to the game must read back without loss. */
class ControllerMappingCodecTest {
    private fun c(key: String) = ControlRef.parse(key)!!

    // The exact text the previous version wrote: nine parts, version mark in the sixth.
    private val oldRecord = "left:thumbstick,right:a,left:dpad_up|1|right:a=right:b;left:dpad_up=none|1|valve|v2|1|1|1"

    @Test
    fun `an old nine part record reads back with every field`() {
        val m = ControllerMappingCodec.decode(oldRecord)
        assertEquals(listOf(c("left:thumbstick"), c("right:a"), c("left:dpad_up")), m.detected)
        assertTrue(m.enabled)
        assertEquals(mapOf("right:a" to "right:b", "left:dpad_up" to "none"), m.overrides)
        assertTrue(m.noticeSeen)
        assertEquals("valve", m.source)
        assertTrue(m.useFrame)
        assertTrue(m.both)
        assertTrue(m.silent)
    }

    @Test
    fun `an old record without the frame choice reads back as not forced`() {
        val m = ControllerMappingCodec.decode("left:thumbstick|0||0|valve|v2|0|0|0")
        assertFalse(m.useFrame)
        assertFalse(m.forced)
        assertTrue(m.automatic)
    }

    @Test
    fun `the current writer keeps the nine old parts as they were`() {
        assertTrue(ControllerMappingCodec.encode(ControllerMappingCodec.decode(oldRecord)).startsWith("$oldRecord|"))
    }

    @Test
    fun `decode of an old six part record gives automatic`() {
        val m = ControllerMappingCodec.decode("left:thumbstick|0||0|valve|v2")
        assertEquals(PresentedControllers.AUTO, m.presented)
        assertTrue(m.profiles.isEmpty())
        assertEquals("", m.used)
        assertEquals("", m.presentedResult)
    }

    @Test
    fun `decode of a nine part record with the frame forced gives frame`() {
        assertEquals(PresentedControllers.FRAME, ControllerMappingCodec.decode(oldRecord).presented)
    }

    @Test
    fun `a record from before the choice that forced nothing is automatic and not forced`() {
        val m = ControllerMappingCodec.decode("left:thumbstick|0||0|valve|v2|0|0|0")
        assertEquals(PresentedControllers.AUTO, m.presented)
    }

    @Test
    fun `touch survives a round trip with the profiles and what was used`() {
        val m = ControllerMapping(
            presented = PresentedControllers.TOUCH,
            profiles = listOf(ControllerProfile("/interaction_profiles/oculus/touch_controller", 30), ControllerProfile("/interaction_profiles/valve/frame_controller_valve", 33)),
            used = "touch", presentedResult = "applied", source = "valve", both = true,
        )
        val back = ControllerMappingCodec.decode(ControllerMappingCodec.encode(m))
        assertEquals(m, back)
        assertFalse(back.useFrame)
    }

    @Test
    fun `touch is not written as the frame being forced for an older reader`() {
        val record = ControllerMappingCodec.encode(ControllerMapping(presented = PresentedControllers.TOUCH))
        assertEquals("0", record.split('|')[6])
        val frame = ControllerMappingCodec.encode(ControllerMapping(presented = PresentedControllers.FRAME))
        assertEquals("1", frame.split('|')[6])
    }

    @Test
    fun `a part this version does not know is ignored`() {
        val m = ControllerMappingCodec.decode("$oldRecord|touch||native|applied|something|else")
        assertEquals(PresentedControllers.TOUCH, m.presented)
        assertEquals("native", m.used)
        assertEquals("applied", m.presentedResult)
    }

    @Test
    fun `a bad presented, used or result value reads as the default`() {
        val m = ControllerMappingCodec.decode("$oldRecord|banana||weird|nope")
        assertEquals(PresentedControllers.AUTO, m.presented)
        assertEquals("", m.used)
        assertEquals("", m.presentedResult)
    }

    @Test
    fun `profiles with a separator are refused and the list is capped`() {
        assertEquals(null, ControllerProfile.clean("/a|b", 3))
        assertEquals(null, ControllerProfile.clean("/a,b", 3))
        assertEquals(null, ControllerProfile.clean("/a=b", 3))
        assertEquals(null, ControllerProfile.clean("x".repeat(201), 3))
        val many = (1..40).map { ControllerProfile("/p/$it", it) }
        assertEquals(ControllerProfile.MAX_PROFILES, ControllerProfile.cleanAll(many).size)
        val record = ControllerMappingCodec.encode(ControllerMapping(profiles = many + ControllerProfile("/bad|one", 1)))
        assertEquals(ControllerProfile.MAX_PROFILES, ControllerMappingCodec.decode(record).profiles.size)
        assertEquals(14, record.split('|').size)
    }

    @Test
    fun `the chosen preset survives a round trip`() {
        val m = ControllerMapping(detected = listOf(c("left:dpad_up"), c("left:dpad_down")), source = "valve", enabled = true, preset = "dpad_vertical")
        assertEquals("dpad_vertical", ControllerMappingCodec.decode(ControllerMappingCodec.encode(m)).preset)
    }

    @Test
    fun `an old record shorter than the preset part reads back with no preset`() {
        assertEquals("", ControllerMappingCodec.decode(oldRecord).preset)
    }

    @Test
    fun `an unknown preset value reads as none`() {
        assertEquals("", ControllerMappingCodec.decode("$oldRecord|touch|/p/a=3|native|applied|banana").preset)
    }

    @Test
    fun `an entry from the hook is read with a colon, a space or no count`() {
        assertEquals(ControllerProfile("/interaction_profiles/oculus/touch_controller", 30), ControllerProfile.parseEntry("/interaction_profiles/oculus/touch_controller:30"))
        assertEquals(ControllerProfile("/interaction_profiles/khr/simple_controller", 6), ControllerProfile.parseEntry("/interaction_profiles/khr/simple_controller 6"))
        assertEquals(ControllerProfile("/interaction_profiles/valve/index_controller", 0), ControllerProfile.parseEntry("/interaction_profiles/valve/index_controller"))
        assertEquals(null, ControllerProfile.parseEntry("  "))
        assertEquals(null, ControllerProfile.parseEntry("/a|b:3"))
    }

    @Test
    fun `a profile kind comes from its path`() {
        assertEquals(ControllerProfile.Kind.FRAME, ControllerProfile("/interaction_profiles/valve/frame_controller_valve", 1).kind)
        assertEquals(ControllerProfile.Kind.INDEX, ControllerProfile("/interaction_profiles/valve/index_controller", 1).kind)
        assertEquals(ControllerProfile.Kind.TOUCH, ControllerProfile("/interaction_profiles/meta/touch_controller_plus", 1).kind)
        assertEquals(ControllerProfile.Kind.TOUCH, ControllerProfile("/interaction_profiles/oculus/touch_controller", 1).kind)
    }

    @Test
    fun `a record from before the version mark is frozen as it worked then`() {
        val m = ControllerMappingCodec.decode("left:dpad_up,right:a|1|right:a=right:b|0|valve")
        assertEquals("left:y", m.overrides["left:dpad_up"])
        assertEquals("right:b", m.overrides["right:a"])
    }
}
