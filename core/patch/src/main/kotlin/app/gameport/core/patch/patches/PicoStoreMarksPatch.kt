package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Takes out of a game's manifest the marks of the Pico store: the application metadata `pvr.app.id` (the id the game is registered under with the store) and the
 * permissions of the store's activation service, everything in the `com.pvr.tobactivate.` namespace (`com.pvr.tobactivate.permission.AUTH_CHECK` today). A Pico checks an
 * application that carries them against its maker's servers, with the signature of the installed file, and closes it at once when the signature is not the one registered:
 * a game re-signed by GamePort never matches. Without the marks the system has nothing to compare.
 *
 * The permissions are matched by their namespace and not by their last word, so a permission of another maker that happens to end the same way is never touched.
 * The metadata is matched by its exact name.
 *
 * Only on a Pico and only for a VR game, for every game: a manifest without the marks is left exactly as it is, so nothing has to be listed, and a game that carries them
 * is found by what its manifest holds, not by a list that would always be one game behind.
 */
object PicoStoreMarksPatch : ApkPatch {
    override val id = "pico_store_marks"
    override val recommended = true
    override val locked = false

    /** The namespace of the permissions of the Pico store's activation service. */
    const val PERMISSION_NAMESPACE = "com.pvr.tobactivate."

    /** The metadata that registers the game with the Pico store. */
    const val APP_ID_METADATA = "pvr.app.id"

    internal fun wanted(context: PatchContext): Boolean =
        context.isVr != false && context.deviceFamily == PicoMetadataPatch.FAMILY

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (!wanted(context)) return
        patchManifest(session.manifest)
    }

    internal fun patchManifest(manifest: JSONObject) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodes {
                this?.without { it.named("uses-permission") && it.nameAttribute()?.startsWith(PERMISSION_NAMESPACE) == true }
            }
            takeNodesEach({ named("application") }) {
                takeNodes {
                    this?.without { it.named("meta-data") && it.nameAttribute() == APP_ID_METADATA }
                }
            }
        }
    }

    /** The same nodes, in the same order, except the ones [drop] says to take out. */
    private fun JSONArray.without(drop: (JSONObject) -> Boolean): JSONArray {
        val kept = JSONArray()
        for (index in 0 until length()) {
            val node = opt(index)
            if (node is JSONObject && drop(node)) continue
            kept.put(node)
        }
        return kept
    }
}
