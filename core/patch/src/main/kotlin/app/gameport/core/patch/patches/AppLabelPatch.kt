package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeAttributes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Gives the game its Steam name. The label in a game's own manifest is often a technical name (a Unity
 * product name such as `Ancient_Dungeon`, or a code), and it is what the headset's menus show for the
 * running app. The label becomes a plain text, so it no longer depends on the game's resources.
 */
object AppLabelPatch : ApkPatch {
    override val id = "app_label"
    override val recommended = true
    override val locked = false

    private const val ATTR_LABEL = 16842753

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        val name = context.gameName?.trim()?.takeIf { it.isNotEmpty() } ?: return
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeAttributes {
                    // The label goes first, where the platform's own tools put the lowest resource id.
                    val others = elemEach<JSONObject> { !named("label") }.reversed()
                    JSONArray().also { attributes ->
                        attributes.put(
                            JSONObject().put("name", "label").put("id", ATTR_LABEL)
                                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                                .put("value_type", "STRING").put("data", name),
                        )
                        others.forEach { attributes.put(it) }
                    }
                }
            }
        }
    }
}
