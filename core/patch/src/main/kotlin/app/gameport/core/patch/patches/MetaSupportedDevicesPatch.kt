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
import app.gameport.core.patch.json.takeEach
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Tells a Meta Quest which headsets the game is made for, in the application metadata `com.oculus.supportedDevices`. A game that does not list the headset it runs on is
 * started in compatibility mode, as if on an older Quest: the runtime then offers it only the frame rates of that one, so a request for 90 or 120 Hz is refused
 * ("display refresh rate unsupported") whatever the screen can do. A game that lists the Quest 3 is taken for one made for it.
 *
 * Only on a Meta headset, only for a VR game, only when the player chose a frame rate for it ([PatchContext.metaHighRefresh]): a game left as it is keeps its own manifest, since
 * leaving compatibility mode changes more than the frame rates. And only when the game says nothing about it: a list the game came with is never replaced.
 * GamePort leaves a mark ([MARK]) when it adds the list, so it can be told apart from the game's own and taken away by patching the game again.
 */
object MetaSupportedDevicesPatch : ApkPatch {
    override val id = "meta_supported_devices"
    override val recommended = true
    override val locked = false

    /** The headset family this patch is for (`meta`, see [app.gameport.core.model.vr.MetaQuestPlatform]). */
    const val FAMILY = "meta"

    const val NAME = "com.oculus.supportedDevices"

    /** The metadata GamePort adds beside the list, to tell that the list is its own. */
    const val MARK = "app.gameport.supported_devices"

    /** The current Quests, as Meta names them for this entry. */
    const val VALUE = "quest2|questpro|quest3|quest3s"

    internal fun wanted(context: PatchContext): Boolean = context.isVr != false && context.deviceFamily == FAMILY && context.metaHighRefresh

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (wanted(context)) patchManifest(session.manifest)
        // The player turned the choice off: a game patched again keeps what an earlier patch gave it, so the declaration GamePort added (it left its mark) is taken away.
        else if (context.deviceFamily == FAMILY) removeOwn(session.manifest)
    }

    /** Takes away the declaration and the mark when the mark is there: a list the game came with has no mark, and is left alone. */
    internal fun removeOwn(manifest: JSONObject) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodes {
                    val ours = this?.elemEach<JSONObject> { named("meta-data") }?.any { it.nameAttribute() == MARK } == true
                    if (ours) takeEach<JSONObject>({ named("meta-data") && (nameAttribute() == NAME || nameAttribute() == MARK) }) { null }
                    this
                }
            }
        }
    }

    internal fun patchManifest(manifest: JSONObject) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodes {
                    val nodes = this ?: JSONArray()
                    val present = nodes.elemEach<JSONObject> { named("meta-data") }.mapNotNull { it.nameAttribute() }.toSet()
                    if (NAME !in present) {
                        nodes.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", NAME), ManifestAttr("value", ATTR_VALUE, "STRING", VALUE)))
                        nodes.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", MARK), ManifestAttr("value", ATTR_VALUE, "STRING", "1")))
                    }
                    nodes
                }
            }
        }
    }
}
