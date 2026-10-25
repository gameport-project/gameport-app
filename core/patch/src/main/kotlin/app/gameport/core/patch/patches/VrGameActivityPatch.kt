package app.gameport.core.patch.patches

import app.gameport.core.model.vr.VrPlatforms
import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.namedElement
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * An Unreal game can start from a splash activity that hands over to `GameActivity`, the one that runs the game. The system reads the manifest
 * of each activity, so the second one opens in a flat window beside the first, whatever the intent that starts it carries. This gives it the
 * immersive categories too, without the launcher one so it stays out of the app list.
 *
 * Only an Unreal `GameActivity` that has no launcher or VR entry of its own is touched, and only for a VR game. It is a patch of its own so a
 * game that does not get along with it can be patched without it.
 */
object VrGameActivityPatch : ApkPatch {
    override val id = "vr_game_activity"
    override val recommended = true
    override val locked = false

    const val UNREAL_GAME_ACTIVITY = "com.epicgames.unreal.GameActivity"

    /** Application metadata the hook reads: present when this patch gave the game's activity its VR entry. */
    const val META_KEY = "app.gameport.vr_game_activity"

    private val ENTRY_CATEGORIES = VrPlatforms.allImmersiveLauncherCategories.toSet() + "android.intent.category.LAUNCHER"

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (context.isVr == false) return
        patchManifest(session.manifest)
    }

    internal fun patchManifest(manifest: JSONObject) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                var added = false
                takeNodesEach({ named("activity") }) {
                    val hasEntry = elem<JSONArray>("nodes").elemEach<JSONObject> { named("intent-filter") }.any { filter ->
                        filter.elem<JSONArray>("nodes").elemEach<JSONObject> { named("category") }.any { it.nameAttribute() in ENTRY_CATEGORIES }
                    }
                    if (nameAttribute() != UNREAL_GAME_ACTIVITY || hasEntry) this else {
                        added = true
                        takeNodes { (this ?: JSONArray()).apply { put(immersiveEntry()) } }
                    }
                }
                // A mark the hook writes into the game's log, so a problem report tells whether the game was given this entry.
                if (added) takeNodes {
                    this?.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", META_KEY), ManifestAttr("value", ATTR_VALUE, "STRING", "true")))
                } else this
            }
        }
    }

    /** An intent filter that only marks an activity as a VR one: the main action and the immersive categories, no launcher. */
    private fun immersiveEntry(): JSONObject = element(
        "intent-filter",
        children = listOf(namedElement("action", "android.intent.action.MAIN")) + VrPlatforms.allImmersiveLauncherCategories.map { namedElement("category", it) },
    )
}
