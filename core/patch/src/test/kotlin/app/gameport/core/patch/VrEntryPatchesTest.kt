package app.gameport.core.patch

import app.gameport.core.model.vr.VrPlatforms
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.namedElement
import app.gameport.core.patch.patches.VrGameActivityPatch
import app.gameport.core.patch.patches.VrLauncherPatch
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VrEntryPatchesTest {
    private val launcher = "android.intent.category.LAUNCHER"
    private val vr = VrPlatforms.allImmersiveLauncherCategories

    private fun filter(vararg categories: String) =
        element("intent-filter", children = listOf(namedElement("action", "android.intent.action.MAIN")) + categories.map { namedElement("category", it) })

    private fun activity(name: String, vararg filters: JSONObject) =
        element("activity", ManifestAttr("name", 16842755, "STRING", name), children = filters.toList())

    private fun manifest(vararg activities: JSONObject): JSONObject =
        JSONObject().put("node_type", "element").put("name", "manifest").put(
            "nodes",
            JSONArray().put(element("application", children = activities.toList())),
        ).let { JSONObject().put("nodes", JSONArray().put(it)) }

    private fun filtersOf(root: JSONObject, activityName: String): List<List<String>> {
        val application = root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }.single()
            .elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") }.single()
        val activity = application.elem<JSONArray>("nodes").elemEach<JSONObject> { named("activity") }.single { it.nameAttribute() == activityName }
        return activity.elem<JSONArray>("nodes").elemEach<JSONObject> { named("intent-filter") }
            .map { f -> f.elem<JSONArray>("nodes").elemEach<JSONObject> { named("category") }.mapNotNull { it.nameAttribute() }.reversed() }.reversed()
    }

    private val game = "com.epicgames.unreal.GameActivity"

    private fun splashThenGame() = manifest(
        activity("com.epicgames.unreal.SplashActivity", filter(launcher)),
        activity(game),
    )

    @Test
    fun `an Unreal GameActivity behind a splash activity gets a VR entry that is not a launcher`() {
        val root = splashThenGame()
        VrLauncherPatch.patchManifest(root)
        VrGameActivityPatch.patchManifest(root)
        assertEquals(listOf(listOf(launcher) + vr), filtersOf(root, "com.epicgames.unreal.SplashActivity"))
        val added = filtersOf(root, game).single()
        assertFalse(launcher in added)
        assertTrue(added.containsAll(vr))
    }

    @Test
    fun `patching again changes nothing`() {
        val once = splashThenGame().also { VrLauncherPatch.patchManifest(it); VrGameActivityPatch.patchManifest(it) }
        val twice = splashThenGame().also { repeat(2) { _ -> VrLauncherPatch.patchManifest(it); VrGameActivityPatch.patchManifest(it) } }
        assertEquals(filtersOf(once, game), filtersOf(twice, game))
        assertEquals(filtersOf(once, "com.epicgames.unreal.SplashActivity"), filtersOf(twice, "com.epicgames.unreal.SplashActivity"))
    }

    @Test
    fun `a GameActivity that is the launcher entry is not given a second one`() {
        val root = manifest(activity(game, filter(launcher)))
        VrLauncherPatch.patchManifest(root)
        VrGameActivityPatch.patchManifest(root)
        assertEquals(listOf(listOf(launcher) + vr), filtersOf(root, game))
    }

    @Test
    fun `another activity is left alone`() {
        val root = manifest(activity("com.example.Main", filter(launcher)), activity("com.example.Other"))
        VrLauncherPatch.patchManifest(root)
        VrGameActivityPatch.patchManifest(root)
        assertEquals(emptyList<List<String>>(), filtersOf(root, "com.example.Other"))
    }

    @Test
    fun `the game is marked when it was given the entry, and only then`() {
        val given = splashThenGame().also { VrLauncherPatch.patchManifest(it); VrGameActivityPatch.patchManifest(it) }
        assertTrue(given.toString().contains("app.gameport.vr_game_activity"))
        val notGiven = manifest(activity(game, filter(launcher))).also { VrLauncherPatch.patchManifest(it); VrGameActivityPatch.patchManifest(it) }
        assertFalse(notGiven.toString().contains("app.gameport.vr_game_activity"))
    }
}
