package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeAttributes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Lowers the Android version a game says it needs, so it can be installed on a headset older than that. Android refuses an APK whose
 * `minSdkVersion` is above the version of the device, and refuses it before anything of the game runs: a PICO 4 is Android 10, and a
 * build made for Steam Frame may well ask for Android 11 or later.
 *
 * The number a game carries is often the one its engine put there rather than one the game truly needs, so lowering it is frequently
 * enough. Frequently is not always: the game may call something the older system does not have and stop the moment it does. It is
 * therefore never done on GamePort's own initiative, only when the player has asked for this game (see [PatchContext.lowerMinSdkTo]),
 * and what it asks for is lowered no further than the version the device actually runs.
 */
object MinSdkFloorPatch : ApkPatch {
    override val id = "min_sdk_floor"
    override val recommended = true
    override val locked = false

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        val floor = context.lowerMinSdkTo ?: return
        val asked = asked(session) ?: return
        if (asked <= floor) return
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("uses-sdk") }) {
                takeAttributes {
                    elemEach<JSONObject> { named("minSdkVersion") }.forEach { it.put("data", floor) }
                    this
                }
            }
        }
    }

    /** The Android version the game's manifest asks for, or null when it does not say. */
    internal fun asked(session: PatchSession): Int? {
        var found: Int? = null
        session.manifest.takeNodesEach({ named("manifest") }) {
            found = elem<JSONArray>("nodes").elemEach<JSONObject> { named("uses-sdk") }.firstOrNull()
                ?.elem<JSONArray>("attributes").elemEach<JSONObject> { named("minSdkVersion") }.firstOrNull()
                ?.opt("data").let { (it as? Number)?.toInt() }
            this
        }
        return found
    }
}
