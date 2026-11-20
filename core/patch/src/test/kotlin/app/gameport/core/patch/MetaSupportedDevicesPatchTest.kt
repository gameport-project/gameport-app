package app.gameport.core.patch

import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.patches.MetaSupportedDevicesPatch
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaSupportedDevicesPatchTest {
    private fun metaData(name: String, value: String) =
        element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", name), ManifestAttr("value", ATTR_VALUE, "STRING", value))

    private fun manifest(vararg existing: JSONObject): JSONObject =
        JSONObject().put("node_type", "element").put("name", "manifest").put(
            "nodes",
            JSONArray().put(element("application", children = existing.toList())),
        ).let { JSONObject().put("nodes", JSONArray().put(it)) }

    private fun metaOf(root: JSONObject): List<Pair<String?, String?>> {
        val application = root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") }.single()
        return application.elem<JSONArray>("nodes").elemEach<JSONObject> { named("meta-data") }.map { node ->
            node.nameAttribute() to node.elem<JSONArray>("attributes").elemEach<JSONObject> { named("value") }.firstOrNull()?.opt("data")?.toString()
        }.reversed()
    }

    private fun context(isVr: Boolean?, family: String?, chosen: Boolean = true) = PatchContext(steamAppId = 1, steamId = 2, personaName = "test", isVr = isVr, deviceFamily = family, metaHighRefresh = chosen)

    @Test
    fun `a VR game patched on a Quest is told it is made for the current Quests`() {
        assertTrue(MetaSupportedDevicesPatch.wanted(context(isVr = true, family = "meta")))
        val root = manifest()
        MetaSupportedDevicesPatch.patchManifest(root)
        assertEquals(
            listOf<Pair<String?, String?>>("com.oculus.supportedDevices" to "quest2|questpro|quest3|quest3s", "app.gameport.supported_devices" to "1"),
            metaOf(root),
        )
    }

    @Test
    fun `a list the game came with is never replaced`() {
        val root = manifest(metaData("com.oculus.supportedDevices", "quest2"))
        MetaSupportedDevicesPatch.patchManifest(root)
        assertEquals(listOf<Pair<String?, String?>>("com.oculus.supportedDevices" to "quest2"), metaOf(root))
    }

    @Test
    fun `a game is left as it is when the player chose no frame rate`() {
        assertFalse(MetaSupportedDevicesPatch.wanted(context(isVr = true, family = "meta", chosen = false)))
    }

    @Test
    fun `nothing is wanted on another headset, off a headset, or for a flat game`() {
        assertFalse(MetaSupportedDevicesPatch.wanted(context(isVr = true, family = "pico")))
        assertFalse(MetaSupportedDevicesPatch.wanted(context(isVr = true, family = "openxr")))
        assertFalse(MetaSupportedDevicesPatch.wanted(context(isVr = true, family = null)))
        assertFalse(MetaSupportedDevicesPatch.wanted(context(isVr = false, family = "meta")))
    }

    @Test
    fun `turning the choice off takes away the declaration GamePort added, and only that one`() {
        val ours = manifest(metaData("com.oculus.supportedDevices", "quest2|questpro|quest3|quest3s"), metaData("app.gameport.supported_devices", "1"), metaData("other", "x"))
        MetaSupportedDevicesPatch.removeOwn(ours)
        assertEquals(listOf<Pair<String?, String?>>("other" to "x"), metaOf(ours))
        // The list the game came with has no mark: it stays.
        val theirs = manifest(metaData("com.oculus.supportedDevices", "quest2"))
        MetaSupportedDevicesPatch.removeOwn(theirs)
        assertEquals(listOf<Pair<String?, String?>>("com.oculus.supportedDevices" to "quest2"), metaOf(theirs))
    }
}
