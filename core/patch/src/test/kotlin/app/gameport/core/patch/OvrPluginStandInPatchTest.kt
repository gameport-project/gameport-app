package app.gameport.core.patch

import app.gameport.core.patch.json.element
import app.gameport.core.patch.patches.OvrPluginStandInPatch
import app.gameport.core.patch.patches.OvrPluginStandInPatch.Action
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OvrPluginStandInPatchTest {
    private fun decide(hasLibrary: Boolean, holdsOurs: Boolean, hasKit: Boolean, enabled: Boolean) =
        OvrPluginStandInPatch.decide(hasLibrary, holdsOurs, hasKit, enabled)

    @Test
    fun `a game with the kit and no library is given the stand-in`() {
        assertEquals(Action.GIVE, decide(hasLibrary = false, holdsOurs = false, hasKit = true, enabled = true))
    }

    @Test
    fun `a game without the kit is left alone`() {
        assertEquals(Action.NOTHING, decide(hasLibrary = false, holdsOurs = false, hasKit = false, enabled = true))
    }

    @Test
    fun `a game that has the real library is never touched, whatever it holds besides`() {
        assertEquals(Action.NOTHING, decide(hasLibrary = true, holdsOurs = false, hasKit = true, enabled = true))
        assertEquals(Action.NOTHING, decide(hasLibrary = true, holdsOurs = false, hasKit = true, enabled = false))
    }

    @Test
    fun `turned off, nothing is given`() {
        assertEquals(Action.NOTHING, decide(hasLibrary = false, holdsOurs = false, hasKit = true, enabled = false))
    }

    @Test
    fun `a game patched again keeps ours when on and loses it when off`() {
        assertEquals(Action.KEEP, decide(hasLibrary = true, holdsOurs = true, hasKit = true, enabled = true))
        assertEquals(Action.TAKE_AWAY, decide(hasLibrary = true, holdsOurs = true, hasKit = true, enabled = false))
    }

    private fun application(vararg children: JSONObject) = JSONObject().put("nodes", JSONArray().put(
        JSONObject().put("node_type", "element").put("name", "manifest").put("nodes", JSONArray().put(element("application", children = children.toList()))),
    ))

    private fun marks(root: JSONObject) = Regex("app.gameport.ovrplugin_stand_in").findAll(root.toString()).count()

    @Test
    fun `the mark is written once, whatever the number of passes, and taken away`() {
        val root = application()
        repeat(3) { OvrPluginStandInPatch.mark(root, present = true) }
        assertEquals(1, marks(root))
        OvrPluginStandInPatch.mark(root, present = false)
        assertEquals(0, marks(root))
    }
}
