package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as `xrlayer/test/controller_allocation_test.cpp`: the page shows what the layer does. */
class ControllerMappingTest {
    private fun c(key: String) = ControlRef.parse(key)!!
    private fun set(vararg keys: String) = keys.map(::c).toSet()
    private fun list(vararg keys: String) = keys.map(::c)
    private fun allocate(used: Set<ControlRef>, overrides: Map<ControlRef, List<ControlRef>> = emptyMap()) = ControllerLayout.allocate(used, overrides)

    @Test
    fun `a game made for a Quest and ported uses the D-pad's up and down for Y and X`() {
        val a = allocate(set("left:dpad_up", "left:dpad_down", "right:a", "right:b", "left:thumbstick"))
        assertEquals(list("left:y"), a[c("left:dpad_up")])
        assertEquals(list("left:x"), a[c("left:dpad_down")])
        assertEquals(list("right:a"), a[c("right:a")])
        assertEquals(list("left:thumbstick"), a[c("left:thumbstick")])
        assertTrue(ControllerLayout.shared(a).isEmpty())
    }

    @Test
    fun `a game that uses the D-pad's left and right gets X and Y`() {
        val a = allocate(set("left:dpad_left", "left:dpad_right"))
        assertEquals(list("left:x"), a[c("left:dpad_left")])
        assertEquals(list("left:y"), a[c("left:dpad_right")])
        assertTrue(ControllerLayout.shared(a).isEmpty())
    }

    @Test
    fun `all four directions share when there are not enough buttons`() {
        val a = allocate(set("left:dpad_up", "left:dpad_down", "left:dpad_left", "left:dpad_right"))
        assertEquals(list("left:y"), a[c("left:dpad_up")])
        assertEquals(list("left:x"), a[c("left:dpad_down")])
        assertEquals(list("left:x"), a[c("left:dpad_left")])
        assertEquals(list("left:y"), a[c("left:dpad_right")])
        assertEquals(setOf(c("left:x"), c("left:y")), ControllerLayout.shared(a).keys)
    }

    @Test
    fun `the four face buttons of the Frame each get a button of their own`() {
        val a = allocate(set("right:a", "right:b", "right:x", "right:y"))
        assertEquals(list("right:a"), a[c("right:a")])
        assertEquals(list("right:b"), a[c("right:b")])
        assertEquals(list("left:x"), a[c("right:x")])
        assertEquals(list("left:y"), a[c("right:y")])
        assertTrue(ControllerLayout.shared(a).isEmpty())
    }

    @Test
    fun `face buttons take X and Y first and the D-pad shares them`() {
        val a = allocate(set("right:x", "right:y", "left:dpad_up", "left:dpad_down"))
        assertEquals(list("left:x"), a[c("right:x")])
        assertEquals(list("left:y"), a[c("right:y")])
        assertEquals(list("left:y"), a[c("left:dpad_up")])
        assertEquals(list("left:x"), a[c("left:dpad_down")])
        assertEquals(2, ControllerLayout.shared(a).size)
    }

    @Test
    fun `bumpers go to the squeeze of the same hand and share it with the grip`() {
        val a = allocate(set("left:bumper", "right:bumper"))
        assertEquals(list("left:squeeze"), a[c("left:bumper")])
        assertEquals(list("right:squeeze"), a[c("right:bumper")])
        val b = allocate(set("right:bumper", "right:squeeze"))
        assertEquals(list("right:squeeze"), b[c("right:squeeze")])
        assertEquals(list("right:squeeze"), b[c("right:bumper")])
        assertEquals(2, ControllerLayout.shared(b).getValue(c("right:squeeze")).size)
    }

    @Test
    fun `the right menu goes to the left one, the view buttons and unknown controls go nowhere`() {
        val a = allocate(set("right:menu", "left:view", "right:view", "left:steam"))
        assertEquals(list("left:menu"), a[c("right:menu")])
        assertTrue(a.getValue(c("left:view")).isEmpty() && a.getValue(c("right:view")).isEmpty() && a.getValue(c("left:steam")).isEmpty())
    }

    @Test
    fun `only what the game uses competes`() {
        assertEquals(mapOf(c("left:dpad_up") to list("left:y")), allocate(set("left:dpad_up")))
        assertTrue(allocate(emptySet()).isEmpty())
    }

