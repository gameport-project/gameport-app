package app.gameport.core.sync

import app.gameport.core.model.ControllerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerProfileExtrasTest {
    @Test
    fun `a call without the extras gives nothing and erases nothing`() {
        val e = ControllerProfileExtras.of(null, null, null, null)
        assertNull(e.profiles)
        assertNull(e.presented)
        assertNull(e.used)
    }

    @Test
    fun `the profiles are read, the malformed ones dropped and the list capped`() {
        val entries = arrayOf("/interaction_profiles/oculus/touch_controller:30", "", "/bad|one:3") + (1..30).map { "/p/$it:$it" }
        val e = ControllerProfileExtras.of(entries, "touch", "applied", "touch")
        assertEquals(ControllerProfile("/interaction_profiles/oculus/touch_controller", 30), e.profiles?.first())
        assertEquals(ControllerProfile.MAX_PROFILES, e.profiles?.size)
        assertEquals("touch", e.present)
        assertEquals("applied", e.presented)
        assertEquals("touch", e.used)
    }

    @Test
    fun `an unknown word is ignored`() {
        val e = ControllerProfileExtras.of(arrayOf(), "banana", "maybe", "everything")
        assertNull(e.profiles)
        assertNull(e.present)
        assertNull(e.presented)
        assertNull(e.used)
    }

    @Test
    fun `the config follows the choice and no game is automatic`() {
        assertEquals(ControllerPresentation("auto", false), ControllerPresentation.of(null))
        assertEquals(ControllerPresentation("auto", false), ControllerPresentation.of(app.gameport.core.model.PresentedControllers.AUTO))
        assertEquals(ControllerPresentation("frame", true), ControllerPresentation.of(app.gameport.core.model.PresentedControllers.FRAME))
        assertEquals(ControllerPresentation("touch", false), ControllerPresentation.of(app.gameport.core.model.PresentedControllers.TOUCH))
    }
}
