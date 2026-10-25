package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_REQUIRED
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeEach
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * A game can have a setting for the real world behind it (the passthrough) that the headset does not offer it: the headset only lets a game use the camera
 * view, and the blend mode that shows it, when the manifest declares that it does (`com.oculus.feature.PASSTHROUGH`), which a build made for another headset
 * does not (seen in Cubism: "ALPHA_BLEND is unavailable since the app doesn't declare the feature").
 *
 * Declaring it only lets the game ask. It is the player's choice for one game (off by default), and a game patched again without it has the declaration taken
 * away when it is the one GamePort gave; one the game came with is never touched.
 */
object PassthroughFeaturePatch : ApkPatch {
    override val id = "passthrough_feature"
    override val recommended = true
    override val locked = false

    const val FEATURE = "com.oculus.feature.PASSTHROUGH"

    /** Application metadata the hook reads: present when this patch declared the feature. */
    const val META_KEY = "app.gameport.passthrough_feature"

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (context.isVr == false) return
        patchManifest(session.manifest, context.allowPassthrough)
    }

    internal enum class Action { DECLARE, KEEP, TAKE_AWAY, NOTHING }

    /** What to do with the manifest: declare the feature, keep our earlier declaration, take it away, or leave it. A declaration the game came with is never ours. */
    internal fun decide(declared: Boolean, ours: Boolean, enabled: Boolean): Action = when {
        ours -> if (enabled) Action.KEEP else Action.TAKE_AWAY
        declared -> Action.NOTHING
        enabled -> Action.DECLARE
        else -> Action.NOTHING
    }

    internal fun patchManifest(manifest: JSONObject, enabled: Boolean) {
        manifest.takeNodesEach({ named("manifest") }) {
            val features = elem<JSONArray>("nodes").elemEach<JSONObject> { named("uses-feature") }
            val declared = features.any { it.nameAttribute() == FEATURE }
            val ours = declared && hasMark(this)
            val action = decide(declared, ours, enabled)
            takeNodes {
                val nodes = this ?: JSONArray()
                if (action == Action.TAKE_AWAY) nodes.takeEach<JSONObject>({ named("uses-feature") && nameAttribute() == FEATURE }) { null }
                if (action == Action.DECLARE) nodes.put(element("uses-feature", ManifestAttr("name", ATTR_NAME, "STRING", FEATURE), ManifestAttr("required", ATTR_REQUIRED, "BOOLEAN", false)))
                nodes
            }
            takeNodesEach({ named("application") }) {
                takeNodes {
                    val nodes = this ?: JSONArray()
                    nodes.takeEach<JSONObject>({ named("meta-data") && nameAttribute() == META_KEY }) { null }
                    if (action == Action.DECLARE || action == Action.KEEP) nodes.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", META_KEY), ManifestAttr("value", ATTR_VALUE, "STRING", "true")))
                    nodes
                }
            }
        }
    }

    private fun hasMark(manifest: JSONObject?): Boolean =
        manifest.elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") }.any { application ->
            application.elem<JSONArray>("nodes").elemEach<JSONObject> { named("meta-data") }.any { it.nameAttribute() == META_KEY }
        }
}
