package app.gameport.core.patch

import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.patches.MinSdkPatch
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinSdkPatchTest {
    private val attrMinSdk = 16843276

    private fun manifest(minSdk: Int?): JSONObject {
        val usesSdk = if (minSdk == null) element("uses-sdk") else element("uses-sdk", ManifestAttr("minSdkVersion", attrMinSdk, "DEC", minSdk))
        return JSONObject().put("node_type", "element").put("name", "manifest").put("nodes", JSONArray().put(usesSdk))
            .let { JSONObject().put("nodes", JSONArray().put(it)) }
    }

    private fun minSdkOf(root: JSONObject): Int? =
        root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject> { named("uses-sdk") }.single()
            .elem<JSONArray>("attributes").elemEach<JSONObject> { named("minSdkVersion") }.firstOrNull()
            ?.opt("data").let { (it as? Number)?.toInt() }

    @Test
    fun `a game that asks for a newer Android than the device gets the device's version`() {
        assertEquals(29, MinSdkPatch.loweredMinSdk(minSdk = 32, deviceSdk = 29))
        assertEquals(29, MinSdkPatch.loweredMinSdk(minSdk = 30, deviceSdk = 29))
    }

    @Test
    fun `a game that already fits, or whose versions are not known, is left alone`() {
        assertNull(MinSdkPatch.loweredMinSdk(minSdk = 29, deviceSdk = 29))
        assertNull(MinSdkPatch.loweredMinSdk(minSdk = 29, deviceSdk = 34))
        assertNull(MinSdkPatch.loweredMinSdk(minSdk = null, deviceSdk = 29))
        assertNull(MinSdkPatch.loweredMinSdk(minSdk = 32, deviceSdk = null))
        assertNull(MinSdkPatch.loweredMinSdk(minSdk = 32, deviceSdk = 0))
    }

    @Test
    fun `the manifest of a game for a newer Android is lowered on an older device`() {
        val root = manifest(minSdk = 32)
        MinSdkPatch.patchManifest(root, deviceSdk = 29)
        assertEquals(29, minSdkOf(root))
    }

    @Test
    fun `the manifest is not changed on a device that meets the minimum, or when the device is not known`() {
        val recent = manifest(minSdk = 29)
        MinSdkPatch.patchManifest(recent, deviceSdk = 32)
        assertEquals(29, minSdkOf(recent))

        val unknown = manifest(minSdk = 32)
        MinSdkPatch.patchManifest(unknown, deviceSdk = null)
        assertEquals(32, minSdkOf(unknown))
    }

    @Test
    fun `a manifest without a minimum is left as it is`() {
        val root = manifest(minSdk = null)
        MinSdkPatch.patchManifest(root, deviceSdk = 29)
        assertNull(minSdkOf(root))
    }
}