    @Test
    fun `the player's choices can be several controls or nowhere, and they take the place of the defaults`() {
        val overrides = mapOf(
            c("left:dpad_up") to ControllerLayout.parseTargets("left:y+left:x"),
            c("right:menu") to ControllerLayout.parseTargets("none"),
        )
        val a = allocate(set("left:dpad_up", "left:dpad_down", "right:menu"), overrides)
        assertEquals(list("left:y", "left:x"), a[c("left:dpad_up")])
        assertTrue(a.getValue(c("right:menu")).isEmpty())
        // The choice took X and Y: the D-pad's down finds none free and shares X.
        assertEquals(list("left:x"), a[c("left:dpad_down")])
    }

    @Test
    fun `a choice is written and read back, several controls joined by a plus`() {
        assertEquals("left:y+left:x", ControllerLayout.encodeTargets(list("left:y", "left:x")))
        assertEquals("none", ControllerLayout.encodeTargets(emptyList()))
        assertEquals(list("left:y", "left:x"), ControllerLayout.parseTargets("left:y+left:x+left:y+bad"))
        assertTrue(ControllerLayout.parseTargets("none").isEmpty())
    }

    @Test
    fun `a mapping uses the defaults until the player customises it`() {
        val used = list("left:dpad_up", "left:dpad_down")
        val off = ControllerMapping(detected = used, enabled = false, overrides = mapOf("left:dpad_up" to "right:a"))
        assertEquals(list("left:y"), off.targetsFor(c("left:dpad_up")))
        val on = off.copy(enabled = true)
        assertEquals(list("right:a"), on.targetsFor(c("left:dpad_up")))
        assertEquals(list("left:x"), on.targetsFor(c("left:dpad_down")))
        assertTrue(on.targetsFor(c("right:menu")).isEmpty())
    }

    @Test
    fun `the page warns about a device button that takes several controls of the game`() {
        val mapping = ControllerMapping(detected = list("right:x", "right:y", "left:dpad_up"))
        assertEquals(setOf(c("left:y")), mapping.sharedTargets().keys)
    }

    @Test
    fun `a customised mapping keeps what the player chose and what it did for the rest, as before the rules changed`() {
        val mapping = ControllerMapping(
            detected = list("left:dpad_up", "left:dpad_down", "right:x", "right:menu", "right:a", "left:view"),
            enabled = true,
            overrides = mapOf("left:dpad_up" to "right:a"),
        )
        val frozen = mapping.frozenAsBefore()
        assertEquals(list("right:a"), frozen.targetsFor(c("left:dpad_up")))
        assertEquals(list("left:x"), frozen.targetsFor(c("left:dpad_down")))
        assertEquals(list("left:menu"), frozen.targetsFor(c("right:menu")))
        assertEquals(list("right:a"), frozen.targetsFor(c("right:a")))
        // Right X and the view button went nowhere before, and still do: the new defaults do not reach them.
        assertTrue(frozen.targetsFor(c("right:x")).isEmpty())
        assertTrue(frozen.targetsFor(c("left:view")).isEmpty())
        // The same mapping changes nothing once frozen.
        assertEquals(frozen, frozen.frozenAsBefore())
    }

    @Test
    fun `a mapping that is not customised follows the defaults and is left as it is`() {
        val mapping = ControllerMapping(detected = list("right:x", "left:dpad_up"), enabled = false)
        assertEquals(mapping, mapping.frozenAsBefore())
        assertEquals(list("left:x"), mapping.targetsFor(c("right:x")))
    }

    @Test
    fun `customising starts from what the game does now and keeps it when the defaults change`() {
        val mapping = ControllerMapping(detected = list("right:x", "right:y", "left:dpad_up"), overrides = mapOf("left:dpad_up" to "right:b"))
        val started = mapping.frozenAsNow().copy(enabled = true)
        assertEquals(list("left:x"), started.targetsFor(c("right:x")))
        assertEquals(list("left:y"), started.targetsFor(c("right:y")))
        // The control the player had chosen for stays theirs.
        assertEquals(list("right:b"), started.targetsFor(c("left:dpad_up")))
        assertEquals(started.overrides.keys, setOf("right:x", "right:y", "left:dpad_up"))
    }

    @Test
    fun `the Steam Frame's controls are off by default`() {
        val mapping = ControllerMapping(both = true)
        assertTrue(!mapping.useFrame)
    }
}
