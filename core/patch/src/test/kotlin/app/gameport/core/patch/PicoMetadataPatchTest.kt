package app.gameport.core.patch

import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.patches.PicoMetadataPatch
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PicoMetadataPatchTest {
    private fun metaData(name: String, value: String) =
        element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", name), ManifestAttr("value", ATTR_VALUE, "STRING", value))

    private fun manifest(vararg existing: JSONObject): JSONObject =
        JSONObject().put("node_type", "element").put("name", "manifest").put(
            "nodes",
            JSONArray().put(element("application", children = existing.toList())),
        ).let { JSONObject().put("nodes", JSONArray().put(it)) }

    /** name to value of every metadata the application holds. */
    private fun metaOf(root: JSONObject): List<Pair<String?, String?>> {
        val application = root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") }.single()
        return application.elem<JSONArray>("nodes").elemEach<JSONObject> { named("meta-data") }.map { node ->
            node.nameAttribute() to node.elem<JSONArray>("attributes").elemEach<JSONObject> { named("value") }.firstOrNull()?.opt("data")?.toString()
        }.reversed()
    }

    private fun permissionsOf(root: JSONObject): List<String?> =
        root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject> { named("uses-permission") }.map { it.nameAttribute() }

    private fun context(isVr: Boolean?, family: String?) = PatchContext(steamAppId = 1, steamId = 2, personaName = "test", isVr = isVr, deviceFamily = family)

    @Test
    fun `a VR game patched on a Pico gets the two markers`() {
        assertTrue(PicoMetadataPatch.wanted(context(isVr = true, family = "pico")))
        assertTrue(PicoMetadataPatch.wanted(context(isVr = null, family = "pico")))
        val root = manifest()
        PicoMetadataPatch.patchManifest(root)
        assertEquals(listOf<Pair<String?, String?>>("pvr.app.type" to "vr", "com.yvr.intent.category.VR" to "vr_only"), metaOf(root))
    }

    @Test
    fun `nothing is wanted on another headset, on a device that is not a headset, or for a flat game`() {
        assertFalse(PicoMetadataPatch.wanted(context(isVr = true, family = "meta")))
        assertFalse(PicoMetadataPatch.wanted(context(isVr = true, family = "openxr")))
        assertFalse(PicoMetadataPatch.wanted(context(isVr = true, family = null)))
        assertFalse(PicoMetadataPatch.wanted(context(isVr = false, family = "pico")))
    }

    @Test
    fun `a value the game came with is never replaced`() {
        val root = manifest(metaData("pvr.app.type", "something else"))
        PicoMetadataPatch.patchManifest(root)
        assertEquals(
            listOf<Pair<String?, String?>>("pvr.app.type" to "something else", "com.yvr.intent.category.VR" to "vr_only"),
            metaOf(root),
        )
    }

    @Test
    fun `patching again does not stack a second copy`() {
        val root = manifest()
        PicoMetadataPatch.patchManifest(root)
        PicoMetadataPatch.patchManifest(root)
        assertEquals(2, metaOf(root).size)
    }

    @Test
    fun `the OpenXR permissions are added once, and one the game declares is not doubled`() {
        val root = manifest()
        PicoMetadataPatch.patchManifest(root)
        PicoMetadataPatch.patchManifest(root)
        assertEquals(setOf("org.khronos.openxr.permission.OPENXR", "org.khronos.openxr.permission.OPENXR_SYSTEM"), permissionsOf(root).toSet())
        assertEquals(2, permissionsOf(root).size)

        val declared = manifest()
        declared.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single().elem<JSONArray>("nodes")!!
            .put(element("uses-permission", ManifestAttr("name", ATTR_NAME, "STRING", "org.khronos.openxr.permission.OPENXR_SYSTEM")))
        PicoMetadataPatch.patchManifest(declared)
        assertEquals(2, permissionsOf(declared).size)
    }
}
