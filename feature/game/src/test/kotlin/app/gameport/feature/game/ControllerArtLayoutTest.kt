package app.gameport.feature.game

import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerLayout
import app.gameport.core.model.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The geometry of the drawn controllers: what is where, what a touch hits, what is lit and in which colour. */
class ControllerArtLayoutTest {
    private fun groups(style: ControllerStyle, hand: Hand) = ControllerArtLayout.zones(style, hand).filter { !it.decor }.map { it.group }.toSet()

    @Test
    fun `the left Steam Frame controller has a D-pad, a view button and a system button`() {
        val left = groups(ControllerStyle.FRAME, Hand.LEFT)
        assertTrue(left.containsAll(setOf("dpad_up", "dpad_down", "dpad_left", "dpad_right", "thumbstick", "view", "system", "shoulder", "trigger", "squeeze")))
        assertFalse("a" in left)
    }

    @Test
    fun `the right Steam Frame controller has four face buttons and a menu button`() {
        val right = groups(ControllerStyle.FRAME, Hand.RIGHT)
        assertTrue(right.containsAll(setOf("a", "b", "x", "y", "menu", "thumbstick", "system", "shoulder", "trigger", "squeeze")))
        assertFalse(right.any { it.startsWith("dpad_") })
    }

    @Test
    fun `the face buttons form a diamond with Y up, X left, B right and A down`() {
        val z = ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.RIGHT).associateBy { it.group }
        assertTrue(z.getValue("y").cy < z.getValue("a").cy)
        assertTrue(z.getValue("x").cx < z.getValue("b").cx)
        assertEquals(z.getValue("y").cx, z.getValue("a").cx, 0.001f)
    }

    @Test
    fun `the right controller is the left one in a mirror`() {
        val left = ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.LEFT).associateBy { it.group }
        val right = ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.RIGHT).associateBy { it.group }
        assertEquals(1f - left.getValue("thumbstick").cx, right.getValue("thumbstick").cx, 0.001f)
        val bodyL = ControllerArtLayout.body(ControllerStyle.QUEST, Hand.LEFT)
        val bodyR = ControllerArtLayout.body(ControllerStyle.QUEST, Hand.RIGHT)
        assertEquals(1f - bodyL.handleTo.x, bodyR.handleTo.x, 0.001f)
    }

    @Test
    fun `every device family offers every control the device can receive`() {
        for (style in ControllerStyle.entries.filter { it != ControllerStyle.FRAME }) {
            for (target in ControllerLayout.targets) {
                val zones = ControllerArtLayout.targetZones(style, target.hand).map { it.group }
                assertTrue("$style lacks ${target.key}", target.group in zones)
            }
        }
    }

    @Test
    fun `a Pico has a home and a capture button that are drawn but not lit`() {
        val left = ControllerArtLayout.zones(ControllerStyle.PICO, Hand.LEFT)
        val right = ControllerArtLayout.zones(ControllerStyle.PICO, Hand.RIGHT)
        assertTrue(left.single { it.group == "system" }.decor)
        assertEquals(0xFFFF8A3DL, left.single { it.group == "system" }.ring)
        assertEquals(0xFF3D8BFFL, right.single { it.group == "system" }.ring)
        assertTrue(right.single { it.group == "capture" }.decor)
        assertTrue(ControllerArtLayout.targetZones(ControllerStyle.PICO, Hand.LEFT).none { it.group == "system" })
    }

    @Test
    fun `the style comes from the family of the device`() {
        assertEquals(ControllerStyle.QUEST, ControllerStyle.ofDeviceFamily("meta"))
        assertEquals(ControllerStyle.PICO, ControllerStyle.ofDeviceFamily("pico"))
        assertEquals(ControllerStyle.GENERIC, ControllerStyle.ofDeviceFamily("openxr"))
        assertEquals(ControllerStyle.GENERIC, ControllerStyle.ofDeviceFamily(null))
    }

    @Test
    fun `each group of controls has its own colour`() {
        assertEquals(ControlColorGroup.TRIGGER, ControllerArtLayout.colorGroupOf("trigger"))
        assertEquals(ControlColorGroup.TRIGGER, ControllerArtLayout.colorGroupOf("bumper"))
        assertEquals(ControlColorGroup.GRIP, ControllerArtLayout.colorGroupOf("squeeze"))
        assertEquals(ControlColorGroup.STICK, ControllerArtLayout.colorGroupOf("thumbstick"))
        for (g in listOf("a", "b", "x", "y")) assertEquals(ControlColorGroup.FACE, ControllerArtLayout.colorGroupOf(g))
        assertEquals(ControlColorGroup.DPAD, ControllerArtLayout.colorGroupOf("dpad_left"))
        for (g in listOf("menu", "view", "system")) assertEquals(ControlColorGroup.SYSTEM, ControllerArtLayout.colorGroupOf(g))
        assertEquals(ControlColorGroup.entries.size, ControlColorGroup.entries.map { it.argb }.distinct().size)
    }

    @Test
    fun `a touch on a control hits it and a touch on the body hits nothing`() {
        val zones = ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.RIGHT)
        for (zone in zones.filter { !it.decor }) assertEquals(zone.group, ControllerArtLayout.zoneAt(zones, zone.cx, zone.cy)?.group)
        assertNull(ControllerArtLayout.zoneAt(zones, 0.5f, 0.66f))
        assertNull(ControllerArtLayout.zoneAt(zones, 0.05f, 1.45f))
    }

    @Test
    fun `a touch between two controls goes to the nearest`() {
        val zones = ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.LEFT)
        val up = zones.first { it.group == "dpad_up" }
        assertEquals("dpad_up", ControllerArtLayout.zoneAt(zones, up.cx, up.cy + 0.01f)?.group)
    }

    @Test
    fun `a decor zone cannot be touched`() {
        val zones = ControllerArtLayout.zones(ControllerStyle.PICO, Hand.LEFT)
        val home = zones.first { it.group == "system" }
        assertNotNull(home)
        assertNull(ControllerArtLayout.zoneAt(zones, home.cx, home.cy))
    }

    @Test
    fun `a bumper and a shoulder are the same zone`() {
        val zones = ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.LEFT)
        val zone = zones.first { it.group == "shoulder" }
        assertEquals(zone, ControllerArtLayout.zoneOf(zones, ControlRef(Hand.LEFT, "bumper")))
        assertEquals(ControlRef(Hand.LEFT, "bumper"), ControllerArtLayout.sourceFor(zone, Hand.LEFT, listOf(ControlRef(Hand.LEFT, "bumper"))))
        assertNull(ControllerArtLayout.sourceFor(zone, Hand.RIGHT, listOf(ControlRef(Hand.LEFT, "bumper"))))
    }

    @Test
    fun `only the controls the game uses are lit on the game side`() {
        val detected = listOf(ControlRef(Hand.LEFT, "thumbstick"), ControlRef(Hand.LEFT, "dpad_up"), ControlRef(Hand.RIGHT, "a"))
        val left = ControllerArtLayout.gameLights(Hand.LEFT, detected, null)
        assertEquals(setOf("thumbstick", "dpad_up"), left.keys)
        assertEquals(ControlColorGroup.STICK, left.getValue("thumbstick").color)
        assertEquals(ControlColorGroup.DPAD, left.getValue("dpad_up").color)
        assertTrue(left.values.all { it.emphasised })
    }

    @Test
    fun `selecting a control makes the others stand back`() {
        val detected = listOf(ControlRef(Hand.LEFT, "thumbstick"), ControlRef(Hand.LEFT, "dpad_up"))
        val left = ControllerArtLayout.gameLights(Hand.LEFT, detected, ControlRef(Hand.LEFT, "dpad_up"))
        assertTrue(left.getValue("dpad_up").emphasised)
        assertFalse(left.getValue("thumbstick").emphasised)
    }

    @Test
    fun `the destination lights up in the colour of the control that goes there`() {
        val allocation = mapOf(ControlRef(Hand.LEFT, "view") to listOf(ControlRef(Hand.LEFT, "menu")), ControlRef(Hand.RIGHT, "a") to listOf(ControlRef(Hand.RIGHT, "a")))
        val left = ControllerArtLayout.deviceLights(Hand.LEFT, allocation, null)
        assertEquals(setOf("menu"), left.keys)
        assertEquals(ControlColorGroup.SYSTEM, left.getValue("menu").color)
        assertEquals(ControlColorGroup.FACE, ControllerArtLayout.deviceLights(Hand.RIGHT, allocation, null).getValue("a").color)
    }

    @Test
    fun `two controls on one button make it shared`() {
        val allocation = mapOf(
            ControlRef(Hand.LEFT, "dpad_up") to listOf(ControlRef(Hand.LEFT, "y")),
            ControlRef(Hand.LEFT, "dpad_right") to listOf(ControlRef(Hand.LEFT, "y")),
            ControlRef(Hand.LEFT, "dpad_down") to listOf(ControlRef(Hand.LEFT, "x")),
        )
        val unfocused = ControllerArtLayout.deviceLights(Hand.LEFT, allocation, null)
        assertTrue(unfocused.getValue("y").shared)
        assertFalse(unfocused.getValue("x").shared)
        val focused = ControllerArtLayout.deviceLights(Hand.LEFT, allocation, ControlRef(Hand.LEFT, "dpad_down"))
        assertTrue(focused.getValue("x").emphasised)
        assertFalse(focused.getValue("y").emphasised)
    }

    @Test
    fun `a shared button carries how many controls land on it, and the selected control is flagged on both drawings`() {
        val y = ControlRef(Hand.LEFT, "y")
        val up = ControlRef(Hand.LEFT, "dpad_up")
        val allocation = mapOf(
            up to listOf(y),
            ControlRef(Hand.LEFT, "dpad_right") to listOf(y),
            ControlRef(Hand.LEFT, "trigger") to listOf(ControlRef(Hand.LEFT, "x")),
        )
        val device = ControllerArtLayout.deviceLights(Hand.LEFT, allocation, up)
        assertEquals(2, device.getValue("y").shareCount)
        assertTrue(device.getValue("y").selected)
        assertFalse(device.getValue("x").selected)
        assertEquals(1, device.getValue("x").shareCount)
        val game = ControllerArtLayout.gameLights(Hand.LEFT, listOf(up, ControlRef(Hand.LEFT, "trigger")), up)
        assertTrue(game.getValue("dpad_up").selected)
        assertFalse(game.getValue("trigger").selected)
    }

    @Test
    fun `a link to the control of the same name is obvious and has no arrow, the others are outlined at both ends`() {
        val trigger = ControlRef(Hand.LEFT, "trigger")
        val up = ControlRef(Hand.LEFT, "dpad_up")
        val view = ControlRef(Hand.LEFT, "view")
        val y = ControlRef(Hand.LEFT, "y")
        val menu = ControlRef(Hand.LEFT, "menu")
        val allocation = mapOf(trigger to listOf(trigger), up to listOf(y), view to listOf(menu), ControlRef(Hand.LEFT, "dpad_down") to emptyList())
        assertTrue(ControllerArtLayout.isObvious(trigger, listOf(trigger)))
        assertFalse(ControllerArtLayout.isObvious(up, listOf(y)))
        val linked = ControllerArtLayout.notObvious(allocation)
        assertEquals(setOf(up, view), linked)
        val device = ControllerArtLayout.deviceLights(Hand.LEFT, allocation, null, linked)
        assertTrue(device.getValue("y").linked)
        assertFalse(device.getValue("trigger").linked)
        val game = ControllerArtLayout.gameLights(Hand.LEFT, listOf(up, trigger), null, linked)
        assertTrue(game.getValue("dpad_up").linked)
        assertFalse(game.getValue("trigger").linked)
    }

    @Test
    fun `every control and the handle stay inside the drawing, whatever the controller`() {
        for (style in ControllerStyle.entries) for (hand in Hand.entries) {
            val body = ControllerArtLayout.body(style, hand)
            assertTrue("$style $hand handle", body.handleTo.y + body.handleWidth / 2 <= ControllerArtLayout.HEIGHT)
            for (zone in ControllerArtLayout.zones(style, hand)) {
                assertTrue("$style $hand ${zone.group}", zone.cy + zone.h / 2 <= ControllerArtLayout.HEIGHT && zone.cy - zone.h / 2 >= 0f)
                assertTrue("$style $hand ${zone.group}", zone.cx - zone.w / 2 >= 0f && zone.cx + zone.w / 2 <= 1f)
            }
        }
    }

    @Test
    fun `on the Steam Frame the trigger is in front of the smaller bumper, never side by side`() {
        val zones = ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.LEFT)
        val bumper = zones.first { it.group == "shoulder" }
        val trigger = zones.first { it.group == "trigger" }
        assertTrue(trigger.w > bumper.w)
        assertEquals(bumper.cx, trigger.cx, 0.0001f)
        assertTrue(zones.indexOf(bumper) < zones.indexOf(trigger))
    }

    @Test
    fun `a control that goes nowhere lights nothing`() {
        assertTrue(ControllerArtLayout.deviceLights(Hand.LEFT, mapOf(ControlRef(Hand.LEFT, "view") to emptyList()), null).isEmpty())
    }

    @Test
    fun `every zone stays inside its controller`() {
        for (style in ControllerStyle.entries) for (hand in Hand.entries) for (z in ControllerArtLayout.zones(style, hand)) {
            assertTrue("$style $hand ${z.group}", z.cx - z.w / 2 >= 0f && z.cx + z.w / 2 <= 1f && z.cy - z.h / 2 >= 0f && z.cy + z.h / 2 <= ControllerArtLayout.HEIGHT)
        }
    }
}
