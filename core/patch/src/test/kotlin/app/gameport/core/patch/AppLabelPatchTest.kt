package app.gameport.core.patch

import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.patches.AppLabelPatch
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLabelPatchTest {
    private val attrLabel = 16842753

    private fun label(value: String) = ManifestAttr("label", attrLabel, "STRING", value)

    private fun manifest(applicationLabel: String?, vararg nodes: JSONObject): JSONObject {
        val attributes = listOfNotNull(applicationLabel?.let(::label)).toTypedArray()
        return JSONObject().put("node_type", "element").put("name", "manifest").put(
            "nodes",
            JSONArray().put(element("application", *attributes, children = nodes.toList())),
        ).let { JSONObject().put("nodes", JSONArray().put(it)) }
    }

    private fun application(root: JSONObject): JSONObject =
        root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") }.single()

    private fun labelOf(node: JSONObject): String? =
        node.elem<JSONArray>("attributes").elemEach<JSONObject> { named("label") }.firstOrNull()?.opt("data")?.toString()

    private fun children(root: JSONObject, name: String): List<JSONObject> =
        application(root).elem<JSONArray>("nodes").elemEach<JSONObject> { named(name) }.reversed()

    @Test
    fun `the game gets its Steam name on the application`() {
        val root = manifest("Ancient_Dungeon")
        AppLabelPatch.patchManifest(root, "Ancient Dungeon VR")
        assertEquals("Ancient Dungeon VR", labelOf(application(root)))
    }

    @Test
    fun `an activity or an alias with a label of its own takes the application's`() {
        val root = manifest(
            "Technical",
            element("activity", ManifestAttr("name", ATTR_NAME, "STRING", "com.example.Main"), label("Technical Activity")),
            element("activity-alias", ManifestAttr("name", ATTR_NAME, "STRING", "com.example.Alias"), label("Technical Alias")),
        )
        AppLabelPatch.patchManifest(root, "Steam Name")
        assertNull(labelOf(children(root, "activity").single()))
        assertNull(labelOf(children(root, "activity-alias").single()))
        assertEquals("Steam Name", labelOf(application(root)))
    }

    @Test
    fun `what else an activity says is kept`() {
        val root = manifest(null, element("activity", ManifestAttr("name", ATTR_NAME, "STRING", "com.example.Main"), label("Technical")))
        AppLabelPatch.patchManifest(root, "Steam Name")
        val activity = children(root, "activity").single()
        assertEquals("com.example.Main", activity.elem<JSONArray>("attributes").elemEach<JSONObject> { named("name") }.single().opt("data"))
    }

    @Test
    fun `patching again leaves one label`() {
        val root = manifest("Technical", element("activity", label("Technical")))
        AppLabelPatch.patchManifest(root, "Steam Name")
        AppLabelPatch.patchManifest(root, "Steam Name")
        assertEquals(1, application(root).elem<JSONArray>("attributes").elemEach<JSONObject> { named("label") }.size)
    }
}
