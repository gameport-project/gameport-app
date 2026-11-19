package app.gameport.core.patch

import app.gameport.core.patch.json.ATTR_AUTHORITIES
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.element
import app.gameport.core.patch.patches.PackageRenamePatch
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageRenamePatchTest {
    private val original = "com.cmgames.unloop"
    private val renamed = "com.gameport.g2672470"

    /** An attribute as the manifest JSON holds one: the package name is a plain one, the framework's carry their id and namespace. */
    private fun plain(name: String, value: String) = JSONObject().put("name", name).put("value_type", "STRING").put("data", value)

    private fun manifest(packageName: String, vararg children: JSONObject) = JSONObject().put(
        "nodes",
        JSONArray().put(
            JSONObject().put("node_type", "element").put("name", "manifest")
                .put("attributes", JSONArray().put(plain("package", packageName)).put(JSONObject().put("name", "versionCode").put("value_type", "DEC").put("data", 7)))
                .put("nodes", JSONArray().also { nodes -> children.forEach { nodes.put(it) } }),
        ),
    )

    /** A manifest shaped like the ones GamePort patches: a permission the package names, providers, and names written both ways. */
    private fun gameManifest() = manifest(
        original,
        element("permission", ManifestAttr("name", ATTR_NAME, "STRING", "$original.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")),
        element("uses-permission", ManifestAttr("name", ATTR_NAME, "STRING", "$original.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")),
        element("uses-permission", ManifestAttr("name", ATTR_NAME, "STRING", "android.permission.INTERNET")),
        element(
            "application",
            ManifestAttr("name", ATTR_NAME, "STRING", ".UnloopApp"),
            children = listOf(
                element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", "$original.someKey"), ManifestAttr("value", 16842788, "STRING", "x")),
                element("provider", ManifestAttr("name", ATTR_NAME, "STRING", "com.google.android.gms.games.provider.PlayGamesInitProvider"), ManifestAttr("authorities", ATTR_AUTHORITIES, "STRING", "$original.playgamesinitprovider")),
                element("activity", ManifestAttr("name", ATTR_NAME, "STRING", "$original.DownloaderActivity")),
                element("activity", ManifestAttr("name", ATTR_NAME, "STRING", "com.epicgames.unreal.GameActivity")),
                element("activity-alias", ManifestAttr("name", ATTR_NAME, "STRING", ".Alias"), ManifestAttr("targetActivity", 16842770, "STRING", ".GameActivity")),
            ),
        ),
    )

    private fun JSONObject.nodes() = elem<JSONArray>("nodes").elemEach<JSONObject>()
    private fun JSONObject.tag() = elem<String>("name")
    private fun JSONObject.attrs() = elem<JSONArray>("attributes").elemEach<JSONObject>()
    private fun JSONObject.attr(name: String) = attrs().firstOrNull { it.elem<String>("name") == name }?.elem<String>("data")
    private fun JSONObject.all(tag: String): List<JSONObject> = (if (tag() == tag) listOf(this) else emptyList()) + nodes().flatMap { it.all(tag) }
    private fun JSONObject.root() = all("manifest").first()
    private fun JSONObject.values(name: String) = all("meta-data").mapNotNull { it.attr(name) } + all("provider").mapNotNull { it.attr(name) } + all("activity").mapNotNull { it.attr(name) } + all("activity-alias").mapNotNull { it.attr(name) } + all("application").mapNotNull { it.attr(name) } + all("permission").mapNotNull { it.attr(name) } + all("uses-permission").mapNotNull { it.attr(name) }

    @Test
    fun `the package attribute takes the new name and the old one is written down`() {
        val json = gameManifest()
        PackageRenamePatch.patchManifest(json, original, renamed)
        assertEquals(renamed, json.root().attr("package"))
        assertEquals(listOf(original), json.all("meta-data").mapNotNull { it.attr("value") }.filter { it == original })
    }

    @Test
    fun `the names the package carries follow it`() {
        val json = gameManifest()
        PackageRenamePatch.patchManifest(json, original, renamed)
        assertEquals(listOf("$renamed.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", "$renamed.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"), json.values("name").filter { it.contains("DYNAMIC_RECEIVER") })
        assertEquals(listOf("$renamed.playgamesinitprovider"), json.all("provider").mapNotNull { it.attr("authorities") })
    }

    @Test
    fun `a class name is never renamed, and a relative one is written in full`() {
        val json = gameManifest()
        PackageRenamePatch.patchManifest(json, original, renamed)
        val names = json.values("name")
        // A class of the game's dex keeps its own name: the components that use it would not find it otherwise.
        assertTrue(names.contains("$original.DownloaderActivity"))
        assertTrue(names.contains("com.google.android.gms.games.provider.PlayGamesInitProvider"))
        assertTrue(names.contains("com.epicgames.unreal.GameActivity"))
        // A relative name belongs to the package: it must keep pointing at the same class.
        assertTrue(names.contains("$original.UnloopApp"))
        assertTrue(names.contains("$original.Alias"))
        assertEquals(listOf("$original.GameActivity"), json.all("activity-alias").mapNotNull { it.attr("targetActivity") })
        // A metadata key is a name the game looks up itself, never one the package carries.
        assertTrue(names.contains("$original.someKey"))
        assertFalse(names.contains("$renamed.someKey"))
    }

    @Test
    fun `patching again does not stack the mark nor move the names twice`() {
        val json = gameManifest()
        repeat(3) { PackageRenamePatch.patchManifest(json, original, renamed) }
        assertEquals(1, json.all("meta-data").count { it.attr("name") == PackageRenamePatch.META_ORIGINAL })
        assertEquals(renamed, json.root().attr("package"))
        assertEquals(listOf("$renamed.playgamesinitprovider"), json.all("provider").mapNotNull { it.attr("authorities") })
    }

    @Test
    fun `nothing is done when the name the game already carries is the one it must have`() {
        assertFalse(PackageRenamePatch.wanted(context(renamed = null), original))
        assertFalse(PackageRenamePatch.wanted(context(renamed = original), original))
        assertTrue(PackageRenamePatch.wanted(context(renamed = renamed), original))
    }

    @Test
    fun `a game the player wants back under its own name is put back where its manifest says it came from`() {
        val json = gameManifest()
        PackageRenamePatch.patchManifest(json, original, renamed)
        val recorded = PackageRenamePatch.recordedOriginal(json)
        assertEquals(original, recorded)
        // Patching again with the choice turned off puts it back; the pass that only finds it already renamed leaves it alone.
        assertEquals(original, PackageRenamePatch.targetName(context(renamed = null, restore = true), renamed, recorded))
        assertEquals(renamed, PackageRenamePatch.targetName(context(renamed = null), renamed, recorded))
        assertEquals(renamed, PackageRenamePatch.targetName(context(renamed = renamed), renamed, recorded))
        // Nothing recorded (a game GamePort never renamed): there is no name to go back to.
        assertEquals(renamed, PackageRenamePatch.targetName(context(renamed = null, restore = true), renamed, null))
    }

    @Test
    fun `putting a game back under its own name keeps the name it came with, and moves every name back with it`() {
        val json = gameManifest()
        PackageRenamePatch.patchManifest(json, original, renamed)
        // Back under its own name: the package and the names it carries follow, and the name it came with is still the one first written down.
        PackageRenamePatch.patchManifest(json, renamed, original, PackageRenamePatch.recordedOriginal(json) ?: original)
        assertEquals(original, json.root().attr("package"))
        assertEquals(original, PackageRenamePatch.recordedOriginal(json))
        assertEquals(listOf("$original.playgamesinitprovider"), json.all("provider").mapNotNull { it.attr("authorities") })
        assertEquals(listOf("$original.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", "$original.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"), json.values("name").filter { it.contains("DYNAMIC_RECEIVER") })
    }

    private fun context(renamed: String?, restore: Boolean = false) =
        PatchContext(steamAppId = 1, steamId = 0, personaName = "player", renamedPackage = renamed, restorePackageName = restore)
}
