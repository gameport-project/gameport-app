package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeEach
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * A Unity game can carry Meta's mixed reality scripts (the MR Utility Kit) without the native library they call, Meta's OVRPlugin: the build made for another
 * headset leaves it out. The scripts then ask for it at every update and the game raises an exception forty times a second, which makes it stutter (seen in
 * Archery Pro with James: no lost frames, but an uneven picture; gone once the library answers).
 *
 * This puts a stand-in in the game that answers "not initialized" to what those scripts ask, which is what the real library would say without Meta's runtime. It is
 * only added to a game that has the kit and no OVRPlugin of its own, so a game that has the real one is never touched; and it can be turned off for a game
 * (a game patched again without it has it taken away, when it is the one GamePort gave).
 */
object OvrPluginStandInPatch : ApkPatch {
    override val id = "ovrplugin_stand_in"
    override val recommended = true
    override val locked = false

    const val LIBRARY_PATH = "lib/arm64-v8a/libOVRPlugin.so"

    /** The native part of Meta's MR Utility Kit: the sign that the game carries the scripts that call OVRPlugin. */
    const val KIT_PATH = "lib/arm64-v8a/libmrutilitykitshared.so"

    /** Application metadata the hook reads: present when this patch gave the game the stand-in. */
    const val META_KEY = "app.gameport.ovrplugin_stand_in"

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (context.isVr == false) return
        val standIn = runCatching { assets.ovrPlugin?.open()?.use { it.readBytes() } }.getOrNull() ?: return
        val present = session.hasFile(LIBRARY_PATH)
        // The one the game holds is ours when it is byte for byte the stand-in: a game patched again starts from the APK already patched.
        val ours = present && session.readFile(LIBRARY_PATH)?.contentEquals(standIn) == true
        val action = decide(hasLibrary = present, holdsOurs = ours, hasKit = session.hasFile(KIT_PATH), enabled = context.ovrPluginStandIn)
        when (action) {
            Action.GIVE -> session.addFile(LIBRARY_PATH, standIn)
            Action.TAKE_AWAY -> session.removeFile(LIBRARY_PATH)
            Action.KEEP, Action.NOTHING -> Unit
        }
        mark(session.manifest, action == Action.GIVE || action == Action.KEEP)
    }

    internal enum class Action { GIVE, KEEP, TAKE_AWAY, NOTHING }

    /**
     * What to do with the game: give it the stand-in (it has the kit, no library, and it is not turned off), keep the one it holds from an earlier pass, take that one
     * away (turned off), or nothing. A library the game came with is never touched, nor counted as ours.
     */
    internal fun decide(hasLibrary: Boolean, holdsOurs: Boolean, hasKit: Boolean, enabled: Boolean): Action = when {
        holdsOurs -> if (enabled) Action.KEEP else Action.TAKE_AWAY
        hasLibrary -> Action.NOTHING
        enabled && hasKit -> Action.GIVE
        else -> Action.NOTHING
    }

    /** Writes or takes away the mark the hook reads, once. */
    internal fun mark(manifest: JSONObject, present: Boolean) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodes {
                    val nodes = this ?: JSONArray()
                    nodes.takeEach<JSONObject>({ named("meta-data") && nameAttribute() == META_KEY }) { null }
                    if (present) nodes.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", META_KEY), ManifestAttr("value", ATTR_VALUE, "STRING", "true")))
                    nodes
                }
            }
        }
    }
}
