package app.gameport.core.patch

import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_REQUIRED
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.patches.UsesLibraryPatch
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsesLibraryPatchTest {
    private fun declaration(element: String, name: String, required: Boolean?): JSONObject =
        if (required == null) element(element, ManifestAttr("name", ATTR_NAME, "STRING", name))
        else element(element, ManifestAttr("name", ATTR_NAME, "STRING", name), ManifestAttr("required", ATTR_REQUIRED, "BOOLEAN", required))

    private fun manifest(vararg declarations: JSONObject): JSONObject {
        val application = JSONObject().put("node_type", "element").put("name", "application").put("nodes", JSONArray().also { array -> declarations.forEach { array.put(it) } })
        return JSONObject().put("nodes", JSONArray().put(JSONObject().put("node_type", "element").put("name", "manifest").put("nodes", JSONArray().put(application))))
    }

    private fun declarationsOf(root: JSONObject): List<JSONObject> =
        root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject>().reversed()

    private fun attributesOf(node: JSONObject): List<JSONObject> = node.elem<JSONArray>("attributes").elemEach<JSONObject>().reversed()

    /** The value of `required` of each declaration, by the name of its library (null when the attribute is not there). */
    private fun requiredOf(root: JSONObject): Map<String, Any?> = declarationsOf(root).associate { node ->
        val attributes = attributesOf(node)
        attributes.single { it.named("name") }.opt("data").toString() to attributes.firstOrNull { it.named("required") }?.opt("data")
    }

    @Test
    fun `a library the game requires becomes optional, so a device without it installs the game`() {
        val root = manifest(declaration("uses-native-library", "libossdk.oculus.so", true), declaration("uses-library", "org.apache.http.legacy", true))
        UsesLibraryPatch.patchManifest(root)
        assertEquals(mapOf<String, Any?>("libossdk.oculus.so" to false, "org.apache.http.legacy" to false), requiredOf(root))
    }

    @Test
    fun `a declaration without the attribute gets it after the name, and one already optional stays so`() {
        val root = manifest(declaration("uses-native-library", "libossdk.oculus.so", null), declaration("uses-native-library", "libother.so", false))
        UsesLibraryPatch.patchManifest(root)
        assertEquals(mapOf<String, Any?>("libossdk.oculus.so" to false, "libother.so" to false), requiredOf(root))
        // The attributes of an Android manifest are in the order of their resource ids: the name first, the new attribute after it.
        val ours = declarationsOf(root).single { attributesOf(it).any { a -> a.opt("data") == "libossdk.oculus.so" } }
        assertEquals(listOf("name", "required"), attributesOf(ours).map { it.getString("name") })
        assertEquals(2, attributesOf(ours).size)
    }

    @Test
    fun `only a device that is not a Meta one is concerned`() {
        assertFalse(UsesLibraryPatch.wanted(PatchContext(1, 1L, "p", deviceFamily = "meta")))
        assertTrue(UsesLibraryPatch.wanted(PatchContext(1, 1L, "p", deviceFamily = "pico")))
        assertTrue(UsesLibraryPatch.wanted(PatchContext(1, 1L, "p", deviceFamily = "openxr")))
        assertTrue(UsesLibraryPatch.wanted(PatchContext(1, 1L, "p", deviceFamily = null)))
    }

    @Test
    fun `a manifest without any library declaration is left as it is`() {
        val root = manifest(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", "x")))
        UsesLibraryPatch.patchManifest(root)
        assertEquals(mapOf<String, Any?>("x" to null), requiredOf(root))
    }
}
