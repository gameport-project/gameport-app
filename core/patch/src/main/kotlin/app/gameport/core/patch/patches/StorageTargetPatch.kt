package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.takeAttributes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Lets the player grant a game the storage permission it needs to read its expansion files. A game built for Android 13 or later cannot be
 * given `READ_EXTERNAL_STORAGE`: the permission screen does not list it, and a game that checks for it (Unity does, before it reads its
 * `.obb`) skips its own data and fails later. Built for Android 12L, the same game is offered the permission again and the player can allow it.
 *
 * Only a game that declares the permission, targets Android 13 or later and has expansion files is changed, and only its target version.
 */
object StorageTargetPatch : ApkPatch {
    override val id = "storage_target"
    override val recommended = true
    override val locked = false

    /** Android 12L: the last version for which the storage permission is still the player's to give. */
    const val LOWERED_TARGET = 32
    private const val PERMISSION = "android.permission.READ_EXTERNAL_STORAGE"

    fun shouldLower(declaresPermission: Boolean, targetSdk: Int?, hasExpansionFiles: Boolean): Boolean =
        declaresPermission && hasExpansionFiles && targetSdk != null && targetSdk > LOWERED_TARGET

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (!context.hasExpansionFiles) return
        var declares = false
        var target: Int? = null
        session.manifest.takeNodesEach({ named("manifest") }) {
            val nodes = elem<JSONArray>("nodes")
            declares = nodes.elemEach<JSONObject> { named("uses-permission") }.any { it.nameAttribute() == PERMISSION }
            target = nodes.elemEach<JSONObject> { named("uses-sdk") }.firstOrNull()
                ?.elem<JSONArray>("attributes").elemEach<JSONObject> { named("targetSdkVersion") }.firstOrNull()
                ?.opt("data").let { (it as? Number)?.toInt() }
            this
        }
        if (!shouldLower(declares, target, context.hasExpansionFiles)) return
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("uses-sdk") }) {
                takeAttributes {
                    elemEach<JSONObject> { named("targetSdkVersion") }.forEach { it.put("data", LOWERED_TARGET) }
                    this
                }
            }
        }
    }
}
