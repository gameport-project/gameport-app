package app.gameport.core.patch

import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.patches.PassthroughFeaturePatch
import app.gameport.core.patch.patches.PassthroughFeaturePatch.Action
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PassthroughFeaturePatchTest {
    private val feature = "com.oculus.feature.PASSTHROUGH"

    private fun root(vararg children: JSONObject) = JSONObject().put("nodes", JSONArray().put(
        JSONObject().put("node_type", "element").put("name", "manifest").put("nodes", JSONArray().also { nodes -> children.forEach { nodes.put(it) } }),
    ))

    private fun declared(root: JSONObject) = Regex("\"data\":\"" + Regex.escape(feature) + "\"").findAll(root.toString()).count()
    private fun marks(root: JSONObject) = Regex("app.gameport.passthrough_feature").findAll(root.toString()).count()
    private fun usesFeature(name: String) = element("uses-feature", ManifestAttr("name", ATTR_NAME, "STRING", name))
    private fun application() = element("application", children = listOf(element("activity")))

    @Test
    fun `the decision follows who declared the feature and whether it is turned on`() {
        assertEquals(Action.DECLARE, PassthroughFeaturePatch.decide(declared = false, ours = false, enabled = true))
        assertEquals(Action.NOTHING, PassthroughFeaturePatch.decide(declared = false, ours = false, enabled = false))
        assertEquals(Action.NOTHING, PassthroughFeaturePatch.decide(declared = true, ours = false, enabled = true))
        assertEquals(Action.NOTHING, PassthroughFeaturePatch.decide(declared = true, ours = false, enabled = false))
        assertEquals(Action.KEEP, PassthroughFeaturePatch.decide(declared = true, ours = true, enabled = true))
        assertEquals(Action.TAKE_AWAY, PassthroughFeaturePatch.decide(declared = true, ours = true, enabled = false))
    }

    @Test
    fun `turned on, the feature is declared once and marked, whatever the number of passes`() {
        val manifest = root(usesFeature("android.hardware.vulkan.version"), application())
        repeat(3) { PassthroughFeaturePatch.patchManifest(manifest, enabled = true) }
        assertEquals(1, declared(manifest))
        assertEquals(1, marks(manifest))
    }

    @Test
    fun `turned off afterwards, what GamePort gave is taken away with its mark`() {
        val manifest = root(application())
        PassthroughFeaturePatch.patchManifest(manifest, enabled = true)
        PassthroughFeaturePatch.patchManifest(manifest, enabled = false)
        assertEquals(0, declared(manifest))
        assertEquals(0, marks(manifest))
    }

    @Test
    fun `a declaration the game came with is never touched nor marked`() {
        val manifest = root(usesFeature(feature), application())
        PassthroughFeaturePatch.patchManifest(manifest, enabled = true)
        PassthroughFeaturePatch.patchManifest(manifest, enabled = false)
        assertEquals(1, declared(manifest))
        assertEquals(0, marks(manifest))
    }

    @Test
    fun `turned off from the start, nothing is written`() {
        val manifest = root(application())
        PassthroughFeaturePatch.patchManifest(manifest, enabled = false)
        assertEquals(0, declared(manifest))
        assertEquals(0, marks(manifest))
    }
}
