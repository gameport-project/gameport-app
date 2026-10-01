package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.PatchVersioning
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.takeAttributes
import app.gameport.core.patch.json.takeEach
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Raises the game's version code by the patch generation. Horizon keeps what it knows about an app
 * (its name, its icon) until the version code changes, so a game patched again, with its name now
 * corrected, would keep showing the old one. The code the game came with is recorded, so patching
 * the same game again never raises it twice, and a newer build from Steam, patched the same way,
 * always ends up higher than the one it replaces. A game never gets a lower code than it already has.
 */
object VersionCodePatch : ApkPatch {
    override val id = "version_code"
    override val recommended = true
    override val locked = false

    const val META_ORIGINAL = "app.gameport.original_version_code"
    private const val ATTR_VERSION_CODE = 16843291

    /** Grows with every release and every development change to the patches, and never shrinks. */
    val offset: Int get() = PatchVersioning.GENERATION * 10 + PatchVersioning.DEV_REVISION

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        var recorded: Int? = null
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodes {
                    recorded = elemEach<JSONObject> { named("meta-data") && nameAttribute() == META_ORIGINAL }
                        .firstOrNull()?.valueAttribute()
                    this
                }
            }
        }
        var original = recorded
        var current: Int? = null
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeAttributes {
                elemEach<JSONObject> { named("versionCode") }.firstOrNull()?.let { current = (it.opt("data") as? Number)?.toInt() }
                this
            }
        }
        val base = original ?: current ?: return
        original = base
        val raised = raisedCode(base, current, context.installedVersionCode) ?: return
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeAttributes {
                elemEach<JSONObject> { named("versionCode") }.forEach { it.put("data", raised) }
                this
            }
            takeNodesEach({ named("application") }) {
                takeNodes {
                    takeEach<JSONObject>({ named("meta-data") && nameAttribute() == META_ORIGINAL }) { null }
                    this?.put(
                        element(
                            "meta-data",
                            ManifestAttr("name", ATTR_NAME, "STRING", META_ORIGINAL),
                            ManifestAttr("value", ATTR_VALUE, "DEC", base),
                        ),
                    )
                }
            }
        }
    }

    /**
     * The code a patched game gets: the one it came with plus [offset], but never lower than the code it has
     * in its manifest now nor than the one installed on the device, because Android refuses to install a
     * lower code over a higher one (a re-patch, or an update from Steam, must stay installable). Null when
     * it would not fit.
     */
    fun raisedCode(base: Int, current: Int?, installed: Long?): Int? =
        maxOf(base.toLong() + offset, (current ?: 0).toLong(), installed ?: 0L).takeIf { it <= Int.MAX_VALUE }?.toInt()

    private fun JSONObject.valueAttribute(): Int? =
        elem<JSONArray>("attributes").elemEach<JSONObject> { named("value") }.firstOrNull()?.opt("data").let { (it as? Number)?.toInt() }
}
