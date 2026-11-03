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
 * Lets a game be installed on a device whose Android is older than the one the game asks for. Android refuses to install an app whose minimum version
 * (`minSdkVersion`) is above the device's own, so a build made for a recent Android never reaches a headset that still runs an older one (a Pico 4 runs Android 10).
 *
 * Only the manifest changes, and only on a device that is below the game's minimum: the minimum becomes the device's own version. Nothing else is touched, so a game
 * that needs a newer Android for something it really calls can still fail once it runs; this only removes the refusal to install. On any device that already meets the
 * game's minimum, the patch does nothing.
 */
object MinSdkPatch : ApkPatch {
    override val id = "min_sdk"
    override val recommended = true
    override val locked = false

    /** The minimum the game should declare on a device at [deviceSdk], or null when it already fits (or one of them is not known). */
    fun loweredMinSdk(minSdk: Int?, deviceSdk: Int?): Int? =
        if (minSdk != null && deviceSdk != null && deviceSdk > 0 && minSdk > deviceSdk) deviceSdk else null

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) = patchManifest(session.manifest, context.deviceSdk)

    internal fun patchManifest(manifest: JSONObject, deviceSdk: Int?) {
        var minSdk: Int? = null
        manifest.takeNodesEach({ named("manifest") }) {
            minSdk = elem<JSONArray>("nodes").elemEach<JSONObject> { named("uses-sdk") }.firstOrNull()
                ?.elem<JSONArray>("attributes").elemEach<JSONObject> { named("minSdkVersion") }.firstOrNull()
                ?.opt("data").let { (it as? Number)?.toInt() }
            this
        }
        val lowered = loweredMinSdk(minSdk, deviceSdk) ?: return
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("uses-sdk") }) {
                takeAttributes {
                    elemEach<JSONObject> { named("minSdkVersion") }.forEach { it.put("data", lowered) }
                    this
                }
            }
        }
    }
}
