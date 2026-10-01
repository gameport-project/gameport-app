package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchCatalog
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeEach
import app.gameport.core.patch.PatchVersioning
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONObject

/**
 * Records which [PatchVersioning] version patched the game, as manifest metadata Android can read
 * without opening the APK. A game with an older number (or none) is offered a new patch.
 */
object PatchVersionPatch : ApkPatch {
    override val id = "patch_version"
    override val recommended = true
    override val locked = true

    const val META_KEY = "app.gameport.patch_version"

    /** Recorded instead of a version when the player took GamePort's patch off the game. */
    const val REMOVED = -1

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) = write(session, context.patchVersion)

    fun write(session: PatchSession, value: Int) {
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodes {
                    this.takeEach<JSONObject>({ named("meta-data") && nameAttribute() == META_KEY }) { null }
                    this?.put(
                        element(
                            "meta-data",
                            ManifestAttr("name", ATTR_NAME, "STRING", META_KEY),
                            ManifestAttr("value", ATTR_VALUE, "DEC", value),
                        ),
                    )
                }
            }
        }
    }
}
