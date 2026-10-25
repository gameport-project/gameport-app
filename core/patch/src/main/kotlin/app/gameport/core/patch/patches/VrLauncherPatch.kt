// Adapted from ovrport's patch_launcher_entry (GPLv3).
package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.namedElement
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import app.gameport.core.model.vr.VrPlatforms
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Makes the launcher activity show up as a VR app: the main entry gets the immersive category of
 * every known [VrPlatforms] entry, so any supported headset starts it in an immersive session
 * instead of a flat window.
 */
object VrLauncherPatch : ApkPatch {
    override val id = "vr_launcher_entry"
    override val recommended = true
    override val locked = false

    private const val LAUNCHER = "android.intent.category.LAUNCHER"

    // An intent filter already marked as a launcher, or as VR by any known platform, is the entry.
    private val LAUNCHER_CATEGORIES = VrPlatforms.allImmersiveLauncherCategories.toSet() + LAUNCHER

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        // A flat game is not a VR app: its launcher entry stays as it is.
        if (context.isVr == false) return
        patchManifest(session.manifest)
    }

    internal fun patchManifest(manifest: JSONObject) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodesEach({ named("activity") }) {
                    val isGameActivity = nameAttribute() == VrGameActivityPatch.UNREAL_GAME_ACTIVITY
                    takeNodesEach({ named("intent-filter") }) {
                        val categories = elem<JSONArray>("nodes").elemEach<JSONObject> { named("category") }.mapNotNull { it.nameAttribute() }
                        // The VR-only filter of an Unreal GameActivity (see VrGameActivityPatch) is not a launcher entry: a second pass leaves it as it is.
                        val isLauncher = categories.any { it in LAUNCHER_CATEGORIES } && !(isGameActivity && LAUNCHER !in categories)
                        takeNodes {
                            if (!isLauncher) this else {
                                JSONArray().put(namedElement("action", "android.intent.action.MAIN")).also { nodes ->
                                    (listOf(LAUNCHER) + VrPlatforms.allImmersiveLauncherCategories)
                                        .forEach { nodes.put(namedElement("category", it)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
