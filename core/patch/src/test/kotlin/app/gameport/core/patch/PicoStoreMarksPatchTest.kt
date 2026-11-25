package app.gameport.core.patch

import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.patches.PicoStoreMarksPatch
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PicoStoreMarksPatchTest {
    private fun permission(name: String) = element("uses-permission", ManifestAttr("name", ATTR_NAME, "STRING", name))

    private fun metaData(name: String, value: String) =
        element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", name), ManifestAttr("value", ATTR_VALUE, "STRING", value))

    private fun manifest(permissions: List<JSONObject>, metas: List<JSONObject>): JSONObject =
        JSONObject().put(
            "nodes",
            JSONArray().put(
                JSONObject().put("node_type", "element").put("name", "manifest").put(
                    "nodes",
                    JSONArray().also { nodes ->
                        permissions.forEach { nodes.put(it) }
                        nodes.put(element("application", children = metas))
                    },
                ),
            ),
        )

    private fun root(manifest: JSONObject) = manifest.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single()

    private fun permissionsOf(manifest: JSONObject): List<String?> =
        root(manifest).elem<JSONArray>("nodes").elemEach<JSONObject> { named("uses-permission") }.map { it.nameAttribute() }

    private fun metaOf(manifest: JSONObject): List<String?> =
        root(manifest).elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject> { named("meta-data") }.map { it.nameAttribute() }

    private fun context(appId: Int, isVr: Boolean?, family: String?) = PatchContext(steamAppId = appId, steamId = 2, personaName = "test", isVr = isVr, deviceFamily = family)

    @Test
    fun `the marks of the Pico store go, whatever the last word of the permission and whatever else the namespace holds`() {
        val file = manifest(
            permissions = listOf(
                permission("android.permission.INTERNET"),
                permission("com.pvr.tobactivate.permission.AUTH_CHECK"),
                permission("com.pvr.tobactivate.permission.OTHER_CHECK"),
            ),
            metas = listOf(metaData("pvr.app.id", "x"), metaData("pvr.app.type", "vr")),
        )
        PicoStoreMarksPatch.patchManifest(file)
        assertEquals(listOf<String?>("android.permission.INTERNET"), permissionsOf(file))
        assertEquals(listOf<String?>("pvr.app.type"), metaOf(file))
    }

    @Test
    fun `a permission of another maker that ends the same way is kept, and so is a metadata whose name only looks alike`() {
        val file = manifest(
            permissions = listOf(permission("com.other.permission.AUTH_CHECK"), permission("com.pvr.tobactivatex.permission.AUTH_CHECK")),
            metas = listOf(metaData("pvr.app.id.extra", "x"), metaData("other.pvr.app.id", "x")),
        )
        PicoStoreMarksPatch.patchManifest(file)
        // The order a manifest is read back in is the library's business, only what is kept matters here.
        assertEquals(setOf<String?>("com.other.permission.AUTH_CHECK", "com.pvr.tobactivatex.permission.AUTH_CHECK"), permissionsOf(file).toSet())
        assertEquals(setOf<String?>("pvr.app.id.extra", "other.pvr.app.id"), metaOf(file).toSet())
    }

    @Test
    fun `a manifest without the marks is left as it is, and running it twice changes nothing more`() {
        val file = manifest(listOf(permission("android.permission.INTERNET")), listOf(metaData("pvr.app.type", "vr")))
        PicoStoreMarksPatch.patchManifest(file)
        PicoStoreMarksPatch.patchManifest(file)
        assertEquals(listOf<String?>("android.permission.INTERNET"), permissionsOf(file))
        assertEquals(listOf<String?>("pvr.app.type"), metaOf(file))
    }

    @Test
    fun `it is wanted for a listed game on a Pico only, VR or unknown`() {
        val listed = PicoStoreMarksPatch.APP_IDS.first()
        assertTrue(PicoStoreMarksPatch.wanted(context(listed, isVr = true, family = "pico")))
        assertTrue(PicoStoreMarksPatch.wanted(context(listed, isVr = null, family = "pico")))
        assertFalse(PicoStoreMarksPatch.wanted(context(listed, isVr = false, family = "pico")))
        assertFalse(PicoStoreMarksPatch.wanted(context(listed, isVr = true, family = "meta")))
        assertFalse(PicoStoreMarksPatch.wanted(context(listed, isVr = true, family = null)))
        assertFalse(PicoStoreMarksPatch.wanted(context(1, isVr = true, family = "pico")))
    }
}
