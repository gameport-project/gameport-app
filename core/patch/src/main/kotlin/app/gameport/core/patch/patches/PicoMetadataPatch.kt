package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Tells a Pico that the game is a VR application, the way the games made for it do, in the game's manifest: the application metadata `pvr.app.type` and
 * `com.yvr.intent.category.VR`. A build made for another headset does not carry them.
 *
 * Only on a Pico, only for a VR game, and only what the game does not already say: a value the game came with is never replaced. Nothing else of the game changes.
 */
object PicoMetadataPatch : ApkPatch {
    override val id = "pico_metadata"
    override val recommended = true
    override val locked = false

    /** The headset family this patch is for (`pico`, see [app.gameport.core.model.vr.PicoPlatform]). */
    const val FAMILY = "pico"

    /** The metadata a Pico reads, and the value it expects. */
    val ENTRIES: List<Pair<String, String>> = listOf("pvr.app.type" to "vr", "com.yvr.intent.category.VR" to "vr_only")

    internal fun wanted(context: PatchContext): Boolean = context.isVr != false && context.deviceFamily == FAMILY

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (!wanted(context)) return
        patchManifest(session.manifest)
    }

    internal fun patchManifest(manifest: JSONObject) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodes {
                    val nodes = this ?: JSONArray()
                    val present = nodes.elemEach<JSONObject> { named("meta-data") }.mapNotNull { it.nameAttribute() }.toSet()
                    ENTRIES.filter { it.first !in present }.forEach { (name, value) ->
                        nodes.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", name), ManifestAttr("value", ATTR_VALUE, "STRING", value)))
                    }
                    nodes
                }
            }
        }
    }
}
