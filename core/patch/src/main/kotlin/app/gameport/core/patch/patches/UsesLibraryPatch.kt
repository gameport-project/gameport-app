package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_REQUIRED
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeAttributes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Lets a game be installed on a device that lacks a system library the game declares. A build made for the Quest declares libraries of Meta's system (`libossdk.oculus.so`) as
 * required, and Android refuses to install the game on a device without them (`INSTALL_FAILED_MISSING_SHARED_LIBRARY`): a Pico never reaches the game.
 *
 * Every library the game declares (`uses-library`, `uses-native-library`) becomes optional (`required="false"`) instead of being taken away: where the system has the library the game
 * still gets it, and where it does not the game installs without it. Only on a device that is not a Meta one, and only the manifest changes. A game that really calls the library
 * can still fail once it runs; this only removes the refusal to install.
 */
object UsesLibraryPatch : ApkPatch {
    override val id = "uses_library"
    override val recommended = true
    override val locked = false

    /** The elements of the manifest that declare a system library. */
    private val DECLARATIONS = listOf("uses-library", "uses-native-library")

    /** On a device that is not a Meta one (a Pico, another OpenXR headset, a phone). An unknown device counts as such, as it is not known to have Meta's libraries. */
    internal fun wanted(context: PatchContext): Boolean = context.deviceFamily != "meta"

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (!wanted(context)) return
        patchManifest(session.manifest)
    }

    internal fun patchManifest(manifest: JSONObject) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                for (declaration in DECLARATIONS) {
                    takeNodesEach({ named(declaration) }) {
                        takeAttributes {
                            val attributes = this ?: JSONArray()
                            val present = attributes.elemEach<JSONObject> { named("required") }
                            if (present.isEmpty()) attributes.put(optional()) else present.forEach { it.put("value_type", "BOOLEAN").put("data", false) }
                            attributes
                        }
                    }
                }
                this
            }
        }
    }

    /** `android:required="false"` as an attribute node (its resource id is higher than `name`'s, so it goes after it). */
    private fun optional(): JSONObject =
        JSONObject().put("name", "required").put("id", ATTR_REQUIRED)
            .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
            .put("value_type", "BOOLEAN").put("data", false)
}
